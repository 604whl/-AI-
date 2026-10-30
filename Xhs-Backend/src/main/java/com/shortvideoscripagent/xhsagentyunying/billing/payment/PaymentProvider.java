package com.shortvideoscripagent.xhsagentyunying.billing.payment;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/**
 * S4-2 支付渠道统一接口。
 * <p>
 * 覆盖四类动作：下单 / 查单 / 退款 / 回调验签解析。
 * </p>
 * <p>
 * <b>凭据处理约定</b>（全局强制）：
 * <ul>
 *   <li>凭据只从环境变量 / 配置读取，<b>不落库明文</b>；</li>
 *   <li>凭据<b>不进日志</b>、<b>不进异常消息</b>、<b>不进 {@code raw_response} / {@code payment_payload}</b>；</li>
 *   <li>渠道配置缺失时<b>启动不失败</b>，只在调用时抛
 *       {@code PAYMENT_PROVIDER_NOT_CONFIGURED}（503）；</li>
 *   <li>渠道超时 / 失败走<b>指数退避重试</b>，重试耗尽后抛 502 上游错误。</li>
 * </ul>
 */
public interface PaymentProvider {

    /**
     * 渠道标识：{@code mock} / {@code alipay} / {@code wechat}（{@code stripe} 预留）。
     */
    String provider();

    /**
     * 渠道是否已配置完备。未配置时 {@link #createPayment} 等动作一律抛 503。
     */
    boolean configured();

    /**
     * 下单：向渠道创建支付单，返回渠道支付参数（跳转参数 / 二维码 / prefill）。
     *
     * @throws com.shortvideoscripagent.xhsagentyunying.common.exception.BusinessException
     *         503 渠道未配置；502 渠道故障
     */
    CreatePaymentResult createPayment(CreatePaymentRequest request);

    /**
     * 查单：按渠道订单号查询支付状态。
     */
    PaymentQueryResult queryPayment(String providerOrderId);

    /**
     * 退款：发起退款。
     */
    RefundResult refund(RefundRequest request);

    /**
     * 回调验签 + 解析。
     * <p>
     * 校验链：<b>签名校验 → 时间戳窗口 → （由上层做）事件 ID 去重</b>。
     * 验签失败必须抛异常，<b>不得</b>凭订单号直接改状态。
     * </p>
     *
     * @param rawBody 渠道原始报文（form body 或 JSON）
     * @param headers 请求头（渠道签名相关头）
     */
    PaymentCallback verifyCallback(String rawBody, Map<String, String> headers);

    /**
     * 对账用：拉取渠道某日的账单流水。渠道不支持时返回空列表。
     */
    default List<ProviderBill> queryDailyBills(LocalDate day) {
        return List.of();
    }

    // ── 载荷 ─────────────────────────────────────────────────────────────

    /**
     * 下单请求。
     */
    record CreatePaymentRequest(
            String orderNo,
            String workspaceId,
            BigDecimal amount,
            String currency,
            String subject,
            String description,
            String notifyUrl,
            String returnUrl,
            Map<String, String> extra) {
    }

    /**
     * 下单结果。
     *
     * @param payParams 渠道支付参数（前端跳转 / 唤起支付用）；<b>不得包含任何凭据</b>
     */
    record CreatePaymentResult(
            String provider,
            String providerOrderId,
            Map<String, String> payParams,
            String payUrl,
            String codeUrl,
            BigDecimal amount,
            String currency) {
    }

    /**
     * 查单结果。
     *
     * @param status {@code pending} / {@code paid} / {@code closed}
     */
    record PaymentQueryResult(
            String providerOrderId,
            String status,
            BigDecimal amount,
            String currency,
            OffsetDateTime paidAt) {
    }

    /**
     * 退款请求。
     */
    record RefundRequest(
            String orderNo,
            String providerOrderId,
            BigDecimal amount,
            String reason,
            String idempotencyKey) {
    }

    /**
     * 退款结果。
     */
    record RefundResult(
            String providerRefundId,
            String status,
            boolean accepted) {
    }

    /**
     * 验签通过的回调事件。
     *
     * @param eventId 渠道事件 ID（重放去重键）
     * @param paid    是否为「支付成功」事件
     * @param raw     渠道报文摘要（<b>已剔除凭据 / 签名</b>）
     */
    record PaymentCallback(
            String eventId,
            String eventType,
            String providerOrderId,
            String orderNo,
            BigDecimal amount,
            String currency,
            boolean paid,
            OffsetDateTime occurredAt,
            Map<String, Object> raw) {
    }

    /**
     * 渠道账单行（对账用）。
     */
    record ProviderBill(
            String providerOrderId,
            String orderNo,
            BigDecimal amount,
            String currency,
            boolean paid,
            OffsetDateTime paidAt) {
    }
}
