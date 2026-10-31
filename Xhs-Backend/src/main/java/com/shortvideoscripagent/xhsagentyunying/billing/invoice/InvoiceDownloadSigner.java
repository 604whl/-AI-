package com.shortvideoscripagent.xhsagentyunying.billing.invoice;

import com.shortvideoscripagent.xhsagentyunying.config.AppAuthProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * S4-4 发票下载链接签名器。
 * <p>
 * <b>为什么需要</b>：本地存储模式下发票 PDF 存在服务端磁盘，
 * 必须避免「知道路径就能下载」——下载链接带 HMAC 签名 + 过期时间，
 * 5 分钟后自动失效。
 * </p>
 * <p>
 * <b>签名载荷</b>：{@code invoiceNo|expiresAt}，
 * 用 JWT 密钥派生（{@code SHA-256(jwtSecret)}）作为 HMAC 密钥，
 * 不额外引入配置项，也不新增一个需要运维管理的密钥。
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InvoiceDownloadSigner {

    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private final AppAuthProperties authProperties;

    /**
     * 生成签名。
     */
    public String sign(String invoiceNo, long expiresAt) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(deriveKey(), HMAC_ALGORITHM));
            byte[] raw = mac.doFinal(payload(invoiceNo, expiresAt)
                    .getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
        } catch (Exception ex) {
            log.error("Failed to sign invoice download url: {}", ex.getMessage(), ex);
            throw new IllegalStateException("invoice_sign_failed", ex);
        }
    }

    /**
     * 校验签名与过期时间。
     *
     * @return true = 签名有效且未过期
     */
    public boolean verify(String invoiceNo, long expiresAt, String signature) {
        if (invoiceNo == null || signature == null || signature.isBlank()) {
            return false;
        }
        if (expiresAt <= 0 || expiresAt < System.currentTimeMillis()) {
            return false;
        }
        String expected = sign(invoiceNo, expiresAt);
        // 定长比较，避免时序侧信道
        return java.security.MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                signature.getBytes(StandardCharsets.UTF_8));
    }

    private String payload(String invoiceNo, long expiresAt) {
        return invoiceNo + "|" + expiresAt;
    }

    /**
     * 从 JWT 密钥派生 HMAC 密钥（SHA-256，固定 32 字节，满足 HMAC 密钥长度要求）。
     */
    private byte[] deriveKey() {
        String secret = authProperties.getJwtSecret();
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("app.auth.jwt-secret must be configured");
        }
        try {
            return java.security.MessageDigest.getInstance("SHA-256")
                    .digest(secret.getBytes(StandardCharsets.UTF_8));
        } catch (Exception ex) {
            throw new IllegalStateException("sha256_unavailable", ex);
        }
    }
}
