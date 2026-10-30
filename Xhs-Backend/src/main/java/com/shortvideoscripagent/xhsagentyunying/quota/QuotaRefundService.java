package com.shortvideoscripagent.xhsagentyunying.quota;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.shortvideoscripagent.xhsagentyunying.domain.entity.UsageLog;
import com.shortvideoscripagent.xhsagentyunying.domain.mapper.UsageLogMapper;
import com.shortvideoscripagent.xhsagentyunying.quota.domain.entity.UsageLedger;
import com.shortvideoscripagent.xhsagentyunying.quota.domain.mapper.UsageLedgerMapper;
import com.shortvideoscripagent.xhsagentyunying.quota.domain.mapper.WorkspaceQuotaMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * P0-2 / S4-3: 额度冲正服务。
 * <p>
 * <b>冲正语义</b>（对齐阶段 0 冻结口径 {@code docs/BILLING-RULES.md}
 * 与 {@code app.usage_ledger.delta} 的「+1 扣减 / -1 冲正」）：
 * <ul>
 *   <li>把原始台账行的 {@code status} 从 {@code CONSUMED} 改为 {@code REFUNDED}
 *       —— 表示「这笔扣费已被冲正」，同时让基于
 *       {@code status='CONSUMED'} 的去重查询不再命中它（任务重试时可重新计费）；</li>
 *   <li><b>另写一行 {@code delta = -1} 的冲正台账</b>，{@code related_log_id} 指向原始行
 *       —— 这是账本层面的对冲行，使「台账金额之和」与「账单金额」天然净额相符；</li>
 *   <li>{@code workspace_quota.used - 1}（额度计数回补）；</li>
 *   <li>更新关联的 {@code app.usage_log} 退费标记（遗留兼容）。</li>
 * </ul>
 * </p>
 * <p>
 * <b>为什么必须写 -1 冲正行</b>：出账与成本聚合 SQL（{@code selectUnbilled} /
 * {@code aggregateCostByUnit}）是按 {@code SUM(amount)} 汇总的。
 * 若只把原始行标记为 {@code REFUNDED} 而不写 -1 行，失败任务的金额会被完整计入账单
 * —— 即「失败任务照样收费」。
 * </p>
 * <p>
 * <b>为什么冲正行的 {@code ref_id} 置 NULL</b>：{@code ref_id} 是业务引用，
 * 冲正行本身不是一次新的用量；且 {@code ref_id} 参与幂等去重
 * （{@code selectActiveByRefIdAndAction}），若沿用原值，重复冲正会被误判成
 * 「又一笔生效扣费」。冲正行的锚点是 {@code related_log_id}。
 * </p>
 */
@Slf4j
@Service
public class QuotaRefundService {

    /** 台账状态：生效 */
    public static final String STATUS_CONSUMED = "CONSUMED";
    /** 台账状态：已冲正 */
    public static final String STATUS_REFUNDED = "REFUNDED";

    /** 遗留 usage_log 退费状态 */
    private static final String USAGE_LOG_REFUNDED = "REFUNDED";

    private final UsageLedgerMapper usageLedgerMapper;
    private final UsageLogMapper usageLogMapper;
    private final WorkspaceQuotaMapper workspaceQuotaMapper;
    private final PlatformTransactionManager transactionManager;

    public QuotaRefundService(UsageLedgerMapper usageLedgerMapper,
                              UsageLogMapper usageLogMapper,
                              WorkspaceQuotaMapper workspaceQuotaMapper,
                              PlatformTransactionManager transactionManager) {
        this.usageLedgerMapper = usageLedgerMapper;
        this.usageLogMapper = usageLogMapper;
        this.workspaceQuotaMapper = workspaceQuotaMapper;
        this.transactionManager = transactionManager;
    }

    /**
     * 冲正指定 ledger 记录。
     * <p>
     * 1. 标记原始 ledger 为 REFUNDED（条件更新，天然幂等）
     * 2. 写一行 delta = -1 的冲正台账（账本对冲）
     * 3. workspace_quota used - 1
     * 4. 更新关联的 usage_log 退费标记（独立事务，失败不影响主流程）
     * </p>
     *
     * @param ledgerId 要冲正的 ledger id
     * @param reason   冲正原因
     * @return true=成功冲正，false=记录不存在或已冲正
     */
    @Transactional
    public boolean refund(UUID ledgerId, String reason) {
        UsageLedger ledger = usageLedgerMapper.selectById(ledgerId);
        if (ledger == null) {
            log.warn("Refund target ledger not found: {}", ledgerId);
            return false;
        }
        if (STATUS_REFUNDED.equals(ledger.getStatus())) {
            log.debug("Ledger already refunded: {}", ledgerId);
            return false;
        }

        // 1. 标记 ledger 为 REFUNDED（条件更新：并发下只有一个线程能成功）
        int updated = usageLedgerMapper.markRefunded(ledgerId);
        if (updated == 0) {
            log.warn("Ledger already refunded (concurrent): {}", ledgerId);
            return false;
        }

        // 2. 写 -1 冲正台账（账本对冲：SUM(amount) 才能净额为零）
        writeReversalLedger(ledger, reason);

        // 3. workspace_quota used - 1
        if (ledger.getWorkspaceId() != null) {
            workspaceQuotaMapper.refund(ledger.getWorkspaceId());
        }

        // 4. 更新关联的 usage_log（遗留兼容）——独立事务，失败不影响冲正结果
        markLegacyUsageLogRefunded(ledgerId);

        log.info("Quota refunded: ledgerId={} action={} amount={} reason={}",
                ledgerId, ledger.getAction(), ledger.getAmount(), reason);
        return true;
    }

    /**
     * 按 refId 查找 ledger 并冲正（用于 handler catch block）。
     */
    @Transactional
    public boolean refundByRefId(String workspaceId, UUID refId, String reason) {
        UsageLedger ledger = usageLedgerMapper.selectOne(
                new LambdaQueryWrapper<UsageLedger>()
                        .eq(UsageLedger::getWorkspaceId, workspaceId)
                        .eq(UsageLedger::getRefId, refId)
                        .eq(UsageLedger::getStatus, STATUS_CONSUMED)
                        .orderByDesc(UsageLedger::getCreatedAt)
                        .last("LIMIT 1")
        );
        if (ledger == null) {
            log.debug("No CONSUMED ledger found for refId {} workspace {}", refId, workspaceId);
            return false;
        }
        return refund(ledger.getId(), reason);
    }

    /**
     * 按<b>原始业务 ID</b>（如 {@code ana_xxx} / {@code sess_xxx}）查找 ledger 并冲正。
     * <p>
     * 走 {@link QuotaRefId#resolve(String)} 做确定性映射，与
     * {@code AtomicQuotaGuard} 写入时用的是同一套规则，因此能对上。
     * </p>
     */
    @Transactional
    public boolean refundByRawRefId(String workspaceId, String rawRefId, String reason) {
        UUID refId = QuotaRefId.resolve(rawRefId);
        if (refId == null) {
            return false;
        }
        return refundByRefId(workspaceId, refId, reason);
    }

    // ══════════════════════════════════════════════════════════════════
    // private
    // ══════════════════════════════════════════════════════════════════

    /**
     * 写一行 {@code delta = -1} 的冲正台账。
     * <p>
     * <b>单价沿用原始行的快照</b>（{@code unit_price / price_version}），
     * 不重新查价格表 —— 否则价格改版后冲正金额会与原始金额对不上，
     * 账单净额就不再是零。
     * </p>
     */
    private void writeReversalLedger(UsageLedger origin, String reason) {
        UsageLedger reversal = new UsageLedger();
        reversal.setId(UUID.randomUUID());
        reversal.setWorkspaceId(origin.getWorkspaceId());
        reversal.setUserId(origin.getUserId());
        // action 沿用原始计量单位（余额流水 action=BALANCE 同样沿用）
        reversal.setAction(origin.getAction());
        // ref_id 置 NULL：冲正不是一次新的用量，且不参与幂等去重
        reversal.setRefId(null);
        reversal.setDelta(-1);
        reversal.setStatus(STATUS_CONSUMED);
        // related_log_id 指向原始台账行（这才是冲正的锚点）
        reversal.setRelatedLogId(origin.getId());
        reversal.setReason(truncate("refund:" + (reason == null ? "" : reason)));
        reversal.setUnitPrice(origin.getUnitPrice());
        reversal.setPriceVersion(origin.getPriceVersion());
        reversal.setProvider(origin.getProvider());
        reversal.setCreatedAt(OffsetDateTime.now());

        BigDecimal originAmount = origin.getAmount();
        if (originAmount == null) {
            // 原始行没有金额快照（历史数据）→ 冲正行金额记 0，不臆造金额
            reversal.setAmount(BigDecimal.ZERO.setScale(4, java.math.RoundingMode.HALF_UP));
        } else {
            reversal.setAmount(originAmount.negate().setScale(4, java.math.RoundingMode.HALF_UP));
        }
        usageLedgerMapper.insert(reversal);
    }

    /**
     * 更新遗留 {@code app.usage_log} 的退费标记。
     * <p>
     * <b>必须独立事务</b>：{@code usage_log} 是遗留兼容表，写失败不应导致
     * 整个冲正事务回滚（否则失败任务仍然被计费）。
     * </p>
     * <p>
     * <b>按 {@code original_log_id} 反查</b>：该列存的是<b>台账行 id</b>（UUID）。
     * 早期实现误用 {@code usage_log.id}（{@code BIGSERIAL}）去匹配台账的
     * {@code ref_id}（{@code UUID}），会触发 PostgreSQL
     * {@code operator does not exist: bigint = character varying}，
     * 该异常使整个事务进入 aborted 状态 → 冲正回滚 → 失败任务照样计费。
     * </p>
     */
    private void markLegacyUsageLogRefunded(UUID ledgerId) {
        TransactionTemplate requiresNew = new TransactionTemplate(transactionManager);
        requiresNew.setPropagationBehavior(
                org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        try {
            requiresNew.executeWithoutResult(status -> {
                UsageLog usageLog = usageLogMapper.selectOne(
                        new LambdaQueryWrapper<UsageLog>()
                                .eq(UsageLog::getOriginalLogId, ledgerId)
                                .last("LIMIT 1")
                );
                if (usageLog == null) {
                    return;
                }
                usageLog.setRefundStatus(USAGE_LOG_REFUNDED);
                usageLog.setRefundedAt(OffsetDateTime.now());
                usageLogMapper.updateById(usageLog);
            });
        } catch (Exception ex) {
            log.debug("Failed to update usage_log refund fields for ledger {}: {}", ledgerId, ex.getMessage());
        }
    }

    private static String truncate(String raw) {
        if (raw == null) {
            return null;
        }
        return raw.length() > 255 ? raw.substring(0, 255) : raw;
    }
}
