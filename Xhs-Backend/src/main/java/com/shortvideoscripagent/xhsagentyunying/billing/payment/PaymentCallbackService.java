package com.shortvideoscripagent.xhsagentyunying.billing.payment;

import com.shortvideoscripagent.xhsagentyunying.billing.order.OrderService;
import com.shortvideoscripagent.xhsagentyunying.billing.order.domain.entity.PaymentEvent;
import com.shortvideoscripagent.xhsagentyunying.billing.order.domain.entity.SubscriptionOrder;
import com.shortvideoscripagent.xhsagentyunying.billing.order.domain.mapper.PaymentEventMapper;
import com.shortvideoscripagent.xhsagentyunying.billing.order.domain.mapper.SubscriptionOrderMapper;
import com.shortvideoscripagent.xhsagentyunying.common.exception.BusinessException;
import com.shortvideoscripagent.xhsagentyunying.dto.billing.order.OrderDtos;
import com.shortvideoscripagent.xhsagentyunying.infra.audit.AuditWriter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * S4-2 支付回调服务。
 * <p>
 * <b>处理链（顺序不可颠倒）</b>：
 * <ol>
 *   <li><b>验签</b>（{@link PaymentProvider#verifyCallback}）——失败一律拒绝并记审计，
 *       <b>绝不凭订单号直接改状态</b>；</li>
 *   <li><b>事件 ID 去重</b>（{@code app.payment_event} 的 {@code (provider, event_id)} 唯一索引）
 *       ——同一渠道事件只处理一次（重放保护）；</li>
 *   <li><b>金额校验</b>——回调金额必须等于订单金额，否则拒付并告警；</li>
 *   <li><b>入账</b>（{@code pending → paid}，SQL 条件保证幂等）；</li>
 *   <li><b>投递履约任务</b>——失败时订单仍为 {@code paid}，队列重试 / DLQ 兜底，不丢单。</li>
 * </ol>
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentCallbackService {

    private final PaymentProviderRegistry providerRegistry;
    private final PaymentEventMapper paymentEventMapper;
    private final SubscriptionOrderMapper orderMapper;
    private final OrderService orderService;
    private final AuditWriter auditWriter;
    private final org.springframework.transaction.PlatformTransactionManager transactionManager;

    /**
     * 在<b>独立事务</b>里标记支付事件为 rejected。
     * <p>
     * P2-6：{@code handleCallback} 整体带 {@code @Transactional}，而「拒绝」的三条路径
     * （订单不存在 / 金额不符 / 渠道不符）都是「先写 result='rejected'，再抛
     * {@link BusinessException}」→ 抛异常导致整个事务回滚，
     * {@code app.payment_event} 里那条 rejected 记录<b>并不存在</b>，
     * 运营无法做风控复盘。
     * <p>
     * 这里用 {@code PROPAGATION_REQUIRES_NEW} 单独提交事件状态，
     * 外层事务的回滚不会带走它。
     * </p>
     */
    private void markEventRejected(PaymentEvent event, String reason) {
        event.setResult("rejected");
        event.setReason(reason);
        try {
            org.springframework.transaction.support.TransactionTemplate requiresNew =
                    new org.springframework.transaction.support.TransactionTemplate(transactionManager);
            requiresNew.setPropagationBehavior(
                    org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);
            requiresNew.executeWithoutResult(tx -> paymentEventMapper.updateById(event));
        } catch (RuntimeException ex) {
            log.error("Failed to persist rejected payment event {}: {}",
                    event.getId(), ex.getMessage(), ex);
        }
    }

    /**
     * 处理渠道回调。
     *
     * @param provider 渠道名
     * @param rawBody  渠道原始报文
     * @param headers  请求头
     */
    @Transactional
    public OrderDtos.CallbackResult handleCallback(String provider, String rawBody,
                                                    Map<String, String> headers) {
        PaymentProvider paymentProvider = providerRegistry.require(provider);
        String providerName = paymentProvider.provider();

        // ── ① 验签（失败 → 审计 + 拒绝） ──
        PaymentProvider.PaymentCallback callback;
        try {
            callback = paymentProvider.verifyCallback(rawBody, headers);
        } catch (BusinessException ex) {
            auditWriter.recordFailure("PAYMENT_CALLBACK_REJECTED", "PAYMENT_EVENT",
                    providerName, ex.getCode(), ex.getMessage(),
                    Map.of("provider", providerName,
                            "reason", ex.getMessage() == null ? "unknown" : ex.getMessage()));
            log.warn("Payment callback rejected (provider={}, code={})",
                    providerName, ex.getCode());
            throw ex;
        } catch (RuntimeException ex) {
            auditWriter.recordFailure("PAYMENT_CALLBACK_REJECTED", "PAYMENT_EVENT",
                    providerName, 40000, "callback_verify_failed",
                    Map.of("provider", providerName));
            log.warn("Payment callback verify error (provider={}): {}",
                    providerName, ex.getClass().getSimpleName());
            throw new BusinessException(PaymentErrors.CALLBACK_MALFORMED, "callback_verify_failed");
        }

        // ── ② 事件 ID 去重（重放保护） ──
        if (callback.eventId() == null || callback.eventId().isBlank()) {
            throw new BusinessException(PaymentErrors.CALLBACK_MALFORMED, "event_id_required");
        }
        PaymentEvent existing = paymentEventMapper.selectByProviderEvent(providerName, callback.eventId());
        if (existing != null) {
            log.info("Duplicate payment event ignored: provider={} eventId={}",
                    providerName, callback.eventId());
            return buildResult(providerName, callback, "duplicate", false, false);
        }

        PaymentEvent event = new PaymentEvent();
        // UUID 主键必须手动生成（MyBatis-Plus 的 ASSIGN_UUID 只对 String 主键自动赋值）
        event.setId(UUID.randomUUID());
        event.setProvider(providerName);
        event.setEventId(callback.eventId());
        event.setOrderNo(callback.orderNo());
        event.setEventType(callback.eventType());
        event.setAmount(callback.amount());
        event.setCurrency(callback.currency());
        event.setOccurredAt(callback.occurredAt());
        event.setReceivedAt(OffsetDateTime.now());
        event.setProcessedAt(OffsetDateTime.now());
        event.setResult("processed");
        event.setReason(null);
        try {
            paymentEventMapper.insert(event);
        } catch (DuplicateKeyException ex) {
            // 并发重复投递：唯一索引兜底
            log.info("Concurrent duplicate payment event ignored: provider={} eventId={}",
                    providerName, callback.eventId());
            return buildResult(providerName, callback, "duplicate", false, false);
        }

        // 非「支付成功」事件：记录即可，不入账
        if (!callback.paid()) {
            event.setResult("processed");
            paymentEventMapper.updateById(event);
            log.info("Non-success payment event recorded: provider={} eventId={} type={}",
                    providerName, callback.eventId(), callback.eventType());
            return buildResult(providerName, callback, "processed", false, false);
        }

        // ── ③ 找订单（绝不凭订单号直接改状态，必须经过验签 + 金额校验） ──
        SubscriptionOrder order = orderMapper.selectByOrderNo(callback.orderNo());
        if (order == null) {
            markEventRejected(event, "order_not_found");
            auditWriter.recordFailure("PAYMENT_CALLBACK_REJECTED", "PAYMENT_EVENT",
                    event.getId().toString(), PaymentErrors.ORDER_NOT_FOUND, "order_not_found",
                    Map.of("provider", providerName, "orderNo", callback.orderNo()));
            throw new BusinessException(PaymentErrors.ORDER_NOT_FOUND, "order_not_found");
        }

        // ── ④ 金额校验（必须等于订单金额） ──
        if (!amountEquals(callback.amount(), order.getAmount())) {
            markEventRejected(event, "amount_mismatch");
            auditWriter.recordFailure("PAYMENT_CALLBACK_REJECTED", "SUBSCRIPTION_ORDER",
                    order.getId().toString(), PaymentErrors.AMOUNT_MISMATCH, "amount_mismatch",
                    Map.of("provider", providerName,
                            "orderNo", order.getOrderNo(),
                            "orderAmount", order.getAmount().toPlainString(),
                            "callbackAmount", callback.amount() == null
                                    ? "" : callback.amount().toPlainString()));
            log.error("Payment amount mismatch: orderNo={} order={} callback={}",
                    order.getOrderNo(), order.getAmount().toPlainString(),
                    callback.amount() == null ? "null" : callback.amount().toPlainString());
            throw new BusinessException(PaymentErrors.AMOUNT_MISMATCH, "amount_mismatch");
        }

        // 渠道名必须与订单一致（防跨渠道串单）
        if (!providerName.equalsIgnoreCase(order.getProvider())) {
            markEventRejected(event, "provider_mismatch");
            auditWriter.recordFailure("PAYMENT_CALLBACK_REJECTED", "SUBSCRIPTION_ORDER",
                    order.getId().toString(), PaymentErrors.ORDER_STATE_CONFLICT, "provider_mismatch",
                    Map.of("provider", providerName, "orderProvider", order.getProvider()));
            throw new BusinessException(PaymentErrors.ORDER_STATE_CONFLICT, "provider_mismatch");
        }

        // ── ⑤ 入账（pending → paid，SQL 条件保证幂等） ──
        OffsetDateTime paidAt = callback.occurredAt() == null ? OffsetDateTime.now() : callback.occurredAt();
        int updated = orderMapper.compareAndSetPaid(order.getId(), paidAt, callback.providerOrderId());
        if (updated == 0) {
            // 已入账（重复回调 / 已处理）→ 幂等跳过，但仍可补投履约
            SubscriptionOrder current = orderMapper.selectById(order.getId());
            log.info("Order already paid (idempotent): orderNo={} status={}",
                    order.getOrderNo(), current == null ? "unknown" : current.getStatus());
            boolean enqueued = enqueueFulfillment(current);
            return buildResult(providerName, callback, "processed", true, enqueued);
        }

        SubscriptionOrder paid = orderMapper.selectById(order.getId());
        auditWriter.record("PAYMENT_SUCCEEDED", "SUBSCRIPTION_ORDER", order.getId().toString(),
                Map.of("provider", providerName,
                        "orderNo", order.getOrderNo(),
                        "workspaceId", order.getWorkspaceId(),
                        "amount", order.getAmount().toPlainString(),
                        "eventId", callback.eventId()));

        // ── ⑥ 投递履约任务（失败不影响 paid 状态） ──
        boolean enqueued = enqueueFulfillment(paid);
        log.info("Payment credited: orderNo={} provider={} amount={} fulfillmentEnqueued={}",
                order.getOrderNo(), providerName, order.getAmount().toPlainString(), enqueued);

        return buildResult(providerName, callback, "processed", true, enqueued);
    }

    /**
     * 金额比较：必须用 {@code compareTo}（scale 不同 {@code equals} 会误判）。
     */
    static boolean amountEquals(BigDecimal a, BigDecimal b) {
        if (a == null || b == null) {
            return false;
        }
        return a.compareTo(b) == 0;
    }

    private boolean enqueueFulfillment(SubscriptionOrder order) {
        if (order == null) {
            return false;
        }
        try {
            orderService.enqueueFulfillment(order);
            return true;
        } catch (RuntimeException ex) {
            log.error("Failed to enqueue fulfillment for order {}: {}",
                    order.getOrderNo(), ex.getMessage());
            return false;
        }
    }

    private OrderDtos.CallbackResult buildResult(String provider, PaymentProvider.PaymentCallback callback,
                                                  String result, boolean credited, boolean enqueued) {
        OrderDtos.CallbackResult r = new OrderDtos.CallbackResult();
        r.setProvider(provider);
        r.setEventId(callback.eventId());
        r.setOrderNo(callback.orderNo());
        r.setResult(result);
        r.setCredited(credited);
        r.setFulfillmentEnqueued(enqueued);
        return r;
    }

    /**
     * 构造审计元数据（不含任何凭据）。
     */
    static Map<String, Object> safeMeta(Map<String, Object> meta) {
        return meta == null ? new LinkedHashMap<>() : meta;
    }
}
