package com.shortvideoscripagent.xhsagentyunying.billing.order;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.shortvideoscripagent.xhsagentyunying.billing.order.domain.entity.SubscriptionOrder;
import com.shortvideoscripagent.xhsagentyunying.billing.order.domain.mapper.SubscriptionOrderMapper;
import com.shortvideoscripagent.xhsagentyunying.billing.payment.PaymentErrors;
import com.shortvideoscripagent.xhsagentyunying.billing.payment.PaymentProvider;
import com.shortvideoscripagent.xhsagentyunying.billing.payment.PaymentProviderRegistry;
import com.shortvideoscripagent.xhsagentyunying.billing.plan.PlanService;
import com.shortvideoscripagent.xhsagentyunying.billing.plan.domain.entity.SubscriptionPlan;
import com.shortvideoscripagent.xhsagentyunying.billing.support.BillingMath;
import com.shortvideoscripagent.xhsagentyunying.common.exception.BusinessException;
import com.shortvideoscripagent.xhsagentyunying.dto.billing.order.OrderDtos;
import com.shortvideoscripagent.xhsagentyunying.infra.audit.AuditWriter;
import com.shortvideoscripagent.xhsagentyunying.infra.ratelimit.RedisRateLimiter;
import com.shortvideoscripagent.xhsagentyunying.infra.taskqueue.TaskQueueProducer;
import com.shortvideoscripagent.xhsagentyunying.workspace.WorkspaceRole;
import com.shortvideoscripagent.xhsagentyunying.workspace.WorkspaceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * S4-2 订单服务。
 * <p>
 * <b>状态机</b>：{@code pending → paid → (fulfilled | refunded | closed)}；
 * {@code pending} 超时 → {@code closed}。
 * </p>
 * <p>
 * <b>幂等</b>：写接口的 {@code Idempotency-Key} 落在
 * {@code app.subscription_order.idempotency_key} 上，配合唯一索引
 * {@code (workspace_id, idempotency_key)}；重复提交返回既有订单。
 * </p>
 * <p>
 * <b>租户隔离</b>：跨 workspace 访问一律 404（不泄露存在性）。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

    private static final int CODE_NOT_FOUND = 40412;
    private static final int CODE_BAD_REQUEST = 40015;
    private static final int CODE_CONFLICT = 40912;
    private static final int CODE_FORBIDDEN = 40312;

    /** 下单限流：10 次/天/工作区 */
    private static final int LIMIT_CREATE_ORDER_PER_DAY = 10;
    private static final int WINDOW_ONE_DAY = 24 * 60 * 60;

    /** 订单有效期（分钟） */
    private static final int ORDER_TTL_MINUTES = 30;

    /** 履约任务类型 */
    public static final String TASK_FULFILL_ORDER = "BILLING_FULFILL_ORDER";

    private final SubscriptionOrderMapper orderMapper;
    private final PlanService planService;
    private final PaymentProviderRegistry providerRegistry;
    private final WorkspaceService workspaceService;
    private final AuditWriter auditWriter;
    private final RedisRateLimiter rateLimiter;
    private final TaskQueueProducer taskQueueProducer;
    private final com.shortvideoscripagent.xhsagentyunying.billing.support.IdempotencyReplay idempotencyReplay;
    private final com.shortvideoscripagent.xhsagentyunying.billing.balance.BalanceService balanceService;
    private final com.shortvideoscripagent.xhsagentyunying.billing.subscription.SubscriptionService subscriptionService;
    private final org.springframework.transaction.PlatformTransactionManager transactionManager;

    // ══════════════════════════════════════════════════════════════════
    // 读
    // ══════════════════════════════════════════════════════════════════

    /**
     * 工作区订单分页列表（OWNER / ADMIN）。
     */
    public Page<OrderDtos.OrderView> listByWorkspace(String workspaceId, Long userId,
                                                       String status, int page, int size) {
        // P3-3：注释与接口契约声明「OWNER / ADMIN」，早期实现只校验「是成员」，
        // 导致 VIEWER / REVIEWER 也能读到全部订单号、金额、渠道订单号。
        WorkspaceRole role = requireMember(workspaceId, userId);
        if (!role.atLeast(WorkspaceRole.ADMIN)) {
            throw new BusinessException(CODE_FORBIDDEN, "insufficient_role");
        }
        LambdaQueryWrapper<SubscriptionOrder> q = new LambdaQueryWrapper<>();
        q.eq(SubscriptionOrder::getWorkspaceId, workspaceId);
        if (status != null && !status.isBlank()) {
            String normalized = status.trim().toLowerCase();
            if (OrderStatus.parse(normalized) == null) {
                throw new BusinessException(CODE_BAD_REQUEST, "invalid_order_status");
            }
            q.eq(SubscriptionOrder::getStatus, normalized);
        }
        q.orderByDesc(SubscriptionOrder::getCreatedAt);

        Page<SubscriptionOrder> p = orderMapper.selectPage(
                new Page<>(Math.max(page, 1), Math.min(Math.max(size, 1), 100)), q);
        Page<OrderDtos.OrderView> result = new Page<>(p.getCurrent(), p.getSize(), p.getTotal());
        result.setRecords(p.getRecords().stream().map(this::toView).toList());
        return result;
    }

    /**
     * 按订单号读订单（OWNER / ADMIN）。
     * <p>
     * 跨 workspace 一律 404。
     * </p>
     */
    public OrderDtos.OrderView getByOrderNo(String orderNo, Long userId) {
        SubscriptionOrder order = requireOrder(orderNo);
        requireMember(order.getWorkspaceId(), userId);
        return toView(order);
    }

    /**
     * 管理端分页查订单。
     */
    public Page<OrderDtos.OrderView> listForAdmin(String status, String provider,
                                                  String workspaceId, int page, int size) {
        LambdaQueryWrapper<SubscriptionOrder> q = new LambdaQueryWrapper<>();
        if (status != null && !status.isBlank()) {
            String normalized = status.trim().toLowerCase();
            if (OrderStatus.parse(normalized) == null) {
                throw new BusinessException(CODE_BAD_REQUEST, "invalid_order_status");
            }
            q.eq(SubscriptionOrder::getStatus, normalized);
        }
        if (provider != null && !provider.isBlank()) {
            q.eq(SubscriptionOrder::getProvider, provider.trim().toLowerCase());
        }
        if (workspaceId != null && !workspaceId.isBlank()) {
            q.eq(SubscriptionOrder::getWorkspaceId, workspaceId.trim());
        }
        q.orderByDesc(SubscriptionOrder::getCreatedAt);

        Page<SubscriptionOrder> p = orderMapper.selectPage(
                new Page<>(Math.max(page, 1), Math.min(Math.max(size, 1), 100)), q);
        Page<OrderDtos.OrderView> result = new Page<>(p.getCurrent(), p.getSize(), p.getTotal());
        result.setRecords(p.getRecords().stream().map(this::toView).toList());
        return result;
    }

    /**
     * 按 ID 读订单（管理端 / 内部）。
     */
    public SubscriptionOrder requireOrderById(UUID orderId) {
        SubscriptionOrder order = orderId == null ? null : orderMapper.selectById(orderId);
        if (order == null) {
            throw new BusinessException(CODE_NOT_FOUND, "order_not_found");
        }
        return order;
    }

    /**
     * 按订单号读订单（内部，跨 workspace 校验留给调用方）。
     */
    public SubscriptionOrder requireOrder(String orderNo) {
        if (orderNo == null || orderNo.isBlank()) {
            throw new BusinessException(CODE_BAD_REQUEST, "order_no_required");
        }
        SubscriptionOrder order = orderMapper.selectByOrderNo(orderNo.trim());
        if (order == null) {
            throw new BusinessException(CODE_NOT_FOUND, "order_not_found");
        }
        return order;
    }

    // ══════════════════════════════════════════════════════════════════
    // 写：下单
    // ══════════════════════════════════════════════════════════════════

    /**
     * 创建订单（幂等；10 次/天/工作区；OWNER only）。
     * <p>
     * 订阅订单金额来自套餐价 × 数量；按量充值订单金额由请求指定。
     * </p>
     * <p>
     * <b>P2-1 事务边界</b>：渠道下单是<b>同步 HTTP 远程调用</b>，绝不能被包在数据库事务里
     * （渠道慢/挂起会把连接与行锁持有到 HTTP 超时，高并发下耗尽连接池；
     * 远程失败还会把已落库的订单整个回滚）。因此拆成三段：
     * <ol>
     *   <li><b>事务 1</b>：校验 + 插入 {@code status=pending} 订单并提交；</li>
     *   <li><b>事务外</b>：调用 {@code provider.createPayment(...)}，不持有任何 DB 事务与行锁；</li>
     *   <li><b>事务 2</b>：回写 {@code providerOrderId} / {@code paymentPayload}；
     *       渠道调用失败时在事务 2 内把订单 {@code compareAndSetClosed} 关闭（不留 pending 悬挂单）。</li>
     * </ol>
     * </p>
     */
    public OrderDtos.OrderView createOrder(String workspaceId, Long userId,
                                           OrderDtos.CreateOrderRequest request,
                                           String idempotencyKey) {
        requireOwner(workspaceId, userId);
        rateLimiter.require("workspace", workspaceId + ":order:create",
                LIMIT_CREATE_ORDER_PER_DAY, WINDOW_ONE_DAY);

        String key = normalizeKey(idempotencyKey);
        if (key != null) {
            SubscriptionOrder existing = orderMapper.selectByIdempotencyKey(workspaceId, key);
            if (existing != null) {
                return toView(existing);
            }
        }
        if (request == null) {
            throw new BusinessException(CODE_BAD_REQUEST, "order_request_required");
        }

        String providerName = normalizeProvider(request.getProvider());
        PaymentProvider provider = providerRegistry.require(providerName);

        OrderType orderType = OrderType.parse(request.getOrderType() == null
                ? inferOrderType(request)
                : request.getOrderType());
        if (orderType == null) {
            throw new BusinessException(CODE_BAD_REQUEST, "invalid_order_type");
        }

        SubscriptionPlan plan = null;
        BigDecimal amount;
        String currency = "CNY";
        int quantity = request.getQuantity() == null ? 1 : Math.max(request.getQuantity(), 1);

        if (orderType == OrderType.SUBSCRIPTION) {
            if (request.getPlanCode() == null || request.getPlanCode().isBlank()) {
                throw new BusinessException(CODE_BAD_REQUEST, "plan_code_required");
            }
            plan = planService.requireActivePlanByCode(request.getPlanCode());
            amount = BillingMath.scale(plan.getPrice().multiply(BigDecimal.valueOf(quantity)));
            currency = plan.getCurrency() == null ? "CNY" : plan.getCurrency();
        } else {
            // 按量充值：金额由请求指定，必须 > 0
            if (request.getAmount() == null || request.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
                throw new BusinessException(CODE_BAD_REQUEST, "amount_required");
            }
            amount = BillingMath.scale(request.getAmount());
        }

        OffsetDateTime now = OffsetDateTime.now();
        SubscriptionOrder order = new SubscriptionOrder();
        // UUID 主键必须手动生成（MyBatis-Plus 的 ASSIGN_UUID 只对 String 主键自动赋值）
        order.setId(UUID.randomUUID());
        order.setOrderNo(generateOrderNo());
        order.setWorkspaceId(workspaceId);
        order.setPlanId(plan == null ? null : plan.getId());
        order.setPlanVersion(plan == null ? null : plan.getVersion());
        order.setOrderType(orderType.getValue());
        order.setQuantity(quantity);
        order.setAmount(amount);
        order.setCurrency(currency);
        order.setProvider(providerName);
        order.setStatus(OrderStatus.PENDING.getValue());
        order.setPaymentPayload(Map.of());
        order.setExpiresAt(now.plusMinutes(ORDER_TTL_MINUTES));
        order.setIdempotencyKey(key);
        order.setCreatedAt(now);
        order.setUpdatedAt(now);

        // ── 事务 1：插入 pending 订单并提交 ──
        try {
            newTransaction().executeWithoutResult(status -> orderMapper.insert(order));
        } catch (org.springframework.dao.DuplicateKeyException ex) {
            // 唯一冲突后当前事务已被 PostgreSQL 置为 aborted，
            // 必须在新事务里回读，否则回读 SQL 会抛
            // "current transaction is aborted" -> 500。
            if (key != null) {
                SubscriptionOrder existing = idempotencyReplay.read(
                        () -> orderMapper.selectByIdempotencyKey(workspaceId, key));
                if (existing != null) {
                    return toView(existing);
                }
            }
            throw new BusinessException(CODE_CONFLICT, "order_create_conflict");
        }

        // ── 事务外：调渠道下单（不持有任何 DB 事务与行锁） ──
        PaymentProvider.CreatePaymentResult result;
        try {
            result = provider.createPayment(
                    new PaymentProvider.CreatePaymentRequest(
                            order.getOrderNo(),
                            workspaceId,
                            amount,
                            currency,
                            buildSubject(plan, orderType),
                            plan == null ? "按量充值" : plan.getName(),
                            null,
                            null,
                            Map.of()));
        } catch (RuntimeException ex) {
            // 渠道调用失败 → 事务 2 里关闭订单（不留下 pending 悬挂单）
            closeOrderAfterProviderFailure(order, workspaceId, providerName);
            auditWriter.recordFailure("ORDER_CREATE_FAILED", "SUBSCRIPTION_ORDER",
                    order.getId().toString(),
                    ex instanceof BusinessException be ? be.getCode() : null,
                    "provider_create_payment_failed",
                    Map.of("workspaceId", workspaceId,
                            "orderNo", order.getOrderNo(),
                            "provider", providerName));
            throw ex;
        }

        // ── 事务 2：回写渠道单号与支付参数 ──
        Map<String, Object> payload = new LinkedHashMap<>();
        if (result.payParams() != null) {
            payload.putAll(result.payParams());
        }
        if (result.payUrl() != null) {
            payload.put("payUrl", result.payUrl());
        }
        if (result.codeUrl() != null) {
            payload.put("codeUrl", result.codeUrl());
        }
        try {
            newTransaction().executeWithoutResult(status -> {
                order.setProviderOrderId(result.providerOrderId());
                order.setPaymentPayload(payload);
                order.setUpdatedAt(OffsetDateTime.now());
                orderMapper.updateById(order);
            });
        } catch (RuntimeException ex) {
            // 回写失败：支付参数拿不到，用户无法继续支付 —— 立即关闭订单，
            // 保证「要么订单不存在，要么订单是 closed，绝不停在 pending 且无人清理」。
            closeOrderAfterProviderFailure(order, workspaceId, providerName);
            auditWriter.recordFailure("ORDER_CREATE_FAILED", "SUBSCRIPTION_ORDER",
                    order.getId().toString(),
                    ex instanceof BusinessException be ? be.getCode() : null,
                    "order_provider_payload_persist_failed",
                    Map.of("workspaceId", workspaceId,
                            "orderNo", order.getOrderNo(),
                            "provider", providerName));
            throw ex;
        }

        auditWriter.record("ORDER_CREATED", "SUBSCRIPTION_ORDER", order.getId().toString(),
                Map.of("workspaceId", workspaceId,
                        "orderNo", order.getOrderNo(),
                        "orderType", orderType.getValue(),
                        "amount", amount.toPlainString(),
                        "currency", currency,
                        "provider", providerName));

        log.info("Order created: orderNo={} ws={} type={} amount={} provider={}",
                order.getOrderNo(), workspaceId, orderType.getValue(), amount.toPlainString(), providerName);
        return toView(order);
    }

    // ══════════════════════════════════════════════════════════════════
    // 写：履约
    // ══════════════════════════════════════════════════════════════════

    /**
     * 履约：把已支付订单推进为 {@code fulfilled}。
     * <p>
     * 由队列任务（{@code BILLING_FULFILL_ORDER}）调用，<b>幂等</b>：
     * 只有 {@code paid} 状态能被置为 {@code fulfilled}（SQL 条件保证）。
     * </p>
     */
    @Transactional
    public boolean fulfill(UUID orderId) {
        SubscriptionOrder order = requireOrderById(orderId);
        if (OrderStatus.FULFILLED.getValue().equals(order.getStatus())) {
            return false;
        }
        if (!OrderStatus.PAID.getValue().equals(order.getStatus())) {
            log.warn("Fulfillment skipped, order not in paid state: orderNo={} status={}",
                    order.getOrderNo(), order.getStatus());
            return false;
        }
        int updated = orderMapper.compareAndSetFulfilled(orderId);
        if (updated == 0) {
            return false;
        }
        auditWriter.record("ORDER_FULFILLED", "SUBSCRIPTION_ORDER", orderId.toString(),
                Map.of("workspaceId", order.getWorkspaceId(),
                        "orderNo", order.getOrderNo()));
        log.info("Order fulfilled: orderNo={} ws={}", order.getOrderNo(), order.getWorkspaceId());
        return true;
    }

    /**
     * 把履约任务投递到持久化队列。
     * <p>
     * <b>支付成功但履约失败时订单仍为 {@code paid}</b>，由队列重试 / DLQ 兜底，不丢单。
     * </p>
     */
    public void enqueueFulfillment(SubscriptionOrder order) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("orderId", order.getId().toString());
        payload.put("orderNo", order.getOrderNo());
        payload.put("workspaceId", order.getWorkspaceId());
        payload.put("orderType", order.getOrderType());
        payload.put("planId", order.getPlanId() == null ? null : order.getPlanId().toString());
        try {
            taskQueueProducer.enqueue(TASK_FULFILL_ORDER, order.getId().toString(),
                    toJson(payload), 0);
        } catch (RuntimeException ex) {
            // 投递失败不影响订单状态（仍为 paid），由定时扫描兜底
            log.error("Failed to enqueue fulfillment for order {}: {}",
                    order.getOrderNo(), ex.getMessage());
        }
    }

    // ══════════════════════════════════════════════════════════════════
    // 写：关闭 / 退款
    // ══════════════════════════════════════════════════════════════════

    /**
     * 补偿扫描：把「已付款但未履约」的订单重新投递履约任务。
     * <p>
     * P2-7：早期实现投递失败只记日志，且没有任何扫描 {@code paid} 状态的补偿任务，
     * 订单会永久滞留（用户已付款、订阅未开通）。
     * </p>
     *
     * @param minutes 早于「已付款多少分钟」才纳入补偿（避免与正常履约抢跑）
     * @return 本次补投的订单数
     */
    public int requeueUnfulfilledOrders(int minutes, int limit) {
        OffsetDateTime before = OffsetDateTime.now().minusMinutes(Math.max(minutes, 1));
        List<SubscriptionOrder> candidates = orderMapper.selectUnfulfilledPaidCandidates(before, limit);
        int requeued = 0;
        for (SubscriptionOrder order : candidates) {
            try {
                taskQueueProducer.enqueue(TASK_FULFILL_ORDER, order.getId().toString(),
                        toJson(Map.of("orderId", order.getId().toString(),
                                "orderNo", order.getOrderNo(),
                                "workspaceId", order.getWorkspaceId(),
                                "orderType", order.getOrderType() == null ? "" : order.getOrderType(),
                                "planId", order.getPlanId() == null ? "" : order.getPlanId().toString())),
                        0);
                requeued++;
                log.warn("Re-queued stalled fulfillment for order {} (paid but not fulfilled)",
                        order.getOrderNo());
            } catch (RuntimeException ex) {
                log.error("Failed to re-queue fulfillment for order {}: {}",
                        order.getOrderNo(), ex.getMessage(), ex);
            }
        }
        return requeued;
    }

    /**
     * 关闭超时未支付订单（{@code pending → closed}）。
     */
    @Transactional
    public int closeExpiredOrders(int limit) {
        OffsetDateTime now = OffsetDateTime.now();
        List<SubscriptionOrder> candidates = orderMapper.selectExpiredCandidates(now, limit);
        int closed = 0;
        for (SubscriptionOrder order : candidates) {
            int updated = orderMapper.compareAndSetClosed(order.getId());
            if (updated > 0) {
                closed++;
                log.info("Order closed (expired): orderNo={}", order.getOrderNo());
            }
        }
        return closed;
    }

    /**
     * 管理端退款。
     * <p>
     * 幂等：同一订单重复退款直接返回既有结果。
     * </p>
     */
    @Transactional
    public OrderDtos.RefundResult refund(UUID orderId, OrderDtos.RefundRequest request,
                                         String idempotencyKey) {
        SubscriptionOrder order = requireOrderById(orderId);
        if (request == null || request.getReason() == null || request.getReason().isBlank()) {
            throw new BusinessException(CODE_BAD_REQUEST, "reason_required");
        }
        if (OrderStatus.REFUNDED.getValue().equals(order.getStatus())) {
            // 幂等重放：回读既有退款结果，而不是返回空对象
            // （早期实现返回 status=null / accepted=false，前端无法判断是否已退）。
            OrderDtos.RefundResult replay = new OrderDtos.RefundResult();
            replay.setOrderNo(order.getOrderNo());
            replay.setStatus(OrderStatus.REFUNDED.getValue());
            replay.setAccepted(true);
            return replay;
        }
        if (!OrderStatus.PAID.getValue().equals(order.getStatus())
                && !OrderStatus.FULFILLED.getValue().equals(order.getStatus())) {
            throw new BusinessException(CODE_CONFLICT, "order_not_refundable");
        }

        BigDecimal refundAmount = request.getAmount() == null
                ? order.getAmount()
                : BillingMath.scale(request.getAmount());
        if (refundAmount.compareTo(order.getAmount()) > 0) {
            throw new BusinessException(CODE_BAD_REQUEST, "refund_amount_exceeds_order");
        }

        PaymentProvider provider = providerRegistry.require(order.getProvider());
        PaymentProvider.RefundResult providerResult = provider.refund(
                new PaymentProvider.RefundRequest(
                        order.getOrderNo(),
                        order.getProviderOrderId(),
                        refundAmount,
                        request.getReason(),
                        normalizeKey(idempotencyKey)));

        int updated = orderMapper.compareAndSetRefunded(orderId);
        if (updated == 0) {
            log.info("Refund state conflict (already handled): orderNo={}", order.getOrderNo());
        } else {
            // P1-3：退款必须真正回补业务权益，否则「退款」只是把订单状态改成 refunded：
            //   1) 按量充值订单 -> 余额回补（creditBalance 自带 orderNo 幂等，重复退款不会重复入账）；
            //   2) 订阅订单     -> 取消订阅 + 降级额度（否则用户退了钱还继续享受 PRO 额度）。
            reverseEntitlements(order, refundAmount);
        }

        auditWriter.record("ORDER_REFUNDED", "SUBSCRIPTION_ORDER", orderId.toString(),
                Map.of("workspaceId", order.getWorkspaceId(),
                        "orderNo", order.getOrderNo(),
                        "amount", refundAmount.toPlainString(),
                        "reason", request.getReason(),
                        "providerRefundId", providerResult.providerRefundId() == null
                                ? "" : providerResult.providerRefundId()));

        OrderDtos.RefundResult result = new OrderDtos.RefundResult();
        result.setOrderNo(order.getOrderNo());
        result.setStatus(OrderStatus.REFUNDED.getValue());
        result.setProviderRefundId(providerResult.providerRefundId());
        result.setAccepted(providerResult.accepted());
        return result;
    }

    // ══════════════════════════════════════════════════════════════════
    // private
    // ══════════════════════════════════════════════════════════════════

    /**
     * 新建一个「独立事务」模板（{@code PROPAGATION_REQUIRES_NEW}）。
     * <p>
     * P2-1：{@code createOrder} 需要显式控制事务边界，把「渠道远程调用」夹在两个事务之间，
     * 且保证外层（如果调用方有事务）不会把这两段的提交/回滚语义搅乱。
     * </p>
     */
    private TransactionTemplate newTransaction() {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return template;
    }

    /**
     * 渠道下单失败后的兜底：把订单从 {@code pending} 原子置为 {@code closed}。
     * <p>
     * P2-1：必须在<b>独立事务</b>里执行 —— 渠道失败后外层已经没有任何事务，
     * 如果这一步跟订单插入共用同一个事务，插入会跟着回滚；
     * 如果这一步不在事务里，则失败会留下 {@code pending} 悬挂单。
     * </p>
     * <p>
     * 语义：<b>最终一致</b> —— 订单要么不存在，要么是 {@code closed}，绝不会停在
     * {@code pending} 且无人清理（{@code closeExpiredOrders} 只是兜底，不是唯一保证）。
     * </p>
     */
    private void closeOrderAfterProviderFailure(SubscriptionOrder order, String workspaceId,
                                                String providerName) {
        try {
            newTransaction().executeWithoutResult(status ->
                    orderMapper.compareAndSetClosed(order.getId()));
        } catch (RuntimeException ex) {
            log.error("Failed to close order after provider failure: orderNo={} ws={} provider={} err={}",
                    order.getOrderNo(), workspaceId, providerName, ex.getMessage());
        }
    }

    /**
     * 退款后回补 / 收回业务权益。
     * <p>
     * P1-3：早期实现退款只改订单状态，导致：
     * <ul>
     *   <li>按量充值订单：用户余额不恢复（预付的钱蒸发）；</li>
     *   <li>订阅订单：订阅仍为 active，继续享受额度（收入与权益两头都对不上）。</li>
     * </ul>
     * </p>
     */
    private void reverseEntitlements(SubscriptionOrder order, BigDecimal refundAmount) {
        try {
            if (order.getPlanId() == null) {
                // 按量充值：余额回补。用 "refund:" + orderNo 作为幂等键，
                // 保证同一订单重复退款不会重复入账。
                balanceService.creditBalance(order.getWorkspaceId(), refundAmount,
                        order.getCurrency(), "refund:" + order.getOrderNo(), "order_refund");
                log.info("Refund credited balance: orderNo={} amount={}",
                        order.getOrderNo(), refundAmount);
            } else {
                // 订阅：取消订阅并降级额度（不删数据，只收窄能力）
                subscriptionService.cancelForRefund(order.getWorkspaceId());
                log.info("Refund canceled subscription: orderNo={} ws={}",
                        order.getOrderNo(), order.getWorkspaceId());
            }
        } catch (RuntimeException ex) {
            // 权益回补失败不能让渠道退款回滚（钱已经退给用户了），
            // 但必须留下 ERROR 记录供人工补偿。
            log.error("Failed to reverse entitlements for refunded order {}: {}",
                    order.getOrderNo(), ex.getMessage(), ex);
            auditWriter.recordFailure("ORDER_REFUND_ENTITLEMENT_FAILED", "SUBSCRIPTION_ORDER",
                    order.getId().toString(), null, "entitlement_reversal_failed",
                    Map.of("workspaceId", order.getWorkspaceId(),
                            "orderNo", order.getOrderNo(),
                            "amount", refundAmount.toPlainString()));
        }
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
            throw new BusinessException(CODE_NOT_FOUND, "order_not_found");
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
     * 订单号生成：{@code SO} + 时间戳（毫秒，base36）+ 随机数，VARCHAR(32) 内且全局唯一。
     */
    private String generateOrderNo() {
        String ts = Long.toString(System.currentTimeMillis(), 36);
        String rand = Integer.toString(ThreadLocalRandom.current().nextInt(1_000_000), 36);
        String no = "SO" + ts + rand.toUpperCase();
        return no.length() > 32 ? no.substring(0, 32) : no;
    }

    private static String normalizeProvider(String provider) {
        if (provider == null || provider.isBlank()) {
            return "mock";
        }
        return provider.trim().toLowerCase();
    }

    private static String inferOrderType(OrderDtos.CreateOrderRequest request) {
        return request.getPlanCode() != null && !request.getPlanCode().isBlank()
                ? OrderType.SUBSCRIPTION.getValue()
                : OrderType.USAGE.getValue();
    }

    private static String buildSubject(SubscriptionPlan plan, OrderType type) {
        if (type == OrderType.USAGE) {
            return "XhsAgent 按量充值";
        }
        return plan == null ? "XhsAgent 订阅" : ("XhsAgent " + plan.getName());
    }

    private static String normalizeKey(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String trimmed = raw.trim();
        return trimmed.length() > 128 ? trimmed.substring(0, 128) : trimmed;
    }

    private static String toJson(Map<String, Object> payload) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(payload);
        } catch (Exception ex) {
            // P3-7：早期实现静默返回 "{}"，载荷丢失会让任务处理失败但没有明确原因。
            log.error("Failed to serialize order payload: {}", ex.getMessage(), ex);
            return "{}";
        }
    }

    /**
     * 订单视图转换。
     */
    public OrderDtos.OrderView toView(SubscriptionOrder order) {
        if (order == null) {
            return null;
        }
        OrderDtos.OrderView v = new OrderDtos.OrderView();
        v.setId(order.getId() == null ? null : order.getId().toString());
        v.setOrderNo(order.getOrderNo());
        v.setWorkspaceId(order.getWorkspaceId());
        v.setPlanId(order.getPlanId() == null ? null : order.getPlanId().toString());
        v.setPlanVersion(order.getPlanVersion());
        v.setOrderType(order.getOrderType());
        v.setQuantity(order.getQuantity());
        v.setAmount(order.getAmount());
        v.setCurrency(order.getCurrency());
        v.setProvider(order.getProvider());
        v.setProviderOrderId(order.getProviderOrderId());
        v.setStatus(order.getStatus());
        v.setPaidAt(order.getPaidAt());
        v.setFulfilledAt(order.getFulfilledAt());
        v.setExpiresAt(order.getExpiresAt());
        v.setCreatedAt(order.getCreatedAt());
        v.setPaymentPayload(order.getPaymentPayload());
        Object payUrl = order.getPaymentPayload() == null ? null : order.getPaymentPayload().get("payUrl");
        v.setPayUrl(payUrl == null ? null : String.valueOf(payUrl));
        return v;
    }
}
