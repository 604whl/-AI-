package com.shortvideoscripagent.xhsagentyunying.billing.payment.provider;

import com.shortvideoscripagent.xhsagentyunying.billing.payment.PaymentErrors;
import com.shortvideoscripagent.xhsagentyunying.billing.payment.PaymentProvider;
import com.shortvideoscripagent.xhsagentyunying.common.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * S4-2 支付宝支付渠道（骨架实现）。
 * <p>
 * <b>配置来源（环境变量，不落库）</b>：
 * <ul>
 *   <li>{@code ALIPAY_APP_ID} —— 应用 ID</li>
 *   <li>{@code ALIPAY_PRIVATE_KEY} —— 应用私钥（PKCS8 PEM）</li>
 *   <li>{@code ALIPAY_PUBLIC_KEY} —— 支付宝公钥（用于验签回调）</li>
 *   <li>{@code ALIPAY_GATEWAY} —— 网关地址，默认 {@code https://openapi.alipay.com/gateway.do}</li>
 * </ul>
 * </p>
 * <p>
 * <b>未配置时</b>：启动正常；调用任一动作抛
 * {@code BusinessException(50301, "PAYMENT_PROVIDER_NOT_CONFIGURED:alipay")}。
 * </p>
 * <p>
 * <b>签名算法</b>（已实现，可直接对接）：
 * <ul>
 *   <li>请求签名：参数按 key 升序拼 {@code k=v&k2=v2}，附加 {@code app_id}，
 *       用 RSA2（SHA256withRSA）私钥签名 → Base64 → {@code sign}</li>
 *   <li>回调验签：支付宝原始参数（<b>剔除 sign / sign_type</b>）按 key 升序拼接，
 *       用<b>支付宝公钥</b>验证 {@code sign}；同时校验 {@code sign_type == RSA2}</li>
 * </ul>
 * </p>
 * <p>
 * <b>凭据保护</b>：私钥 / 公钥只存在于内存与本地变量，
 * <b>不进日志、不进异常消息、不进 {@code raw_response} / {@code payParams}</b>。
 * </p>
 */
@Slf4j
@Component
public class AlipayPaymentProvider implements PaymentProvider {

    public static final String PROVIDER = "alipay";

    private static final String ENV_APP_ID = "ALIPAY_APP_ID";
    private static final String ENV_PRIVATE_KEY = "ALIPAY_PRIVATE_KEY";
    private static final String ENV_PUBLIC_KEY = "ALIPAY_PUBLIC_KEY";
    private static final String ENV_GATEWAY = "ALIPAY_GATEWAY";

    private static final String DEFAULT_GATEWAY = "https://openapi.alipay.com/gateway.do";
    private static final String SIGN_TYPE = "RSA2";
    private static final String TRADE_STATUS_SUCCESS = "TRADE_SUCCESS";

    @Override
    public String provider() {
        return PROVIDER;
    }

    @Override
    public boolean configured() {
        return PaymentCredentials.allPresent(ENV_APP_ID, ENV_PRIVATE_KEY, ENV_PUBLIC_KEY);
    }

    @Override
    public CreatePaymentResult createPayment(CreatePaymentRequest request) {
        requireConfigured();
        if (request == null || request.orderNo() == null || request.orderNo().isBlank()) {
            throw new BusinessException(PaymentErrors.ORDER_INVALID, "order_no_required");
        }
        BigDecimal amount = request.amount() == null ? BigDecimal.ZERO : request.amount();
        String currency = request.currency() == null ? "CNY" : request.currency();

        // 支付宝金额单位是「元」，这里统一保留 2 位（支付宝不支持 4 位小数）
        String totalAmount = amount.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString();

        Map<String, Object> bizContent = new LinkedHashMap<>();
        bizContent.put("out_trade_no", request.orderNo());
        bizContent.put("total_amount", totalAmount);
        bizContent.put("subject", request.subject() == null ? "XhsAgent 订阅" : request.subject());
        bizContent.put("product_code", "FAST_INSTANT_TRADE_PAY");

        Map<String, String> params = new LinkedHashMap<>();
        params.put("app_id", PaymentCredentials.env(ENV_APP_ID));
        params.put("method", "alipay.trade.page.pay");
        params.put("format", "JSON");
        params.put("charset", "utf-8");
        params.put("sign_type", SIGN_TYPE);
        params.put("timestamp", java.time.LocalDateTime.now()
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        params.put("version", "1.0");
        params.put("notify_url", request.notifyUrl() == null ? "" : request.notifyUrl());
        params.put("return_url", request.returnUrl() == null ? "" : request.returnUrl());
        params.put("biz_content", toJson(bizContent));

        // 请求签名（私钥，仅内存使用）
        params.put("sign", signRequest(params));

        String gateway = PaymentCredentials.env(ENV_GATEWAY);
        if (gateway == null) {
            gateway = DEFAULT_GATEWAY;
        }
        String payUrl = gateway + "?" + toFormQuery(params);

        Map<String, String> payParams = new LinkedHashMap<>();
        payParams.put("provider", PROVIDER);
        payParams.put("out_trade_no", request.orderNo());
        payParams.put("amount", totalAmount);
        payParams.put("currency", currency);
        payParams.put("sign_type", SIGN_TYPE);

        // provider_order_id 支付宝在创建后才有 trade_no，下单阶段用 out_trade_no 占位
        return new CreatePaymentResult(PROVIDER, request.orderNo(), payParams, payUrl,
                null, amount, currency);
    }

    @Override
    public PaymentQueryResult queryPayment(String providerOrderId) {
        requireConfigured();
        if (providerOrderId == null || providerOrderId.isBlank()) {
            throw new BusinessException(PaymentErrors.ORDER_INVALID, "provider_order_id_required");
        }
        // 骨架：真实实现需调用 alipay.trade.query 并验签响应
        throw new BusinessException(PaymentErrors.PROVIDER_ERROR,
                "alipay_query_not_implemented");
    }

    @Override
    public RefundResult refund(RefundRequest request) {
        requireConfigured();
        if (request == null || request.providerOrderId() == null || request.providerOrderId().isBlank()) {
            throw new BusinessException(PaymentErrors.ORDER_INVALID, "provider_order_id_required");
        }
        // 骨架：真实实现需调用 alipay.trade.refund
        throw new BusinessException(PaymentErrors.PROVIDER_ERROR,
                "alipay_refund_not_implemented");
    }

    @Override
    public PaymentCallback verifyCallback(String rawBody, Map<String, String> headers) {
        requireConfigured();
        Map<String, String> params = parseForm(rawBody);

        String sign = params.get("sign");
        String signType = params.get("sign_type");
        if (sign == null || sign.isBlank()) {
            throw new BusinessException(PaymentErrors.SIGNATURE_INVALID, "signature_missing");
        }
        if (signType != null && !SIGN_TYPE.equalsIgnoreCase(signType)) {
            throw new BusinessException(PaymentErrors.SIGNATURE_INVALID, "unsupported_sign_type");
        }

        // ① 验签（剔除 sign / sign_type / 空值）
        Map<String, String> verifyContent = new LinkedHashMap<>(params);
        verifyContent.remove("sign");
        verifyContent.remove("sign_type");
        verifyContent.values().removeIf(v -> v == null || v.isBlank());

        if (!verifySignature(verifyContent, sign)) {
            throw new BusinessException(PaymentErrors.SIGNATURE_INVALID, "signature_invalid");
        }

        // ② 时间戳窗口（notify_time / gmt_payment，容忍时区）
        OffsetDateTime occurredAt = parseAlipayTime(params.get("gmt_payment"));
        if (occurredAt == null) {
            occurredAt = parseAlipayTime(params.get("notify_time"));
        }
        if (occurredAt == null) {
            occurredAt = OffsetDateTime.now();
        }
        long diffSeconds = Math.abs(java.time.Duration.between(occurredAt, OffsetDateTime.now()).getSeconds());
        if (diffSeconds > 5 * 60) {
            throw new BusinessException(PaymentErrors.TIMESTAMP_OUT_OF_WINDOW,
                    "timestamp_out_of_window");
        }

        // ③ 解析
        String eventId = params.get("trade_no");
        String orderNo = params.get("out_trade_no");
        String tradeStatus = params.get("trade_status");
        String totalAmount = params.get("total_amount");
        if (eventId == null || eventId.isBlank()) {
            throw new BusinessException(PaymentErrors.CALLBACK_MALFORMED, "trade_no_required");
        }
        if (orderNo == null || orderNo.isBlank()) {
            throw new BusinessException(PaymentErrors.CALLBACK_MALFORMED, "out_trade_no_required");
        }
        if (totalAmount == null || totalAmount.isBlank()) {
            throw new BusinessException(PaymentErrors.CALLBACK_MALFORMED, "total_amount_required");
        }
        BigDecimal amount;
        try {
            amount = new BigDecimal(totalAmount);
        } catch (NumberFormatException ex) {
            throw new BusinessException(PaymentErrors.CALLBACK_MALFORMED, "invalid_total_amount");
        }

        boolean paid = TRADE_STATUS_SUCCESS.equalsIgnoreCase(tradeStatus);

        // raw 只保留业务字段，绝不包含 sign / 密钥
        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("trade_no", eventId);
        raw.put("out_trade_no", orderNo);
        raw.put("total_amount", amount.toPlainString());
        raw.put("trade_status", tradeStatus);
        raw.put("app_id", params.get("app_id"));
        raw.put("occurred_at", occurredAt.toString());

        return new PaymentCallback(eventId, paid ? "payment.succeeded" : "payment.pending",
                eventId, orderNo, amount, params.get("currency"), paid, occurredAt, raw);
    }

    @Override
    public List<ProviderBill> queryDailyBills(LocalDate day) {
        requireConfigured();
        // 骨架：真实实现需调用 alipay.trade.downloadurl.query / 对账单下载
        return new ArrayList<>();
    }

    // ── 内部 ─────────────────────────────────────────────────────────────

    private void requireConfigured() {
        if (!configured()) {
            log.warn("Alipay provider not configured, missing env keys: {}",
                    PaymentCredentials.missing(ENV_APP_ID, ENV_PRIVATE_KEY, ENV_PUBLIC_KEY));
            throw PaymentErrors.notConfigured(PROVIDER);
        }
    }

    /**
     * 请求签名：参数按 key 升序 → {@code k=v&k2=v2} → RSA2 私钥签名 → Base64。
     */
    private String signRequest(Map<String, String> params) {
        String content = buildSignContent(params);
        try {
            java.security.PrivateKey key = PemKeyUtils.parsePrivateKey(
                    PaymentCredentials.env(ENV_PRIVATE_KEY));
            java.security.Signature signature = java.security.Signature.getInstance("SHA256withRSA");
            signature.initSign(key);
            signature.update(content.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(signature.sign());
        } catch (Exception ex) {
            throw new BusinessException(PaymentErrors.PROVIDER_ERROR, "alipay_sign_failed");
        }
    }

    /**
     * 回调验签：支付宝公钥 → RSA2 验签。
     */
    private boolean verifySignature(Map<String, String> params, String sign) {
        try {
            java.security.PublicKey key = PemKeyUtils.parsePublicKey(
                    PaymentCredentials.env(ENV_PUBLIC_KEY));
            java.security.Signature signature = java.security.Signature.getInstance("SHA256withRSA");
            signature.initVerify(key);
            signature.update(buildSignContent(params).getBytes(StandardCharsets.UTF_8));
            return signature.verify(Base64.getDecoder().decode(sign));
        } catch (Exception ex) {
            log.warn("Alipay signature verify error: {}", ex.getClass().getSimpleName());
            return false;
        }
    }

    /**
     * 支付宝签名原文：key 升序，剔除空值，{@code k=v&k2=v2}。
     */
    private static String buildSignContent(Map<String, String> params) {
        Map<String, String> filtered = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : params.entrySet()) {
            if (e.getKey() == null || e.getValue() == null || e.getValue().isBlank()) {
                continue;
            }
            if ("sign".equals(e.getKey()) || "sign_type".equals(e.getKey())) {
                continue;
            }
            filtered.put(e.getKey(), e.getValue());
        }
        List<String> keys = new ArrayList<>(filtered.keySet());
        java.util.Collections.sort(keys);
        StringBuilder sb = new StringBuilder();
        for (String key : keys) {
            if (sb.length() > 0) {
                sb.append('&');
            }
            sb.append(key).append('=').append(filtered.get(key));
        }
        return sb.toString();
    }

    private static Map<String, String> parseForm(String rawBody) {
        if (rawBody == null || rawBody.isBlank()) {
            throw new BusinessException(PaymentErrors.CALLBACK_MALFORMED, "empty_body");
        }
        Map<String, String> result = new LinkedHashMap<>();
        for (String pair : rawBody.trim().split("&")) {
            if (pair.isBlank()) {
                continue;
            }
            int idx = pair.indexOf('=');
            if (idx < 0) {
                continue;
            }
            try {
                result.put(java.net.URLDecoder.decode(pair.substring(0, idx), StandardCharsets.UTF_8),
                        java.net.URLDecoder.decode(pair.substring(idx + 1), StandardCharsets.UTF_8));
            } catch (Exception ex) {
                result.put(pair.substring(0, idx), pair.substring(idx + 1));
            }
        }
        return result;
    }

    private static OffsetDateTime parseAlipayTime(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return OffsetDateTime.parse(value,
                    java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        } catch (Exception ex) {
            return null;
        }
    }

    private static String toFormQuery(Map<String, String> params) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> e : params.entrySet()) {
            if (sb.length() > 0) {
                sb.append('&');
            }
            try {
                sb.append(java.net.URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8))
                        .append('=')
                        .append(java.net.URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8));
            } catch (Exception ex) {
                sb.append(e.getKey()).append('=').append(e.getValue());
            }
        }
        return sb.toString();
    }

    private static String toJson(Map<String, Object> map) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(map);
        } catch (Exception ex) {
            throw new BusinessException(PaymentErrors.PROVIDER_ERROR, "biz_content_serialize_failed");
        }
    }
}
