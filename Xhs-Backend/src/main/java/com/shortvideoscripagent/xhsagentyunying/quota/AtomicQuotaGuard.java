package com.shortvideoscripagent.xhsagentyunying.quota;

import com.shortvideoscripagent.xhsagentyunying.billing.pricing.PricingService;
import com.shortvideoscripagent.xhsagentyunying.billing.pricing.domain.entity.PriceBook;
import com.shortvideoscripagent.xhsagentyunying.billing.support.BillingMath;
import com.shortvideoscripagent.xhsagentyunying.domain.mapper.WorkspaceMapper;
import com.shortvideoscripagent.xhsagentyunying.quota.domain.entity.UsageLedger;
import com.shortvideoscripagent.xhsagentyunying.quota.domain.entity.WorkspaceQuota;
import com.shortvideoscripagent.xhsagentyunying.quota.domain.mapper.UsageLedgerMapper;
import com.shortvideoscripagent.xhsagentyunying.quota.domain.mapper.WorkspaceQuotaMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * P0-2: 原子化额度守卫。
 * <p>
 * 单 SQL UPDATE 原子扣减，保证并发下不超额。
 * 成功后写入 usage_ledger 审计记录。
 * </p>
 * <p>
 * <b>S4-3 扩展</b>：扣费时同时写入<b>价格快照</b>
 * （{@code unit_price / amount / price_version}），使账单金额可由「单价 × 数量」复算。
 * 没有价格快照的台账行会被账单出账 SQL（{@code AND amount IS NOT NULL}）排除，
 * 导致这部分用量永远不出账 —— 因此<b>计价快照必须与台账行同批写入</b>。
 * </p>
 * <p>
 * <b>S4-3 P0-11 扩展</b>：提供两个扣费入口。
 * <ul>
 *   <li>{@link #tryConsume} —— <b>不去重</b>，每次调用都扣 1 次。
 *       用于「同一业务引用天然对应多次计费」的场景（Agent 对话：同一 session
 *       的每条消息各扣 1 次）。</li>
 *   <li>{@link #tryConsumeIdempotent} —— <b>幂等</b>，同一
 *       {@code (workspace_id, ref_id, action)} 最多只有一笔生效扣费。
 *       用于「一次任务只应扣 1 次」的场景（分析任务、任务队列重试、孤儿任务恢复）。</li>
 * </ul>
 * </p>
 */
@Slf4j
@Service
public class AtomicQuotaGuard {

    private final WorkspaceQuotaMapper workspaceQuotaMapper;
    private final UsageLedgerMapper usageLedgerMapper;
    private final WorkspaceMapper workspaceMapper;
    private final QuotaProperties quotaProperties;
    private final PricingService pricingService;

    public AtomicQuotaGuard(
            WorkspaceQuotaMapper workspaceQuotaMapper,
            UsageLedgerMapper usageLedgerMapper,
            WorkspaceMapper workspaceMapper,
            QuotaProperties quotaProperties,
            PricingService pricingService
    ) {
        this.workspaceQuotaMapper = workspaceQuotaMapper;
        this.usageLedgerMapper = usageLedgerMapper;
        this.workspaceMapper = workspaceMapper;
        this.quotaProperties = quotaProperties;
        this.pricingService = pricingService;
    }

    /**
     * 原子扣减 workspace 额度 —— <b>不去重</b>，每次调用都扣 1 次。
     * <p>
     * 适用于「同一业务引用天然对应多次计费」的场景，例如 Agent 对话：
     * 同一 {@code sessionId} 的每条消息都要各扣 1 次。
     * </p>
     * <p>
     * 任务类（一次任务只应扣 1 次）请改用
     * {@link #tryConsumeIdempotent(String, String, UUID, String)}。
     * </p>
     */
    @Transactional
    public QuotaCheckResult tryConsume(String workspaceId, String action, UUID refId, String reason) {
        return doConsume(workspaceId, action, refId, reason, null);
    }

    /**
     * 原子扣减 workspace 额度 —— <b>幂等版本</b>。
     * <p>
     * <b>语义</b>：同一 {@code (workspace_id, ref_id, action)} 三元组<b>最多只有一笔生效扣费</b>。
     * 重复调用时直接复用已有的台账行，既不再扣配额也不写新台账行。
     * </p>
     * <p>
     * <b>为什么需要</b>：任务队列的失败重试（{@code TaskQueueConsumerRunner#handleFailure}）
     * 与孤儿任务恢复（{@code TaskRecoveryService#recoverOrphanTasks}）都会把同一个
     * {@code taskId} 重新投递给 handler。若 handler 每次执行都扣一次配额，
     * 同一个任务失败 3 次就会被扣 3 次费 —— 实测库里已出现同一 {@code ref_id} 3 行
     * {@code ANALYSIS} 台账记录（1 个任务扣了 3 次）。
     * </p>
     * <p>
     * <b>去重键为什么是三元组而不是 {@code ref_id} 单列</b>：
     * <ul>
     *   <li>{@code ref_id} 允许为 NULL（余额流水、热点采集等无业务引用的场景），
     *       NULL 不能参与唯一约束；</li>
     *   <li>同一 {@code sessionId} 的多条 Agent 对话本来就该各扣 1 次 ——
     *       因此这类场景必须用 {@link #tryConsume}（不去重）。</li>
     * </ul>
     * 任务类动作（一次任务只应扣 1 次）才使用本方法。
     * </p>
     * <p>
     * <b>并发安全</b>：查询与写入之间存在竞态窗口。为把窗口压到最小，
     * 本方法依赖 {@code app.usage_ledger} 上针对 {@code dedupe_key} 的
     * <b>部分唯一索引</b>（见 V65 迁移）做最终防线：并发插入时后者会命中唯一冲突，
     * 此时回查并复用先写入的那一行，并把多扣的额度回补。
     * </p>
     *
     * @return 扣费结果；{@code ledgerId()} 为本次生效的台账行 id
     */
    @Transactional
    public QuotaCheckResult tryConsumeIdempotent(String workspaceId, String action, UUID refId, String reason) {
        if (refId == null) {
            // 没有业务引用 -> 无法去重，退化为普通扣减
            return doConsume(workspaceId, action, null, reason, null);
        }

        // 1. 先查：已有生效扣费则直接复用
        UsageLedger existing = usageLedgerMapper.selectActiveByRefIdAndAction(workspaceId, refId, action);
        if (existing != null) {
            log.info("Quota consume skipped (idempotent replay): ws={} action={} refId={} ledgerId={}",
                    workspaceId, action, refId, existing.getId());
            return QuotaCheckResult.ok(existing.getId());
        }

        // 2. 扣配额 + 写台账（带 dedupe_key）
        String dedupeKey = buildDedupeKey(action, refId);
        QuotaCheckResult result = doConsume(workspaceId, action, refId, reason, dedupeKey);
        if (result.isOk()) {
            return result;
        }
        // 3. 配额不足：也要排除「其实已被另一线程扣过」的情况
        UsageLedger concurrent = usageLedgerMapper.selectActiveByRefIdAndAction(workspaceId, refId, action);
        if (concurrent != null) {
            log.info("Quota consume resolved to concurrent ledger (idempotent): ws={} action={} refId={}",
                    workspaceId, action, refId);
            return QuotaCheckResult.ok(concurrent.getId());
        }
        return result;
    }

    /**
     * 扣配额 + 写台账的公共实现。
     *
     * @param dedupeKey 幂等去重键；{@code null} 表示不参与去重
     */
    private QuotaCheckResult doConsume(String workspaceId, String action, UUID refId,
                                       String reason, String dedupeKey) {
        // 1. 首次原子扣减
        int affected = workspaceQuotaMapper.atomicConsume(workspaceId);

        if (affected == 0) {
            // 2. 检查是否因为 reset_at 过期导致扣减失败
            int resetRows = workspaceQuotaMapper.resetIfExpired(workspaceId);
            if (resetRows > 0) {
                log.info("Auto-reset quota for workspace {}", workspaceId);
                affected = workspaceQuotaMapper.atomicConsume(workspaceId);
            }
        }

        if (affected == 0) {
            // 3. 仍失败：workspace 不存在或配额耗尽，尝试懒创建
            if (workspaceQuotaMapper.selectById(workspaceId) == null) {
                lazyInitQuota(workspaceId);
                affected = workspaceQuotaMapper.atomicConsume(workspaceId);
            }
        }

        if (affected == 0) {
            log.warn("Quota exceeded for workspace {} action {}", workspaceId, action);
            return QuotaCheckResult.exceeded("quota_exceeded");
        }

        // 4. 写入 ledger CONSUMED（含价格快照，供后续出账与成本回算）
        UsageLedger ledger = new UsageLedger();
        ledger.setId(UUID.randomUUID());
        ledger.setWorkspaceId(workspaceId);
        ledger.setAction(action);
        ledger.setRefId(refId);
        ledger.setDelta(1);
        ledger.setStatus("CONSUMED");
        ledger.setReason(reason);
        ledger.setDedupeKey(dedupeKey);
        ledger.setCreatedAt(OffsetDateTime.now());
        applyPriceSnapshot(ledger);

        int inserted = insertLedger(ledger);
        if (inserted == 0) {
            // 唯一冲突：并发下另一线程已写入同一 dedupe_key
            // -> 回补多扣的额度，复用已存在的台账行
            UsageLedger existing = usageLedgerMapper.selectByDedupeKey(workspaceId, dedupeKey);
            workspaceQuotaMapper.refund(workspaceId);
            if (existing != null) {
                log.info("Quota consume deduped by unique key: ws={} action={} dedupeKey={} ledgerId={}",
                        workspaceId, action, dedupeKey, existing.getId());
                return QuotaCheckResult.ok(existing.getId());
            }
            // 回查不到（理论上不可能）→ 仍返回本行 id，金额不丢账
            log.warn("Quota consume unique conflict but ledger not found: ws={} dedupeKey={}", workspaceId, dedupeKey);
            return QuotaCheckResult.ok(ledger.getId());
        }

        log.debug("Quota consumed: workspace={} action={} ledgerId={} unitPrice={} amount={}",
                workspaceId, action, ledger.getId(), ledger.getUnitPrice(), ledger.getAmount());
        return QuotaCheckResult.ok(ledger.getId());
    }

    /**
     * 写台账行。
     * <p>
     * <b>有 dedupe_key</b> → 用 {@code ON CONFLICT DO NOTHING} 的幂等插入，
     * 避免唯一冲突把 PostgreSQL 事务置为 aborted 状态
     * （捕获 {@code DuplicateKeyException} 已经太晚了 —— 事务已被污染）。
     * </p>
     * <b>无 dedupe_key</b> → 普通插入。
     * </p>
     */
    private int insertLedger(UsageLedger ledger) {
        if (ledger.getDedupeKey() == null) {
            usageLedgerMapper.insert(ledger);
            return 1;
        }
        return usageLedgerMapper.insertIdempotent(
                ledger.getId(),
                ledger.getWorkspaceId(),
                ledger.getUserId(),
                ledger.getAction(),
                ledger.getRefId(),
                ledger.getDelta(),
                ledger.getStatus(),
                ledger.getReason(),
                ledger.getUnitPrice(),
                ledger.getAmount(),
                ledger.getPriceVersion(),
                ledger.getDedupeKey(),
                ledger.getCreatedAt()
        );
    }

    /**
     * 幂等去重键：{@code <ACTION>:<ref_id>}。
     * <p>
     * 用大写化的 action 归一，避免 {@code analysis} / {@code ANALYSIS} 产生两个不同的 key。
     * </p>
     */
    private static String buildDedupeKey(String action, UUID refId) {
        if (action == null || action.isBlank() || refId == null) {
            return null;
        }
        return action.trim().toUpperCase() + ":" + refId;
    }

    /**
     * 写入价格快照（{@code unit_price / amount / price_version}）。
     * <p>
     * <b>为什么必须在这里写</b>：账单出账 SQL（{@code selectUnbilled}）带
     * {@code AND amount IS NOT NULL}，没有金额快照的台账行永远不会被出账，
     * 用量就会凭空消失。
     * </p>
     * <p>
     * <b>为什么不能抛错</b>：价格表缺行是「配置问题」，不应阻断用户正常业务；
     * 此时单价按 0 计（成本侧同样为 0），账单金额为 0 但账单行仍然存在，
     * 计量单位、数量都可追溯，不会丢账。
     * </p>
     */
    private void applyPriceSnapshot(UsageLedger ledger) {
        try {
            PriceBook price = pricingService.resolvePrice(ledger.getAction(), ledger.getCreatedAt());
            if (price == null) {
                log.debug("No price row for action={}, ledger priced at 0", ledger.getAction());
                ledger.setUnitPrice(BigDecimal.ZERO.setScale(BillingMath.SCALE, RoundingMode.HALF_UP));
                ledger.setAmount(BigDecimal.ZERO.setScale(BillingMath.SCALE, RoundingMode.HALF_UP));
                return;
            }
            BigDecimal unitPrice = BillingMath.scale(price.getUnitPrice());
            int delta = ledger.getDelta() == null ? 0 : ledger.getDelta();
            ledger.setUnitPrice(unitPrice);
            ledger.setAmount(BillingMath.scale(unitPrice.multiply(BigDecimal.valueOf(delta))));
            ledger.setPriceVersion(price.getVersion());
        } catch (Exception ex) {
            // 计价失败不得影响扣费主流程；金额留 0，行仍然会被出账（quantity 可追溯）
            log.warn("Price snapshot failed for action={}: {}",
                    ledger.getAction(), ex.getMessage());
            ledger.setUnitPrice(BigDecimal.ZERO.setScale(BillingMath.SCALE, RoundingMode.HALF_UP));
            ledger.setAmount(BigDecimal.ZERO.setScale(BillingMath.SCALE, RoundingMode.HALF_UP));
        }
    }

    /**
     * 懒创建 workspace 配额记录。
     */
    private void lazyInitQuota(String workspaceId) {
        var ws = workspaceMapper.selectById(workspaceId);
        int dailyQuota = quotaProperties.getDefaultDailyQuota();
        if (ws != null && ws.getDefaultQuota() != null) {
            dailyQuota = ws.getDefaultQuota();
        }
        OffsetDateTime resetAt = quotaProperties.isLazyInitExpired()
                ? OffsetDateTime.now().minusSeconds(1)
                : OffsetDateTime.now().plusDays(1);

        WorkspaceQuota q = new WorkspaceQuota();
        q.setWorkspaceId(workspaceId);
        q.setDailyQuota(dailyQuota);
        q.setUsed(0);
        q.setResetAt(resetAt);
        q.setCreatedAt(OffsetDateTime.now());
        q.setUpdatedAt(OffsetDateTime.now());
        try {
            workspaceQuotaMapper.insert(q);
            log.info("Lazy-initialized workspace_quota for {}", workspaceId);
        } catch (Exception ex) {
            log.debug("workspace_quota already exists for {}", workspaceId);
        }
    }

    /**
     * 查询 workspace 配额状态。
     */
    public WorkspaceQuota getQuota(String workspaceId) {
        WorkspaceQuota q = workspaceQuotaMapper.selectById(workspaceId);
        if (q != null && q.getResetAt() != null && q.getResetAt().isBefore(OffsetDateTime.now())) {
            workspaceQuotaMapper.resetIfExpired(workspaceId);
            q = workspaceQuotaMapper.selectById(workspaceId);
        }
        return q;
    }

    /**
     * 检查是否还有剩余配额。
     */
    public boolean hasQuota(String workspaceId) {
        WorkspaceQuota q = workspaceQuotaMapper.selectById(workspaceId);
        if (q == null) return true;
        if (q.getResetAt() != null && q.getResetAt().isBefore(OffsetDateTime.now())) return true;
        return q.getUsed() < q.getDailyQuota();
    }
}
