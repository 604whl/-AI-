package com.shortvideoscripagent.xhsagentyunying.billing.refund;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.shortvideoscripagent.xhsagentyunying.billing.balance.BalanceService;
import com.shortvideoscripagent.xhsagentyunying.billing.order.domain.entity.SubscriptionOrder;
import com.shortvideoscripagent.xhsagentyunying.billing.order.domain.mapper.SubscriptionOrderMapper;
import com.shortvideoscripagent.xhsagentyunying.billing.payment.PaymentProvider;
import com.shortvideoscripagent.xhsagentyunying.billing.payment.PaymentProviderRegistry;
import com.shortvideoscripagent.xhsagentyunying.billing.refund.domain.entity.RefundOrder;
import com.shortvideoscripagent.xhsagentyunying.billing.refund.domain.mapper.RefundOrderMapper;
import com.shortvideoscripagent.xhsagentyunying.billing.subscription.SubscriptionService;
import com.shortvideoscripagent.xhsagentyunying.billing.support.BillingMath;
import com.shortvideoscripagent.xhsagentyunying.billing.support.IdempotencyReplay;
import com.shortvideoscripagent.xhsagentyunying.common.exception.BusinessException;
import com.shortvideoscripagent.xhsagentyunying.dto.billing.refund.RefundDtos;
import com.shortvideoscripagent.xhsagentyunying.infra.audit.AuditWriter;
import com.shortvideoscripagent.xhsagentyunying.infra.ratelimit.RedisRateLimiter;
import com.shortvideoscripagent.xhsagentyunying.quota.QuotaRefundService;
import com.shortvideoscripagent.xhsagentyunying.quota.domain.entity.UsageLedger;
import com.shortvideoscripagent.xhsagentyunying.quota.domain.mapper.UsageLedgerMapper;
import com.shortvideoscripagent.xhsagentyunying.workspace.WorkspaceRole;
import com.shortvideoscripagent.xhsagentyunying.workspace.WorkspaceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * S4-4 退款服务。
 * <p>
 * <b>流程</b>：用户申请 → 平台管理员审核 → 调用渠道退款 → 渠道确认 → 额度 / 余额冲正 →（若已开票）发票红冲。
 * </p>
 * <p>
 * <b>状态机</b>（{@code app.refund_order.status}）：
 * <pre>
 * pending --审核通过--> approved --渠道退款成功--> refunded
 *   |                       |
 *   |--审核驳回--> rejected  |--渠道退款失败--> failed
 * </pre>
 * </p>
 * <p>
 * <b>P2-1 事务边界</b>：调用渠道退款是<b>同步 HTTP 远程调用</b>，
 * 绝不能被包在数据库事务里（渠道慢/挂起会把连接与行锁持有到 HTTP 超时；
 * 远程失败还会把已落库的退款单整个回滚）。因此审核通过拆成三段：
 * <ol>
 *   <li><b>事务 1</b>：{@code pending → approved}（条件更新）并提交；</li>
 *   <li><b>事务外</b>：调用 {@code provider.refund(...)}，不持有任何 DB 事务与行锁；</li>
 *   <li><b>事务 2</b>：{@code approved → refunded} + 权益冲正 + 发票红冲。</li>
 * </ol>
 * </p>
 * <p>
 * <b>幂等</b>：三重锚点。
 * <ul>
 *   <li><b>申请幂等</b>：{@code (workspace_id, idempotency_key)} 唯一索引；</li>
 *   <li><b>审核幂等</b>：状态条件更新（{@code WHERE status = 'pending'}），并发只有一个成功；</li>
 *   <li><b>渠道幂等</b>：{@code provider_refund_id} 唯一索引 —— 同一渠道退款事件重复投递只生效一次。</li>
 * </ul>
 * </p>
 * <p>
 * <b>额度冲正复用 {@link QuotaRefundService}</b>，不重复实现扣费逻辑。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RefundService {

    /** 退款单状态：待审核 */
    public static final String STATUS_PENDING = "pending";
    /** 退款单状态：审核通过 */
    public static final String STATUS_APPROVED = "approved";
    /** 退款单状态：审核驳回 */
    public static final String STATUS_REJECTED = "rejected";
    /** 退款单状态：退款完成 */
    public static final String STATUS_REFUNDED = "refunded";
    /** 退款单状态：渠道退款失败 */
    public static final String STATUS_FAILED = "failed";

    /** 申请退款限流：3 次/天/工作区 */
    private static final int LIMIT_CREATE_REFUND_PER_DAY = 3;
    private static final int WINDOW_ONE_DAY = 24 * 60 * 60;

    private static final int CODE_BAD_REQUEST = 40016;
    private static final int CODE_NOT_FOUND = 40416;
    private static final int CODE_CONFLICT = 40916;
    private static final int CODE_FORBIDDEN = 40316;

    private final RefundOrderMapper refundMapper;
    private final SubscriptionOrderMapper orderMapper;
    private final PaymentProviderRegistry providerRegistry;
    private final WorkspaceService workspaceService;
    private final AuditWriter auditWriter;
    private final RedisRateLimiter rateLimiter;
    private final IdempotencyReplay idempotencyReplay;
    private final BalanceService balanceService;
    private final SubscriptionService subscriptionService;
    private final QuotaRefundService quotaRefundService;
    private final UsageLedgerMapper usageLedgerMapper;
    private final org.springframework.transaction.PlatformTransactionManager transactionManager;
    /**
     * 发票服务（惰性获取，避免退款域 ↔ 发票域循环依赖）。
     */
    private final org.springframework.beans.factory.ObjectProvider<
            com.shortvideoscripagent.xhsagentyunying.billing.invoice.InvoiceService> invoiceServiceProvider;

    // ══════════════════════════════════════════════════════════════════
    // 读
    // ══════════════════════════════════════════════════════════════════

    /**
     * 工作区退款单分页列表（OWNER / ADMIN）。
     */
    public Page<RefundDtos.RefundView> listByWorkspace(String workspaceId, Long userId,
                                                       String status, int page, int size) {
        WorkspaceRole role = requireMember(workspaceId, userId);
        if (!role.atLeast(WorkspaceRole.ADMIN)) {
            throw new BusinessException(CODE_FORBIDDEN, "insufficient_role");
        }
        LambdaQueryWrapper<RefundOrder> q = new LambdaQueryWrapper<>();
        q.eq(RefundOrder::getWorkspaceId, workspaceId);
        if (status != null && !status.isBlank()) {
            q.eq(RefundOrder::getStatus, normalizeStatus(status));
        }
        q.orderByDesc(RefundOrder::getCreatedAt);

        Page<RefundOrder> p = refundMapper.selectPage(
                new Page<>(Math.max(page, 1), Math.min(Math.max(size, 1), 100)), q);
        Page<RefundDtos.RefundView> result = new Page<>(p.getCurrent(), p.getSize(), p.getTotal());
        result.setRecords(p.getRecords().stream().map(this::toView).toList());
        return result;
    }

    /**
     * 管理端退款单分页列表。
     */
    public Page<RefundDtos.RefundView> listForAdmin(String status, String workspaceId,
                                                     int page, int size) {
        LambdaQueryWrapper<RefundOrder> q = new LambdaQueryWrapper<>();
        if (status != null && !status.isBlank()) {
            q.eq(RefundOrder::getStatus, normalizeStatus(status));
        }
        if (workspaceId != null && !workspaceId.isBlank()) {
            q.eq(RefundOrder::getWorkspaceId, workspaceId.trim());
        }
        q.orderByDesc(RefundOrder::getCreatedAt);

        Page<RefundOrder> p = refundMapper.selectPage(
                new Page<>(Math.max(page, 1), Math.min(Math.max(size, 1), 100)), q);
        Page<RefundDtos.RefundView> result = new Page<>(p.getCurrent(), p.getSize(), p.getTotal());
        result.setRecords(p.getRecords().stream().map(this::toView).toList());
        return result;
    }

    /**
     * 按退款单号查退款单（工作区隔离由调用方负责）。
     */
    public RefundDtos.RefundView getByRefundNo(String refundNo, Long userId) {
        RefundOrder refund = requireByRefundNo(refundNo);
        requireMember(refund.getWorkspaceId(), userId);
        return toView(refund);
    }

    /**
     * 按渠道退款单号查退款单（渠道回调幂等锚点）。
     */
    public RefundOrder requireByProviderRefundId(String providerRefundId) {
        if (providerRefundId == null || providerRefundId.isBlank()) {
            throw new BusinessException(CODE_BAD_REQUEST, "provider_refund_id_required");
        }
        RefundOrder refund = refundMapper.selectByProviderRefundId(providerRefundId);
        if (refund == null) {
            throw new BusinessException(CODE_NOT_FOUND, "refund_not_found");
        }
        return refund;
    }

    // ══════════════════════════════════════════════════════════════════
    // 写：申请退款
    // ══════════════════════════════════════════════════════════════════

    /**
     * 申请退款（幂等；3 次/天/工作区；OWNER only）。
     * <p>
     * <b>校验链</b>：
     * <ol>
     *   <li>当前用户是 workspace 的 OWNER；</li>
     *   <li>订单存在且属于该 workspace（跨 workspace 一律 404）；</li>
     *   <li>订单处于可退款状态（{@code paid} / {@code fulfilled}）；</li>
     *   <li>退款金额 &gt; 0 且「累计已承诺退款额（approved + refunded）+ 本次」≤ 订单实付金额。</li>
     * </ol>
     * </p>
     */
    public RefundDtos.RefundView requestRefund(String workspaceId, Long userId,
                                                RefundDtos.CreateRefundRequest request,
                                                String idempotencyKey) {
        requireOwner(workspaceId, userId);
        rateLimiter.require("workspace", workspaceId + ":refund:create",
                LIMIT_CREATE_REFUND_PER_DAY, WINDOW_ONE_DAY);

        if (request == null || request.getOrderNo() == null || request.getOrderNo().isBlank()) {
            throw new BusinessException(CODE_BAD_REQUEST, "order_no_required");
        }
        if (request.getReason() == null || request.getReason().isBlank()) {
            throw new BusinessException(CODE_BAD_REQUEST, "reason_required");
        }

        String key = normalizeKey(idempotencyKey);
        if (key != null) {
            RefundOrder existing = refundMapper.selectByIdempotencyKey(workspaceId, key);
            if (existing != null) {
                log.info("Refund request replay (idempotent): ws={} refundNo={}",
                        workspaceId, existing.getRefundNo());
                return toView(existing);
            }
        }

        SubscriptionOrder order = orderMapper.selectByOrderNo(request.getOrderNo().trim());
        if (order == null || !workspaceId.equals(order.getWorkspaceId())) {
            // 跨 workspace 一律 404，不泄露订单存在性
            throw new BusinessException(CODE_NOT_FOUND, "order_not_found");
        }
        if (!"paid".equals(order.getStatus()) && !"fulfilled".equals(order.getStatus())) {
            throw new BusinessException(CODE_CONFLICT, "order_not_refundable");
        }

        BigDecimal orderAmount = BillingMath.scale(order.getAmount());
        BigDecimal refundAmount = request.getAmount() == null
                ? orderAmount
                : BillingMath.scale(request.getAmount());
        if (refundAmount.signum() <= 0) {
            throw new BusinessException(CODE_BAD_REQUEST, "invalid_refund_amount");
        }

        // 累计已承诺退款额（approved + refunded）+ 本次 ≤ 订单实付金额
        BigDecimal committed = refundMapper.sumCommittedAmount(order.getId());
        if (committed == null) {
            committed = BigDecimal.ZERO;
        }
        BigDecimal total = committed.add(refundAmount);
        if (total.compareTo(orderAmount) > 0) {
            log.warn("Refund exceeds order amount: orderNo={} order={} committed={} requested={}",
                    order.getOrderNo(), orderAmount.toPlainString(),
                    committed.toPlainString(), refundAmount.toPlainString());
            throw new BusinessException(CODE_BAD_REQUEST, "refund_amount_exceeds_order");
        }

        OffsetDateTime now = OffsetDateTime.now();
        RefundOrder refund = new RefundOrder();
        refund.setId(UUID.randomUUID());
        refund.setRefundNo(generateRefundNo());
        refund.setOrderId(order.getId());
        refund.setOrderNo(order.getOrderNo());
        refund.setWorkspaceId(workspaceId);
        refund.setAmount(refundAmount);
        refund.setCurrency(order.getCurrency() == null ? "CNY" : order.getCurrency());
        refund.setReason(truncate(request.getReason(), 512));
        refund.setStatus(STATUS_PENDING);
        refund.setRequestedBy(userId);
        refund.setProvider(order.getProvider());
        refund.setIdempotencyKey(key);
        refund.setCreatedAt(now);
        refund.setUpdatedAt(now);

        try {
            newTransaction().executeWithoutResult(status -> refundMapper.insert(refund));
        } catch (DuplicateKeyException ex) {
            // 并发重复提交：唯一索引兜底，在独立事务里回读
            if (key != null) {
                RefundOrder existing = idempotencyReplay.read(
                        () -> refundMapper.selectByIdempotencyKey(workspaceId, key));
                if (existing != null) {
                    return toView(existing);
                }
            }
            throw new BusinessException(CODE_CONFLICT, "refund_create_conflict");
        }

        auditWriter.record("REFUND_REQUESTED", "REFUND_ORDER", refund.getId().toString(),
                Map.of("workspaceId", workspaceId,
                        "refundNo", refund.getRefundNo(),
                        "orderNo", order.getOrderNo(),
                        "amount", refundAmount.toPlainString(),
                        "currency", refund.getCurrency()));

        log.info("Refund requested: refundNo={} ws={} orderNo={} amount={}",
                refund.getRefundNo(), workspaceId, order.getOrderNo(), refundAmount.toPlainString());
        return toView(refund);
    }

    // ══════════════════════════════════════════════════════════════════
    // 写：审核
    // ══════════════════════════════════════════════════════════════════

    /**
     * 审核通过退款单并调用渠道退款。
     * <p>
     * <b>P2-1 事务边界</b>：渠道退款是同步 HTTP 远程调用，必须在事务外执行。
     * </p>
     * <p>
     * <b>幂等</b>：{@code pending → approved} 用条件更新保证并发只有一个成功；
     * 重复审核（已经是 {@code approved} / {@code refunded}）直接返回既有结果，
     * <b>不会重复调用渠道退款</b>（渠道退款本身也带幂等键）。
     * </p>
     */
    public RefundDtos.RefundResult approve(String refundNo, Long reviewerId, String note) {
        RefundOrder refund = requireByRefundNo(refundNo);
        if (STATUS_APPROVED.equals(refund.getStatus())) {
            // 已审核通过（可能正在流程中）→ 幂等返回，不重复调渠道
            log.info("Refund already approved, skip: refundNo={}", refundNo);
            return buildResult(refund, true, false, false);
        }
        if (STATUS_REFUNDED.equals(refund.getStatus())) {
            return buildResult(refund, true, true, false);
        }
        if (!STATUS_PENDING.equals(refund.getStatus())) {
            throw new BusinessException(CODE_CONFLICT, "refund_not_approvable");
        }

        SubscriptionOrder order = orderMapper.selectById(refund.getOrderId());
        if (order == null) {
            throw new BusinessException(CODE_NOT_FOUND, "order_not_found");
        }

        // ── 事务 1：pending → approved ──
        int moved = refundMapper.compareAndSetApproved(refund.getId(), reviewerId,
                OffsetDateTime.now(), truncate(note, 512));
        if (moved == 0) {
            RefundOrder current = refundMapper.selectById(refund.getId());
            log.info("Refund approve conflict (already handled): refundNo={} status={}",
                    refundNo, current == null ? "unknown" : current.getStatus());
            return buildResult(current == null ? refund : current, true,
                    STATUS_REFUNDED.equals(current == null ? null : current.getStatus()), false);
        }

        // ── 事务外：调渠道退款（不持有任何 DB 事务与行锁） ──
        PaymentProvider provider = providerRegistry.require(order.getProvider());
        PaymentProvider.RefundResult providerResult;
        try {
            providerResult = provider.refund(new PaymentProvider.RefundRequest(
                    order.getOrderNo(),
                    order.getProviderOrderId(),
                    refund.getAmount(),
                    refund.getReason(),
                    refund.getRefundNo()));
        } catch (RuntimeException ex) {
            markFailed(refund, "provider_refund_failed");
            auditWriter.recordFailure("REFUND_APPROVE_FAILED", "REFUND_ORDER",
                    refund.getId().toString(),
                    ex instanceof BusinessException be ? be.getCode() : null,
                    "provider_refund_failed",
                    Map.of("refundNo", refundNo,
                            "orderNo", order.getOrderNo(),
                            "provider", order.getProvider()));
            throw ex;
        }

        // ── 事务 2：approved → refunded + 权益冲正 ──
        boolean reversed = false;
        boolean redFlushed = false;
        if (providerResult != null && providerResult.accepted()) {
            RefundOrder reloaded = refundMapper.selectById(refund.getId());
            OffsetDateTime refundedAt = OffsetDateTime.now();
            int updated = refundMapper.compareAndSetRefunded(refund.getId(),
                    providerResult.providerRefundId(), refundedAt);
            if (updated > 0) {
                reversed = reverseEntitlements(order, refund, providerResult.providerRefundId());
                redFlushed = redFlushInvoiceIfNeeded(order, refund);
                auditWriter.record("REFUND_COMPLETED", "REFUND_ORDER", refund.getId().toString(),
                        Map.of("workspaceId", refund.getWorkspaceId(),
                                "refundNo", refundNo,
                                "orderNo", order.getOrderNo(),
                                "amount", refund.getAmount().toPlainString(),
                                "providerRefundId", providerResult.providerRefundId() == null
                                        ? "" : providerResult.providerRefundId(),
                                "entitlementReversed", String.valueOf(reversed),
                                "invoiceRedFlushed", String.valueOf(redFlushed)));
                log.info("Refund completed: refundNo={} orderNo={} amount={} reversed={} redFlushed={}",
                        refundNo, order.getOrderNo(), refund.getAmount().toPlainString(),
                        reversed, redFlushed);
            } else {
                // 并发 / 重复：已有其它线程处理过（渠道回调先到）
                log.info("Refund completion conflict (already handled): refundNo={}", refundNo);
            }
            RefundOrder current = refundMapper.selectById(refund.getId());
            return buildResult(current == null ? reloaded : current, true, true, redFlushed);
        }

        // 渠道未受理（如需要异步处理）→ 保持 approved，等待渠道回调确认
        RefundOrder current = refundMapper.selectById(refund.getId());
        auditWriter.record("REFUND_SUBMITTED", "REFUND_ORDER", refund.getId().toString(),
                Map.of("refundNo", refundNo,
                        "orderNo", order.getOrderNo(),
                        "provider", order.getProvider(),
                        "providerRefundId", providerResult == null || providerResult.providerRefundId() == null
                                ? "" : providerResult.providerRefundId()));
        return buildResult(current == null ? refund : current,
                providerResult != null && providerResult.accepted(), false, false);
    }

    /**
     * 审核驳回退款单。
     * <p>
     * <b>幂等</b>：{@code pending → rejected} 条件更新；已驳回重复调用直接返回既有结果。
     * </p>
     */
    public RefundDtos.RefundResult reject(String refundNo, Long reviewerId, String note) {
        RefundOrder refund = requireByRefundNo(refundNo);
        if (STATUS_REJECTED.equals(refund.getStatus())) {
            log.info("Refund already rejected, skip: refundNo={}", refundNo);
            return buildResult(refund, false, false, false);
        }
        if (!STATUS_PENDING.equals(refund.getStatus())) {
            throw new BusinessException(CODE_CONFLICT, "refund_not_rejectable");
        }
        if (note == null || note.isBlank()) {
            throw new BusinessException(CODE_BAD_REQUEST, "review_note_required");
        }

        int moved = refundMapper.compareAndSetRejected(refund.getId(), reviewerId,
                OffsetDateTime.now(), truncate(note, 512));
        if (moved == 0) {
            RefundOrder current = refundMapper.selectById(refund.getId());
            log.info("Refund reject conflict (already handled): refundNo={} status={}",
                    refundNo, current == null ? "unknown" : current.getStatus());
            return buildResult(current == null ? refund : current, false, false, false);
        }

        auditWriter.record("REFUND_REJECTED", "REFUND_ORDER", refund.getId().toString(),
                Map.of("workspaceId", refund.getWorkspaceId(),
                        "refundNo", refundNo,
                        "orderNo", refund.getOrderNo(),
                        "amount", refund.getAmount().toPlainString(),
                        "note", truncate(note, 512)));

        log.info("Refund rejected: refundNo={} ws={}", refundNo, refund.getWorkspaceId());
        RefundOrder current = refundMapper.selectById(refund.getId());
        return buildResult(current == null ? refund : current, false, false, false);
    }

    // ══════════════════════════════════════════════════════════════════
    // 写：渠道回调确认
    // ══════════════════════════════════════════════════════════════════

    /**
     * 渠道退款回调确认。
     * <p>
     * <b>幂等锚点</b>：{@code provider_refund_id} 唯一索引 ——
     * 同一渠道退款事件重复投递只生效一次（第一次完成后，后续直接返回既有结果，
     * <b>不会重复冲正额度 / 余额</b>）。
     * </p>
     *
     * @param providerRefundId 渠道退款单号
     * @param success          渠道是否确认退款成功
     */
    @Transactional
    public RefundDtos.RefundResult confirmFromCallback(String providerRefundId, boolean success,
                                                        String failureReason) {
        RefundOrder refund = requireByProviderRefundId(providerRefundId);

        if (STATUS_REFUNDED.equals(refund.getStatus())) {
            log.info("Duplicate refund callback ignored: providerRefundId={}", providerRefundId);
            return buildResult(refund, true, true, false);
        }
        if (!STATUS_APPROVED.equals(refund.getStatus())) {
            // 只有 approved 能被确认；pending 说明还没审核通过
            throw new BusinessException(CODE_CONFLICT, "refund_not_approved");
        }

        if (!success) {
            int updated = refundMapper.compareAndSetFailed(refund.getId(),
                    truncate(failureReason == null ? "provider_refund_failed" : failureReason, 128));
            auditWriter.recordFailure("REFUND_CALLBACK_FAILED", "REFUND_ORDER",
                    refund.getId().toString(), null, "provider_refund_failed",
                    Map.of("refundNo", refund.getRefundNo(),
                            "providerRefundId", providerRefundId));
            RefundOrder current = refundMapper.selectById(refund.getId());
            return buildResult(current == null ? refund : current, false, false, false);
        }

        SubscriptionOrder order = orderMapper.selectById(refund.getOrderId());
        if (order == null) {
            throw new BusinessException(CODE_NOT_FOUND, "order_not_found");
        }

        int updated = refundMapper.compareAndSetRefunded(refund.getId(), providerRefundId,
                OffsetDateTime.now());
        if (updated == 0) {
            // 并发重复回调：另一个线程已完成
            RefundOrder current = refundMapper.selectById(refund.getId());
            log.info("Refund callback conflict (already handled): providerRefundId={}", providerRefundId);
            return buildResult(current == null ? refund : current, true, true, false);
        }

        boolean reversed = reverseEntitlements(order, refund, providerRefundId);
        boolean redFlushed = redFlushInvoiceIfNeeded(order, refund);

        auditWriter.record("REFUND_COMPLETED", "REFUND_ORDER", refund.getId().toString(),
                Map.of("workspaceId", refund.getWorkspaceId(),
                        "refundNo", refund.getRefundNo(),
                        "orderNo", order.getOrderNo(),
                        "amount", refund.getAmount().toPlainString(),
                        "providerRefundId", providerRefundId,
                        "entitlementReversed", String.valueOf(reversed),
                        "invoiceRedFlushed", String.valueOf(redFlushed)));

        log.info("Refund confirmed by callback: refundNo={} amount={} reversed={} redFlushed={}",
                refund.getRefundNo(), refund.getAmount().toPlainString(), reversed, redFlushed);

        RefundOrder current = refundMapper.selectById(refund.getId());
        return buildResult(current == null ? refund : current, true, true, redFlushed);
    }

    // ══════════════════════════════════════════════════════════════════
    // private
    // ══════════════════════════════════════════════════════════════════

    /**
     * 退款成功后回补 / 收回业务权益。
     * <p>
     * <b>复用既有能力，不重复实现扣费逻辑</b>：
     * <ul>
     *   <li><b>按量充值订单</b>（{@code planId == null}）→ {@link BalanceService#creditBalance}
     *       余额回补（自带 {@code orderNo} 幂等，重复退款不会重复入账）；</li>
     *   <li><b>订阅订单</b> → {@link SubscriptionService#cancelForRefund}
     *       取消订阅 + 降级额度（否则用户退了钱还继续享受 PRO 额度）。</li>
     * </ul>
     * </p>
     * <p>
     * <b>额度冲正</b>：若订单周期内存在该工作区的可冲正台账行，
     * 复用 {@link QuotaRefundService} 冲正（写 {@code delta = -1} 冲正台账）。
     * </p>
     * <p>
     * <b>失败语义</b>：权益回补失败<b>不能</b>让渠道退款回滚（钱已经退给用户了），
     * 但必须留下 ERROR 记录供人工补偿。
     * </p>
     */
    private boolean reverseEntitlements(SubscriptionOrder order, RefundOrder refund,
                                        String providerRefundId) {
        try {
            if (order.getPlanId() == null) {
                // 按量充值：余额回补。用 "refund:" + refundNo 作为幂等键
                balanceService.creditBalance(order.getWorkspaceId(), refund.getAmount(),
                        refund.getCurrency(), "refund:" + refund.getRefundNo(), "order_refund");
                log.info("Refund credited balance: refundNo={} amount={}",
                        refund.getRefundNo(), refund.getAmount().toPlainString());
            } else {
                // 订阅：取消订阅并降级额度（不删数据，只收窄能力）
                subscriptionService.cancelForRefund(order.getWorkspaceId());
                log.info("Refund canceled subscription: refundNo={} ws={}",
                        refund.getRefundNo(), order.getWorkspaceId());
            }
            // 台账冲正（订阅订单周期内的扣费行）—— 复用 QuotaRefundService
            reverseUsageLedger(order, refund);
            return true;
        } catch (RuntimeException ex) {
            log.error("Failed to reverse entitlements for refund {}: {}",
                    refund.getRefundNo(), ex.getMessage(), ex);
            auditWriter.recordFailure("REFUND_ENTITLEMENT_FAILED", "REFUND_ORDER",
                    refund.getId().toString(), null, "entitlement_reversal_failed",
                    Map.of("workspaceId", order.getWorkspaceId(),
                            "refundNo", refund.getRefundNo(),
                            "orderNo", order.getOrderNo(),
                            "amount", refund.getAmount().toPlainString()));
            return false;
        }
    }

    /**
     * 冲正该工作区在退款订单周期内仍生效的用量台账。
     * <p>
     * 只冲正「未出账」的台账行（{@code statement_id IS NULL}）——
     * 已出账的扣费属于历史账单，不应被追溯改写（账单是不可变凭证）。
     * </p>
     */
    private void reverseUsageLedger(SubscriptionOrder order, RefundOrder refund) {
        List<UsageLedger> candidates = usageLedgerMapper.selectUnbilledByWorkspace(
                order.getWorkspaceId(), 100);
        for (UsageLedger ledger : candidates) {
            try {
                quotaRefundService.refund(ledger.getId(), "order_refund:" + refund.getRefundNo());
            } catch (RuntimeException ex) {
                log.warn("Failed to reverse ledger {} for refund {}: {}",
                        ledger.getId(), refund.getRefundNo(), ex.getMessage());
            }
        }
    }

    /**
     * 已开票订单退款时做发票红冲。
     * <p>
     * 由 {@code InvoiceService} 实现（避免退款域与发票域循环依赖，
     * 这里通过 Spring 的 {@code ObjectProvider} 惰性获取）。
     * </p>
     */
    private boolean redFlushInvoiceIfNeeded(SubscriptionOrder order, RefundOrder refund) {
        try {
            com.shortvideoscripagent.xhsagentyunying.billing.invoice.InvoiceService invoiceService =
                    invoiceServiceProvider.getIfAvailable();
            if (invoiceService == null) {
                return false;
            }
            return invoiceService.redFlushByOrder(order.getId(), refund.getRefundNo(),
                    refund.getAmount());
        } catch (RuntimeException ex) {
            log.error("Failed to red-flush invoice for refund {}: {}",
                    refund.getRefundNo(), ex.getMessage(), ex);
            auditWriter.recordFailure("REFUND_INVOICE_RED_FLUSH_FAILED", "REFUND_ORDER",
                    refund.getId().toString(), null, "invoice_red_flush_failed",
                    Map.of("refundNo", refund.getRefundNo(),
                            "orderNo", order.getOrderNo()));
            return false;
        }
    }

    /**
     * 渠道退款失败：{@code approved → failed}。
     */
    private void markFailed(RefundOrder refund, String reason) {
        try {
            newTransaction().executeWithoutResult(status ->
                    refundMapper.compareAndSetFailed(refund.getId(), truncate(reason, 128)));
        } catch (RuntimeException ex) {
            log.error("Failed to mark refund failed: refundNo={} err={}",
                    refund.getRefundNo(), ex.getMessage());
        }
    }

    /**
     * 新建一个「独立事务」模板（{@code PROPAGATION_REQUIRES_NEW}）。
     */
    private TransactionTemplate newTransaction() {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return template;
    }

    /**
     * 校验当前用户是 workspace 成员；不是成员一律 404（不泄露存在性）。
     */
    WorkspaceRole requireMember(String workspaceId, Long userId) {
        if (userId == null) {
            throw new BusinessException(CODE_FORBIDDEN, "unauthorized");
        }
        WorkspaceRole role = workspaceService.findRole(userId, workspaceId);
        if (role == null) {
            throw new BusinessException(CODE_NOT_FOUND, "refund_not_found");
        }
        return role;
    }

    /**
     * 校验 OWNER 角色。
     */
    void requireOwner(String workspaceId, Long userId) {
        WorkspaceRole role = requireMember(workspaceId, userId);
        if (role != WorkspaceRole.OWNER) {
            throw new BusinessException(CODE_FORBIDDEN, "owner_required");
        }
    }

    /**
     * 按退款单号取退款单。
     */
    public RefundOrder requireByRefundNo(String refundNo) {
        if (refundNo == null || refundNo.isBlank()) {
            throw new BusinessException(CODE_BAD_REQUEST, "refund_no_required");
        }
        LambdaQueryWrapper<RefundOrder> q = new LambdaQueryWrapper<>();
        q.eq(RefundOrder::getRefundNo, refundNo.trim()).last("LIMIT 1");
        RefundOrder refund = refundMapper.selectOne(q);
        if (refund == null) {
            throw new BusinessException(CODE_NOT_FOUND, "refund_not_found");
        }
        return refund;
    }

    /**
     * 退款单号生成：{@code RF} + 时间戳（毫秒，base36）+ 随机数，VARCHAR(32) 内且全局唯一。
     */
    private String generateRefundNo() {
        String ts = Long.toString(System.currentTimeMillis(), 36);
        String rand = Integer.toString(ThreadLocalRandom.current().nextInt(1_000_000), 36);
        String no = "RF" + ts + rand.toUpperCase();
        return no.length() > 32 ? no.substring(0, 32) : no;
    }

    private RefundDtos.RefundResult buildResult(RefundOrder refund, boolean accepted,
                                                 boolean reversed, boolean redFlushed) {
        RefundDtos.RefundResult result = new RefundDtos.RefundResult();
        if (refund == null) {
            return result;
        }
        result.setRefundNo(refund.getRefundNo());
        result.setOrderNo(refund.getOrderNo());
        result.setStatus(refund.getStatus());
        result.setAmount(refund.getAmount());
        result.setProviderRefundId(refund.getProviderRefundId());
        result.setAccepted(accepted);
        result.setEntitlementReversed(reversed);
        result.setInvoiceRedFlushed(redFlushed);
        return result;
    }

    /**
     * 退款单视图转换。
     */
    public RefundDtos.RefundView toView(RefundOrder refund) {
        if (refund == null) {
            return null;
        }
        RefundDtos.RefundView v = new RefundDtos.RefundView();
        v.setId(refund.getId() == null ? null : refund.getId().toString());
        v.setRefundNo(refund.getRefundNo());
        v.setOrderId(refund.getOrderId() == null ? null : refund.getOrderId().toString());
        v.setOrderNo(refund.getOrderNo());
        v.setWorkspaceId(refund.getWorkspaceId());
        v.setAmount(refund.getAmount());
        v.setCurrency(refund.getCurrency());
        v.setReason(refund.getReason());
        v.setStatus(refund.getStatus());
        v.setRequestedBy(refund.getRequestedBy());
        v.setReviewedBy(refund.getReviewedBy());
        v.setReviewedAt(refund.getReviewedAt());
        v.setReviewNote(refund.getReviewNote());
        v.setProvider(refund.getProvider());
        v.setProviderRefundId(refund.getProviderRefundId());
        v.setRefundedAt(refund.getRefundedAt());
        v.setFailureReason(refund.getFailureReason());
        v.setCreatedAt(refund.getCreatedAt());
        return v;
    }

    private static String normalizeStatus(String raw) {
        String s = raw == null ? "" : raw.trim().toLowerCase();
        return switch (s) {
            case STATUS_PENDING, STATUS_APPROVED, STATUS_REJECTED, STATUS_REFUNDED, STATUS_FAILED -> s;
            default -> throw new BusinessException(CODE_BAD_REQUEST, "invalid_refund_status");
        };
    }

    private static String normalizeKey(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String trimmed = raw.trim();
        return trimmed.length() > 128 ? trimmed.substring(0, 128) : trimmed;
    }

    private static String truncate(String raw, int max) {
        if (raw == null) {
            return null;
        }
        return raw.length() > max ? raw.substring(0, max) : raw;
    }
}
