package com.shortvideoscripagent.xhsagentyunying.billing.payment.provider;

import com.shortvideoscripagent.xhsagentyunying.billing.payment.PaymentErrors;
import com.shortvideoscripagent.xhsagentyunying.billing.payment.PaymentProvider;
import com.shortvideoscripagent.xhsagentyunying.common.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * S4-2 Mock 支付渠道。
 * <p>
 * 用途：本地 / 测试环境跑通「下单 → 回调验签 → 履约」完整链路，
 * 也用于验证 {@link PaymentProvider} 契约。
 * </p>
 * <p>
 * <b>验签算法</b>：{@code sign = hex(HMAC_SHA256(secret, eventId + "|" + orderNo + "|" + amount))}，
 * 密钥来自环境变量 {@code PAYMENT_MOCK_SECRET}（<b>必须显式配置，无默认值</b>；未配置时一律拒绝回调）。
 * 回调请求头 {@code X-Mock-Signature} 携带签名；缺失或错误 → 验签失败（40012）。
 * </p>
 * <p>
 * <b>时间戳窗口</b>：请求头 {@code X-Mock-Timestamp}（毫秒），与服务器时间偏差超过
 * {@value #TIMESTAMP_WINDOW_SECONDS} 秒 → 拒绝（40013）。
 * </p>
 * <p>
 * Mock 渠道状态存在内存里（进程内），重启即丢；生产环境不应启用。
 * </p>
 */
@Slf4j
@Component
@Profile("!prod")
public class MockPaymentProvider implements PaymentProvider {

    public static final String PROVIDER = "mock";

    /** 签名请求头 */
    public static final String HEADER_SIGNATURE = "X-Mock-Signature";
    /** 时间戳请求头（毫秒） */
    public static final String HEADER_TIMESTAMP = "X-Mock-Timestamp";

    /** 时间戳窗口（秒） */
    public static final int TIMESTAMP_WINDOW_SECONDS = 300;

    /**
     * 注意：默认密钥已移除。任何可猜的默认密钥都会让人伪造回调白嫖订单，
     * 必须通过环境变量 {@code PAYMENT_MOCK_SECRET} 显式配置（见 {@link #secret()}）。
     */

    /** 渠道订单状态：orderNo -> 支付记录 */
    private final Map<String, MockPayment> payments = new ConcurrentHashMap<>();

    @Override
    public String provider() {
        return PROVIDER;
    }

    @Override
    public boolean configured() {
        // 必须显式配置 PAYMENT_MOCK_SECRET 才算「已配置」，
        // 否则该渠道无法验签，任何回调都会被拒绝。
        return secret() != null;
    }

    @Override
    public CreatePaymentResult createPayment(CreatePaymentRequest request) {
        if (request == null || request.orderNo() == null || request.orderNo().isBlank()) {
            throw new BusinessException(PaymentErrors.ORDER_INVALID, "order_no_required");
        }
        BigDecimal amount = request.amount() == null ? BigDecimal.ZERO : request.amount();
        MockPayment payment = new MockPayment(
                request.orderNo(),
                amount,
                request.currency() == null ? "CNY" : request.currency(),
                OffsetDateTime.now(),
                "pending");
        MockPayment previous = payments.putIfAbsent(request.orderNo(), payment);
        if (previous != null) {
            payment = previous;
        }

        Map<String, String> payParams = new LinkedHashMap<>();
        payParams.put("provider", PROVIDER);
        payParams.put("out_trade_no", request.orderNo());
        payParams.put("amount", amount.toPlainString());
        payParams.put("currency", payment.currency());

        return new CreatePaymentResult(PROVIDER, payment.providerOrderId(), payParams,
                null, null, amount, payment.currency());
    }

    @Override
    public PaymentQueryResult queryPayment(String providerOrderId) {
        if (providerOrderId == null || providerOrderId.isBlank()) {
            throw new BusinessException(PaymentErrors.ORDER_INVALID, "provider_order_id_required");
        }
        for (MockPayment payment : payments.values()) {
            if (providerOrderId.equals(payment.providerOrderId())
                    || providerOrderId.equals(payment.orderNo())) {
                return new PaymentQueryResult(payment.providerOrderId(), payment.status(),
                        payment.amount(), payment.currency(), payment.paidAt());
            }
        }
        return new PaymentQueryResult(providerOrderId, "pending", null, null, null);
    }

    @Override
    public RefundResult refund(RefundRequest request) {
        if (request == null || request.providerOrderId() == null || request.providerOrderId().isBlank()) {
            throw new BusinessException(PaymentErrors.ORDER_INVALID, "provider_order_id_required");
        }
        MockPayment payment = findByProviderOrderId(request.providerOrderId());
        if (payment == null) {
            throw new BusinessException(PaymentErrors.ORDER_NOT_FOUND, "order_not_found");
        }
        if ("refunded".equals(payment.status())) {
            // 幂等：重复退款返回同一结果
            return new RefundResult(payment.providerOrderId() + "-RF", "refunded", true);
        }
        payment.status = "refunded";
        return new RefundResult(payment.providerOrderId() + "-RF", "refunded", true);
    }

    @Override
    public PaymentCallback verifyCallback(String rawBody, Map<String, String> headers) {
        Map<String, Object> body = parseBody(rawBody);
        String eventId = str(body.get("event_id"));
        String orderNo = str(body.get("out_trade_no"));
        String amountStr = str(body.get("total_amount"));
        String currency = str(body.get("currency"));
        String tradeStatus = str(body.get("trade_status"));
        Long timestamp = headerLong(headers, HEADER_TIMESTAMP);

        if (eventId == null || eventId.isBlank()) {
            throw new BusinessException(PaymentErrors.CALLBACK_MALFORMED, "event_id_required");
        }
        if (orderNo == null || orderNo.isBlank()) {
            throw new BusinessException(PaymentErrors.CALLBACK_MALFORMED, "out_trade_no_required");
        }
        if (amountStr == null || amountStr.isBlank()) {
            throw new BusinessException(PaymentErrors.CALLBACK_MALFORMED, "total_amount_required");
        }

        // ① 时间戳窗口
        verifyTimestampWindow(timestamp);

        // ② 签名校验
        String signature = header(headers, HEADER_SIGNATURE);
        if (signature == null || signature.isBlank()) {
            throw new BusinessException(PaymentErrors.SIGNATURE_INVALID, "signature_missing");
        }
        BigDecimal amount;
        try {
            amount = new BigDecimal(amountStr);
        } catch (NumberFormatException ex) {
            throw new BusinessException(PaymentErrors.CALLBACK_MALFORMED, "invalid_total_amount");
        }
        String expected = sign(eventId, orderNo, amount);
        if (expected == null) {
            // 未配置 PAYMENT_MOCK_SECRET：无法验签，一律拒绝（禁止使用默认密钥兜底）
            log.warn("Mock payment provider secret not configured (PAYMENT_MOCK_SECRET); rejecting callback");
            throw new BusinessException(PaymentErrors.PROVIDER_NOT_CONFIGURED, "mock_secret_not_configured");
        }
        if (!constantTimeEquals(expected, signature)) {
            throw new BusinessException(PaymentErrors.SIGNATURE_INVALID, "signature_invalid");
        }

        // ③ 解析事件（验签通过后才允许读金额）
        boolean paid = "TRADE_SUCCESS".equalsIgnoreCase(tradeStatus) || "SUCCESS".equalsIgnoreCase(tradeStatus);
        OffsetDateTime occurredAt = timestamp == null
                ? OffsetDateTime.now()
                : OffsetDateTime.ofInstant(java.time.Instant.ofEpochMilli(timestamp), java.time.ZoneOffset.UTC);

        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("event_id", eventId);
        raw.put("out_trade_no", orderNo);
        raw.put("total_amount", amount.toPlainString());
        raw.put("currency", currency);
        raw.put("trade_status", tradeStatus);
        raw.put("occurred_at", occurredAt.toString());

        String providerOrderId = str(body.get("trade_no"));
        if (providerOrderId == null) {
            providerOrderId = "MOCK-" + eventId;
        }
        // 回写渠道内部状态（供 queryPayment 使用）
        MockPayment payment = findByOrderNo(orderNo);
        if (payment == null) {
            payment = new MockPayment(orderNo, amount, currency == null ? "CNY" : currency,
                    occurredAt, paid ? "paid" : "pending");
            payments.put(orderNo, payment);
        }
        if (paid) {
            payment.status = "paid";
            payment.paidAt = occurredAt;
            payment.amount = amount;
            if (payment.providerOrderId == null || payment.providerOrderId.startsWith("MOCK-")) {
                payment.providerOrderId = providerOrderId;
            }
        }

        return new PaymentCallback(eventId, paid ? "payment.succeeded" : "payment.pending",
                payment.providerOrderId, orderNo, amount, currency, paid, occurredAt, raw);
    }

    @Override
    public List<ProviderBill> queryDailyBills(LocalDate day) {
        List<ProviderBill> bills = new ArrayList<>();
        for (MockPayment payment : payments.values()) {
            if (payment.paidAt() == null) {
                continue;
            }
            if (day != null && !payment.paidAt().toLocalDate().equals(day)) {
                continue;
            }
            bills.add(new ProviderBill(payment.providerOrderId(), payment.orderNo(),
                    payment.amount(), payment.currency(), true, payment.paidAt()));
        }
        return bills;
    }

    // ── 辅助 ─────────────────────────────────────────────────────────────

    /**
     * 计算 Mock 渠道签名（供测试 / 本地联调构造回调）。
     * <p>
     * 未配置 {@code PAYMENT_MOCK_SECRET} 时返回 {@code null}（无法构造合法签名）。
     * </p>
     */
    public static String sign(String eventId, String orderNo, BigDecimal amount) {
        String key = secret();
        if (key == null) {
            return null;
        }
        String payload = eventId + "|" + orderNo + "|" + (amount == null ? "0" : amount.toPlainString());
        return hmacSha256(key, payload);
    }

    /**
     * 当前 Mock 签名密钥（供本地联调构造回调用）。
     * <p>
     * <b>安全要求</b>：必须显式设置 {@code PAYMENT_MOCK_SECRET}。
     * 若未设置，返回 {@code null}，此时 {@link #verifyCallback} 一律拒绝，
     * <b>绝不回退到任何公开可猜的默认密钥</b>——否则任何人都能伪造回调白嫖订单。
     * </p>
     */
    public static String secret() {
        String env = System.getenv("PAYMENT_MOCK_SECRET");
        return env == null || env.isBlank() ? null : env;
    }

    private void verifyTimestampWindow(Long timestamp) {
        if (timestamp == null) {
            throw new BusinessException(PaymentErrors.TIMESTAMP_OUT_OF_WINDOW, "timestamp_missing");
        }
        long now = System.currentTimeMillis();
        long diff = Math.abs(now - timestamp);
        if (diff > TIMESTAMP_WINDOW_SECONDS * 1000L) {
            throw new BusinessException(PaymentErrors.TIMESTAMP_OUT_OF_WINDOW,
                    "timestamp_out_of_window");
        }
    }

    private MockPayment findByOrderNo(String orderNo) {
        return payments.get(orderNo);
    }

    private MockPayment findByProviderOrderId(String providerOrderId) {
        for (MockPayment payment : payments.values()) {
            if (providerOrderId.equals(payment.providerOrderId())) {
                return payment;
            }
        }
        return null;
    }

    private static Map<String, Object> parseBody(String rawBody) {
        if (rawBody == null || rawBody.isBlank()) {
            throw new BusinessException(PaymentErrors.CALLBACK_MALFORMED, "empty_body");
        }
        Map<String, Object> result = new LinkedHashMap<>();
        String trimmed = rawBody.trim();
        if (trimmed.startsWith("{")) {
            try {
                com.fasterxml.jackson.databind.ObjectMapper mapper =
                        new com.fasterxml.jackson.databind.ObjectMapper();
                Map<String, Object> parsed = mapper.readValue(trimmed,
                        new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {
                        });
                result.putAll(parsed);
            } catch (Exception ex) {
                throw new BusinessException(PaymentErrors.CALLBACK_MALFORMED, "invalid_json_body");
            }
        } else {
            // form-urlencoded
            for (String pair : trimmed.split("&")) {
                if (pair.isBlank()) {
                    continue;
                }
                int idx = pair.indexOf('=');
                if (idx < 0) {
                    continue;
                }
                String key = urlDecode(pair.substring(0, idx));
                String value = urlDecode(pair.substring(idx + 1));
                result.put(key, value);
            }
        }
        return result;
    }

    private static String urlDecode(String raw) {
        try {
            return java.net.URLDecoder.decode(raw, StandardCharsets.UTF_8);
        } catch (Exception ex) {
            return raw;
        }
    }

    private static String header(Map<String, String> headers, String name) {
        if (headers == null) {
            return null;
        }
        String value = headers.get(name);
        if (value == null) {
            for (Map.Entry<String, String> e : headers.entrySet()) {
                if (e.getKey() != null && e.getKey().equalsIgnoreCase(name)) {
                    return e.getValue();
                }
            }
        }
        return value;
    }

    private static Long headerLong(Map<String, String> headers, String name) {
        String value = header(headers, name);
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static String str(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static String hmacSha256(String secret, String payload) {
        try {
            javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
            mac.init(new javax.crypto.spec.SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8),
                    "HmacSHA256"));
            byte[] bytes = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception ex) {
            throw new IllegalStateException("HMAC-SHA256 unavailable", ex);
        }
    }

    private static boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) {
            return false;
        }
        byte[] ba = a.getBytes(StandardCharsets.UTF_8);
        byte[] bb = b.getBytes(StandardCharsets.UTF_8);
        if (ba.length != bb.length) {
            return false;
        }
        int diff = 0;
        for (int i = 0; i < ba.length; i++) {
            diff |= ba[i] ^ bb[i];
        }
        return diff == 0;
    }

    /**
     * Mock 渠道内部支付记录。
     */
    private static final class MockPayment {
        private final String orderNo;
        private BigDecimal amount;
        private final String currency;
        private final OffsetDateTime createdAt;
        private volatile String status;
        private volatile OffsetDateTime paidAt;
        private volatile String providerOrderId;

        private MockPayment(String orderNo, BigDecimal amount, String currency,
                            OffsetDateTime createdAt, String status) {
            this.orderNo = orderNo;
            this.amount = amount;
            this.currency = currency;
            this.createdAt = createdAt;
            this.status = status;
            this.providerOrderId = "MOCK-" + orderNo;
        }

        String providerOrderId() {
            return providerOrderId;
        }

        String orderNo() {
            return orderNo;
        }

        BigDecimal amount() {
            return amount;
        }

        String currency() {
            return currency;
        }

        String status() {
            return status;
        }

        OffsetDateTime paidAt() {
            return paidAt;
        }
    }
}
