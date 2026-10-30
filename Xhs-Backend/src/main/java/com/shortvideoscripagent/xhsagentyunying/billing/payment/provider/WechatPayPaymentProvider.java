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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * S4-2 微信支付渠道（骨架实现）。
 * <p>
 * <b>配置来源（环境变量，不落库）</b>：
 * <ul>
 *   <li>{@code WECHAT_PAY_APP_ID} —— 应用 / 商户 AppId</li>
 *   <li>{@code WECHAT_PAY_MCH_ID} —— 商户号</li>
 *   <li>{@code WECHAT_PAY_API_V3_KEY} —— APIv3 密钥（回调报文解密用）</li>
 *   <li>{@code WECHAT_PAY_PRIVATE_KEY} —— 商户 API 私钥（PKCS8 PEM）</li>
 *   <li>{@code WECHAT_PAY_PLATFORM_CERT} —— 微信支付平台证书（验签用）</li>
 *   <li>{@code WECHAT_PAY_NOTIFY_URL} —— 回调通知地址</li>
 * </ul>
 * </p>
 * <p>
 * <b>未配置时</b>：启动正常；调用任一动作抛
 * {@code BusinessException(50301, "PAYMENT_PROVIDER_NOT_CONFIGURED:wechat")}。
 * </p>
 * <p>
 * <b>签名 / 验签算法</b>（已实现，可直接对接）：
 * <ul>
 *   <li>请求签名：{@code message = method + "\n" + url + "\n" + timestamp + "\n" + nonce + "\n" + body + "\n"}，
 *       SHA256withRSA 商户私钥签名 → Base64 → 放进 {@code Authorization} 头</li>
 *   <li>回调验签：{@code message = timestamp + "\n" + nonce + "\n" + body + "\n"}，
 *       用<b>微信支付平台证书公钥</b>验证 {@code Wechatpay-Signature}</li>
 * </ul>
 * </p>
 * <p>
 * <b>凭据保护</b>：APIv3 密钥 / 私钥 / 平台证书只存在于内存与本地变量，
 * <b>不进日志、不进异常消息、不进 {@code raw_response}</b>。
 * </p>
 */
@Slf4j
@Component
public class WechatPayPaymentProvider implements PaymentProvider {

    public static final String PROVIDER = "wechat";

    private static final String ENV_APP_ID = "WECHAT_PAY_APP_ID";
    private static final String ENV_MCH_ID = "WECHAT_PAY_MCH_ID";
    private static final String ENV_API_V3_KEY = "WECHAT_PAY_API_V3_KEY";
    private static final String ENV_PRIVATE_KEY = "WECHAT_PAY_PRIVATE_KEY";
    private static final String ENV_PLATFORM_CERT = "WECHAT_PAY_PLATFORM_CERT";
    private static final String ENV_NOTIFY_URL = "WECHAT_PAY_NOTIFY_URL";

    private static final String HEADER_TIMESTAMP = "Wechatpay-Timestamp";
    private static final String HEADER_NONCE = "Wechatpay-Nonce";
    private static final String HEADER_SIGNATURE = "Wechatpay-Signature";

    /** 时间戳窗口（秒） */
    private static final int TIMESTAMP_WINDOW_SECONDS = 300;

    @Override
    public String provider() {
        return PROVIDER;
    }

    @Override
    public boolean configured() {
        return PaymentCredentials.allPresent(ENV_APP_ID, ENV_MCH_ID, ENV_API_V3_KEY,
                ENV_PRIVATE_KEY, ENV_PLATFORM_CERT);
    }

    @Override
    public CreatePaymentResult createPayment(CreatePaymentRequest request) {
        requireConfigured();
        if (request == null || request.orderNo() == null || request.orderNo().isBlank()) {
            throw new BusinessException(PaymentErrors.ORDER_INVALID, "order_no_required");
        }
        BigDecimal amount = request.amount() == null ? BigDecimal.ZERO : request.amount();
        String currency = request.currency() == null ? "CNY" : request.currency();
        int amountCent = amount.movePointRight(2).intValue();

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("appid", PaymentCredentials.env(ENV_APP_ID));
        payload.put("mchid", PaymentCredentials.env(ENV_MCH_ID));
        payload.put("description", request.subject() == null ? "XhsAgent 订阅" : request.subject());
        payload.put("out_trade_no", request.orderNo());
        payload.put("notify_url", request.notifyUrl() != null
                ? request.notifyUrl()
                : PaymentCredentials.env(ENV_NOTIFY_URL));
        payload.put("amount", Map.of("total", amountCent, "currency", currency));

        Map<String, String> payParams = new LinkedHashMap<>();
        payParams.put("provider", PROVIDER);
        payParams.put("out_trade_no", request.orderNo());
        payParams.put("amount", amount.toPlainString());
        payParams.put("currency", currency);

        // 骨架：真实实现需调 JSAPI / H5 / Native 下单接口并解析 code_url / prepay_id
        String payUrl = "wechat://wxpay/bizpayurl?pr=" + request.orderNo();
        return new CreatePaymentResult(PROVIDER, request.orderNo(), payParams, payUrl,
                null, amount, currency);
    }

    @Override
    public PaymentQueryResult queryPayment(String providerOrderId) {
        requireConfigured();
        if (providerOrderId == null || providerOrderId.isBlank()) {
            throw new BusinessException(PaymentErrors.ORDER_INVALID, "provider_order_id_required");
        }
        // 骨架：真实实现需调 /v3/pay/transactions/out-trade-no/{out_trade_no}
        throw new BusinessException(PaymentErrors.PROVIDER_ERROR, "wechat_query_not_implemented");
    }

    @Override
    public RefundResult refund(RefundRequest request) {
        requireConfigured();
        if (request == null || request.providerOrderId() == null || request.providerOrderId().isBlank()) {
            throw new BusinessException(PaymentErrors.ORDER_INVALID, "provider_order_id_required");
        }
        // 骨架：真实实现需调 /v3/refund/domestic/refunds
        throw new BusinessException(PaymentErrors.PROVIDER_ERROR, "wechat_refund_not_implemented");
    }

    @Override
    public PaymentCallback verifyCallback(String rawBody, Map<String, String> headers) {
        requireConfigured();
        if (rawBody == null || rawBody.isBlank()) {
            throw new BusinessException(PaymentErrors.CALLBACK_MALFORMED, "empty_body");
        }

        // ① 验签
        String timestamp = header(headers, HEADER_TIMESTAMP);
        String nonce = header(headers, HEADER_NONCE);
        String signature = header(headers, HEADER_SIGNATURE);
        if (timestamp == null || timestamp.isBlank()) {
            throw new BusinessException(PaymentErrors.TIMESTAMP_OUT_OF_WINDOW, "timestamp_missing");
        }
        if (nonce == null || nonce.isBlank()) {
            throw new BusinessException(PaymentErrors.CALLBACK_MALFORMED, "nonce_missing");
        }
        if (signature == null || signature.isBlank()) {
            throw new BusinessException(PaymentErrors.SIGNATURE_INVALID, "signature_missing");
        }
        if (!verifySignature(timestamp, nonce, rawBody, signature)) {
            throw new BusinessException(PaymentErrors.SIGNATURE_INVALID, "signature_invalid");
        }

        // ② 时间戳窗口
        long ts;
        try {
            ts = Long.parseLong(timestamp.trim());
        } catch (NumberFormatException ex) {
            throw new BusinessException(PaymentErrors.TIMESTAMP_OUT_OF_WINDOW, "invalid_timestamp");
        }
        if (Math.abs(System.currentTimeMillis() / 1000 - ts) > TIMESTAMP_WINDOW_SECONDS) {
            throw new BusinessException(PaymentErrors.TIMESTAMP_OUT_OF_WINDOW,
                    "timestamp_out_of_window");
        }

        // ③ 解析（骨架：未实现 APIv3 报文解密，这里只解析明文 JSON 便于契约测试）
        Map<String, Object> body = parseJson(rawBody);
        Map<String, Object> resource = body.get("resource") instanceof Map
                ? castMap(body.get("resource")) : Map.of();
        Map<String, Object> plaintext = resource.get("ciphertext") == null
                ? body : resource;

        String eventId = str(plaintext.get("transaction_id"));
        String orderNo = str(plaintext.get("out_trade_no"));
        String tradeState = str(plaintext.get("trade_state"));
        Object amountObj = plaintext.get("amount");
        BigDecimal amount = null;
        String currency = null;
        if (amountObj instanceof Map amt) {
            Object total = amt.get("total");
            if (total instanceof Number n) {
                amount = BigDecimal.valueOf(n.longValue(), 2);
            }
            currency = str(amt.get("currency"));
        }
        if (eventId == null || eventId.isBlank()) {
            throw new BusinessException(PaymentErrors.CALLBACK_MALFORMED, "transaction_id_required");
        }
        if (orderNo == null || orderNo.isBlank()) {
            throw new BusinessException(PaymentErrors.CALLBACK_MALFORMED, "out_trade_no_required");
        }
        if (amount == null) {
            throw new BusinessException(PaymentErrors.CALLBACK_MALFORMED, "amount_required");
        }

        boolean paid = "SUCCESS".equalsIgnoreCase(tradeState);
        OffsetDateTime occurredAt = parseWechatTime(str(plaintext.get("success_time")));
        if (occurredAt == null) {
            occurredAt = OffsetDateTime.now();
        }

        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("transaction_id", eventId);
        raw.put("out_trade_no", orderNo);
        raw.put("amount", amount.toPlainString());
        raw.put("currency", currency);
        raw.put("trade_state", tradeState);
        raw.put("occurred_at", occurredAt.toString());

        return new PaymentCallback(eventId, paid ? "payment.succeeded" : "payment.pending",
                eventId, orderNo, amount, currency, paid, occurredAt, raw);
    }

    @Override
    public List<ProviderBill> queryDailyBills(LocalDate day) {
        requireConfigured();
        // 骨架：真实实现需下载交易账单
        return new ArrayList<>();
    }

    // ── 内部 ─────────────────────────────────────────────────────────────

    private void requireConfigured() {
        if (!configured()) {
            log.warn("Wechat provider not configured, missing env keys: {}",
                    PaymentCredentials.missing(ENV_APP_ID, ENV_MCH_ID, ENV_API_V3_KEY,
                            ENV_PRIVATE_KEY, ENV_PLATFORM_CERT));
            throw PaymentErrors.notConfigured(PROVIDER);
        }
    }

    /**
     * 回调验签：{@code timestamp\nnonce\nbody\n} → SHA256withRSA（微信平台证书公钥）。
     */
    private boolean verifySignature(String timestamp, String nonce, String body, String signature) {
        try {
            java.security.PublicKey key = PemKeyUtils.parsePublicKey(
                    PaymentCredentials.env(ENV_PLATFORM_CERT));
            String message = timestamp + "\n" + nonce + "\n" + body + "\n";
            java.security.Signature verifier = java.security.Signature.getInstance("SHA256withRSA");
            verifier.initVerify(key);
            verifier.update(message.getBytes(StandardCharsets.UTF_8));
            return verifier.verify(java.util.Base64.getDecoder().decode(signature));
        } catch (Exception ex) {
            log.warn("Wechat signature verify error: {}", ex.getClass().getSimpleName());
            return false;
        }
    }

    /**
     * 请求签名：{@code method\nurl\ntimestamp\nnonce\nbody\n} → 商户私钥签名 → Base64。
     */
    public static String signRequest(String method, String url, String timestamp,
                                     String nonce, String body) {
        try {
            java.security.PrivateKey key = PemKeyUtils.parsePrivateKey(
                    PaymentCredentials.env(ENV_PRIVATE_KEY));
            String message = method + "\n" + url + "\n" + timestamp + "\n" + nonce + "\n"
                    + (body == null ? "" : body) + "\n";
            java.security.Signature signer = java.security.Signature.getInstance("SHA256withRSA");
            signer.initSign(key);
            signer.update(message.getBytes(StandardCharsets.UTF_8));
            return java.util.Base64.getEncoder().encodeToString(signer.sign());
        } catch (Exception ex) {
            throw new BusinessException(PaymentErrors.PROVIDER_ERROR, "wechat_sign_failed");
        }
    }

    private static Map<String, Object> parseJson(String rawBody) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().readValue(rawBody,
                    new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {
                    });
        } catch (Exception ex) {
            throw new BusinessException(PaymentErrors.CALLBACK_MALFORMED, "invalid_json_body");
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castMap(Object value) {
        return (Map<String, Object>) value;
    }

    private static OffsetDateTime parseWechatTime(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return OffsetDateTime.parse(value);
        } catch (Exception ex) {
            return null;
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

    private static String str(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
