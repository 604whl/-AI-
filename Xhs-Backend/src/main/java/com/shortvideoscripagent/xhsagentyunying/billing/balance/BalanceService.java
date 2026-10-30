package com.shortvideoscripagent.xhsagentyunying.billing.balance;

import com.shortvideoscripagent.xhsagentyunying.billing.balance.domain.entity.WorkspaceBalance;
import com.shortvideoscripagent.xhsagentyunying.billing.balance.domain.mapper.WorkspaceBalanceMapper;
import com.shortvideoscripagent.xhsagentyunying.billing.support.BillingMath;
import com.shortvideoscripagent.xhsagentyunying.common.exception.BusinessException;
import com.shortvideoscripagent.xhsagentyunying.domain.mapper.WorkspaceMapper;
import com.shortvideoscripagent.xhsagentyunying.dto.billing.pricing.BillingDtos;
import com.shortvideoscripagent.xhsagentyunying.infra.audit.AuditWriter;
import com.shortvideoscripagent.xhsagentyunying.quota.domain.entity.UsageLedger;
import com.shortvideoscripagent.xhsagentyunying.quota.domain.mapper.UsageLedgerMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * S4-3 余额服务（按量计费预付余额）。
 * <p>
 * <b>原子扣减</b>：扣减用单条 UPDATE
 * {@code UPDATE ... SET balance = balance - ? WHERE workspace_id = ? AND balance >= ?}，
 * 受影响行数为 0 即表示余额不足 → 抛可读错误 {@code insufficient_balance}。
 * 数据库行锁 + 条件更新保证并发下<b>不会出现负余额</b>。
 * </p>
 * <p>
 * <b>流水可追溯</b>：每一次余额变动都写一条 {@code app.usage_ledger}：
 * <ul>
 *   <li>{@code action = 'BALANCE'}（余额语义，非计量单位）；</li>
 *   <li>{@code delta = +1} 入账 / {@code delta = -1} 扣减（与「+1 扣减 / -1 冲正」的
 *       常规语义相反，此处只针对 BALANCE 行，reason 里写明方向）；</li>
 *   <li>{@code amount} 记带符号的金额（入账为正、扣减为负），便于对账。</li>
 * </ul>
 * BALANCE 行<b>不参与</b>账单出账与成本毛利聚合（聚合 SQL 显式排除 {@code action = 'BALANCE'}）。
 * </p>
 * <p>
 * <b>入账入口</b>：S4-2（支付/订单）在本服务就绪后调用 {@link #creditBalance} 完成「订单 → 入账」。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BalanceService {

    /** 余额流水的 action 语义（非计量单位） */
    public static final String BALANCE_ACTION = "BALANCE";
    /** 默认币种 */
    public static final String DEFAULT_CURRENCY = "CNY";

    /** 余额不足错误码 */
    public static final int CODE_INSUFFICIENT_BALANCE = 40201;
    /** 非法参数 */
    public static final int CODE_BAD_REQUEST = 40010;
    /** 工作区不存在 */
    public static final int CODE_NOT_FOUND = 40410;

    private final WorkspaceBalanceMapper balanceMapper;
    private final UsageLedgerMapper usageLedgerMapper;
    private final WorkspaceMapper workspaceMapper;
    private final AuditWriter auditWriter;

    /**
     * 读取余额（不存在时懒创建一条 0 余额记录）。
     */
    @Transactional
    public BillingDtos.BalanceView getBalance(String workspaceId) {
        WorkspaceBalance balance = ensureAccount(workspaceId);
        return toView(balance);
    }

    /**
     * 入账（充值到账 / 退款回补）。<b>幂等</b>：同一 {@code orderNo} 重复入账只生效一次。
     *
     * @param orderNo 业务单号（订单号）；为空时不做幂等控制
     * @return 入账后的余额视图
     */
    @Transactional
    public BillingDtos.BalanceView creditBalance(String workspaceId, BigDecimal amount,
                                                 String currency, String orderNo, String reason) {
        BigDecimal normalized = normalizeAmount(amount);
        if (normalized.signum() <= 0) {
            throw new BusinessException(CODE_BAD_REQUEST, "invalid_topup_amount");
        }
        ensureAccount(workspaceId);

        if (orderNo != null && !orderNo.isBlank() && existsBalanceOrderLedger(workspaceId, orderNo)) {
            log.info("Balance credit skipped (idempotent): ws={} orderNo={}", workspaceId, orderNo);
            return toView(balanceMapper.selectByWorkspace(workspaceId));
        }

        int affected = balanceMapper.atomicCredit(workspaceId, normalized);
        if (affected == 0) {
            throw new BusinessException(CODE_NOT_FOUND, "balance_account_not_found");
        }
        writeLedger(workspaceId, +1, normalized, buildCreditReason(reason, orderNo));

        auditWriter.record("BALANCE_CREDITED", "WORKSPACE_BALANCE", workspaceId,
                Map.of("workspaceId", workspaceId,
                        "amount", normalized.toPlainString(),
                        "orderNo", orderNo == null ? "" : orderNo,
                        "reason", reason == null ? "" : reason));

        log.info("Balance credited: ws={} amount={} orderNo={}", workspaceId, normalized, orderNo);
        return toView(balanceMapper.selectByWorkspace(workspaceId));
    }

    /**
     * 扣减余额（按量消费）。
     * <p>
     * <b>原子</b>：单条 UPDATE + {@code balance >= amount} 条件。
     * </p>
     *
     * @param unit    关联的计量单位（写进 reason 便于追溯），可为空
     * @param orderNo 业务单号（幂等键），可为空
     * @throws BusinessException 余额不足时（{@code insufficient_balance}）
     */
    @Transactional
    public void debitBalance(String workspaceId, BigDecimal amount, String unit,
                              String orderNo, String reason) {
        BigDecimal normalized = normalizeAmount(amount);
        if (normalized.signum() <= 0) {
            throw new BusinessException(CODE_BAD_REQUEST, "invalid_debit_amount");
        }
        ensureAccount(workspaceId);

        if (orderNo != null && !orderNo.isBlank() && existsBalanceOrderLedger(workspaceId, orderNo)) {
            // 已扣过（幂等重放）
            log.info("Balance debit skipped (idempotent): ws={} orderNo={}", workspaceId, orderNo);
            return;
        }

        int affected = balanceMapper.atomicDebit(workspaceId, normalized);
        if (affected == 0) {
            WorkspaceBalance current = balanceMapper.selectByWorkspace(workspaceId);
            BigDecimal bal = current == null || current.getBalance() == null
                    ? BigDecimal.ZERO : current.getBalance();
            BigDecimal frz = current == null || current.getFrozen() == null
                    ? BigDecimal.ZERO : current.getFrozen();
            // 可用余额口径与 atomicDebit 的判定条件保持一致：balance - frozen
            BigDecimal left = bal.subtract(frz);
            log.warn("Balance insufficient: ws={} need={} left={} reason={}",
                    workspaceId, normalized, left, reason);
            auditWriter.recordFailure("BALANCE_DEBIT_REJECTED", "WORKSPACE_BALANCE", workspaceId,
                    CODE_INSUFFICIENT_BALANCE, "insufficient_balance",
                    Map.of("workspaceId", workspaceId,
                            "required", normalized.toPlainString(),
                            "available", left.toPlainString()));
            throw new BusinessException(CODE_INSUFFICIENT_BALANCE,
                    "insufficient_balance: available=" + left.toPlainString()
                            + " required=" + normalized.toPlainString());
        }
        writeLedger(workspaceId, -1, normalized, buildDebitReason(unit, reason, orderNo));

        auditWriter.record("BALANCE_DEBITED", "WORKSPACE_BALANCE", workspaceId,
                Map.of("workspaceId", workspaceId,
                        "amount", normalized.toPlainString(),
                        "unit", unit == null ? "" : unit,
                        "reason", reason == null ? "" : reason));
        log.info("Balance debited: ws={} amount={} unit={}", workspaceId, normalized, unit);
    }

    /**
     * 冻结余额（未支付账单占用）。
     */
    @Transactional
    public boolean freezeBalance(String workspaceId, BigDecimal amount, String reason) {
        BigDecimal normalized = normalizeAmount(amount);
        if (normalized.signum() <= 0) {
            throw new BusinessException(CODE_BAD_REQUEST, "invalid_freeze_amount");
        }
        ensureAccount(workspaceId);
        return balanceMapper.atomicFreeze(workspaceId, normalized) > 0;
    }

    /**
     * 解冻余额。
     */
    @Transactional
    public boolean unfreezeBalance(String workspaceId, BigDecimal amount) {
        BigDecimal normalized = normalizeAmount(amount);
        if (normalized.signum() <= 0) {
            return false;
        }
        return balanceMapper.atomicUnfreeze(workspaceId, normalized) > 0;
    }

    /**
     * 余额是否足够（不抛错）。
     * <p>
     * P2-5：判定口径必须与 {@code atomicDebit} 一致 —— 后者用的是
     * {@code balance - frozen >= amount}（可用余额），而这里早期只比 {@code balance}，
     * 会导致「预检说够、扣的时候说不够」。
     * </p>
     */
    @Transactional
    public boolean hasSufficientBalance(String workspaceId, BigDecimal amount) {
        WorkspaceBalance balance = balanceMapper.selectByWorkspace(workspaceId);
        if (balance == null || balance.getBalance() == null) {
            return false;
        }
        BigDecimal frozen = balance.getFrozen() == null ? BigDecimal.ZERO : balance.getFrozen();
        BigDecimal available = balance.getBalance().subtract(frozen);
        return available.compareTo(normalizeAmount(amount)) >= 0;
    }

    /**
     * 懒创建账户（0 余额）。
     */
    private WorkspaceBalance ensureAccount(String workspaceId) {
        if (workspaceId == null || workspaceId.isBlank()) {
            throw new BusinessException(CODE_BAD_REQUEST, "workspace_id_required");
        }
        WorkspaceBalance existing = balanceMapper.selectByWorkspace(workspaceId);
        if (existing != null) {
            return existing;
        }
        if (workspaceMapper.selectById(workspaceId) == null) {
            throw new BusinessException(CODE_NOT_FOUND, "workspace_not_found");
        }
        WorkspaceBalance created = new WorkspaceBalance();
        // UUID 主键必须手动生成（MyBatis-Plus 的 ASSIGN_UUID 只对 String 主键自动赋值）
        created.setId(UUID.randomUUID());
        created.setWorkspaceId(workspaceId);
        created.setBalance(BigDecimal.ZERO.setScale(BillingMath.SCALE, RoundingMode.HALF_UP));
        created.setFrozen(BigDecimal.ZERO.setScale(BillingMath.SCALE, RoundingMode.HALF_UP));
        created.setCurrency(DEFAULT_CURRENCY);
        created.setStatus("active");
        created.setUpdatedAt(OffsetDateTime.now());
        created.setCreatedAt(OffsetDateTime.now());
        try {
            balanceMapper.insert(created);
            log.info("Balance account created: ws={}", workspaceId);
        } catch (org.springframework.dao.DuplicateKeyException ex) {
            // 并发创建：另一线程已建好，回读即可
        }
        WorkspaceBalance now = balanceMapper.selectByWorkspace(workspaceId);
        return now == null ? created : now;
    }

    /**
     * 写余额流水。
     *
     * @param delta  +1 入账 / -1 扣减
     * @param amount 带符号金额（入账为正、扣减为负）
     */
    private void writeLedger(String workspaceId, int delta, BigDecimal amount, String reason) {
        UsageLedger ledger = new UsageLedger();
        ledger.setId(UUID.randomUUID());
        ledger.setWorkspaceId(workspaceId);
        ledger.setAction(BALANCE_ACTION);
        ledger.setDelta(delta);
        ledger.setStatus("CONSUMED");
        ledger.setReason(truncate(reason));
        ledger.setAmount(amount);
        ledger.setCreatedAt(OffsetDateTime.now());
        usageLedgerMapper.insert(ledger);
    }

    /**
     * 判断同一 {@code orderNo} 是否已经写过余额流水（幂等）。
     * <p>
     * 入账与扣减共用这一套检查：流水 reason 里都带 {@code orderNo=xxx} 片段，
     * 因此同一单号重复触发时只生效一次。
     * </p>
     */
    private boolean existsBalanceOrderLedger(String workspaceId, String orderNo) {
        String reasonLike = "%orderNo=" + orderNo + "%";
        Long count = usageLedgerMapper.selectCount(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<UsageLedger>()
                        .eq(UsageLedger::getWorkspaceId, workspaceId)
                        .eq(UsageLedger::getAction, BALANCE_ACTION)
                        .like(UsageLedger::getReason, reasonLike)
        );
        return count != null && count > 0;
    }

    /**
     * 入账原因：必须带 {@code orderNo=xxx} 片段，
     * 否则 {@link #existsBalanceOrderLedger} 的幂等检查匹配不到（流水里没有 orderNo）。
     */
    private static String buildCreditReason(String reason, String orderNo) {
        StringBuilder sb = new StringBuilder();
        sb.append(reason == null || reason.isBlank() ? "balance_credit" : reason);
        if (orderNo != null && !orderNo.isBlank()) {
            sb.append("|orderNo=").append(orderNo);
        }
        return sb.toString();
    }

    private static String buildDebitReason(String unit, String reason, String orderNo) {
        StringBuilder sb = new StringBuilder();
        sb.append(unit == null || unit.isBlank() ? "balance_debit" : "balance_debit:" + unit);
        if (reason != null && !reason.isBlank()) {
            sb.append('|').append(reason);
        }
        if (orderNo != null && !orderNo.isBlank()) {
            sb.append("|orderNo=").append(orderNo);
        }
        return sb.toString();
    }

    private static String truncate(String raw) {
        if (raw == null) {
            return null;
        }
        return raw.length() > 255 ? raw.substring(0, 255) : raw;
    }

    private static BigDecimal normalizeAmount(BigDecimal amount) {
        if (amount == null) {
            return BigDecimal.ZERO.setScale(BillingMath.SCALE, RoundingMode.HALF_UP);
        }
        return BillingMath.scale(amount);
    }

    private BillingDtos.BalanceView toView(WorkspaceBalance balance) {
        BillingDtos.BalanceView view = new BillingDtos.BalanceView();
        if (balance == null) {
            return view;
        }
        view.setWorkspaceId(balance.getWorkspaceId());
        BigDecimal bal = balance.getBalance() == null
                ? BigDecimal.ZERO.setScale(BillingMath.SCALE, RoundingMode.HALF_UP)
                : BillingMath.scale(balance.getBalance());
        BigDecimal frozen = balance.getFrozen() == null
                ? BigDecimal.ZERO.setScale(BillingMath.SCALE, RoundingMode.HALF_UP)
                : BillingMath.scale(balance.getFrozen());
        view.setBalance(bal);
        view.setFrozen(frozen);
        view.setAvailable(BillingMath.scale(bal.subtract(frozen)));
        view.setCurrency(balance.getCurrency());
        view.setStatus(balance.getStatus());
        view.setUpdatedAt(balance.getUpdatedAt());
        return view;
    }
}
