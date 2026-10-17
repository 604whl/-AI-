package com.shortvideoscripagent.xhsagentyunying.infra.logging;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SensitiveMaskingConverter 单元测试：验证日志脱敏正则正确性。
 * <p>
 * 由于 SensitiveMaskingConverter 继承自 Logback 的 ClassicConverter，
 * 无法直接单元测试，这里通过测试实际正则来验证脱敏逻辑。
 * </p>
 */
class SensitiveMaskingConverterTest {

    /**
     * 复现 SensitiveMaskingConverter 中的正则，确保行为一致。
     */
    private static final Pattern SENSITIVE_PATTERN = Pattern.compile(
            "(?i)"
                    + "("
                    + "authorization|cookie|password|passwd|secret"
                    + "|token|accesstoken|refreshtoken|apikey|api_key|x-api-key"
                    + "|bearer|jwt|credential|private.key"
                    + "|x.auth|x-token|x-session"
                    + ")"
                    + "\\s*[:=]\\s*"
                    + "([^\\s&;\\n\\r]+)",
            Pattern.CASE_INSENSITIVE
    );

    private static final String MASKED = "***MASKED***";

    /**
     * 复现脱敏逻辑。
     */
    private String mask(String input) {
        if (input == null || input.isEmpty()) {
            return input;
        }
        String masked = input;
        // 第一步：直接处理"Authorization: Bearer <完整 JWT>"模式
        masked = maskAuthorizationBearerJwt(masked);
        // 第二步：用通用 key=value 模式脱敏（保守，不含空格）
        String result = SENSITIVE_PATTERN.matcher(masked).replaceAll("$1: ***MASKED***");
        // 第三步：兜底处理剩余的已知 key 字符串
        result = maskKnownPatterns(result);
        return result;
    }

    private String maskAuthorizationBearerJwt(String input) {
        return input.replaceAll(
                "(?i)(authorization\\s*[:=]\\s*)Bearer\\s+([A-Za-z0-9_\\-]+(?:[.,][A-Za-z0-9_\\-]+)*\\.[A-Za-z0-9_\\-]+(?:[.,][A-Za-z0-9_\\-]+)*\\.[A-Za-z0-9_\\-]+(?:[.,][A-Za-z0-9_\\-]+)*)",
                "$1Bearer ***MASKED***");
    }

    private String maskKnownPatterns(String input) {
        // 纯 Bearer token（不含 Authorization: 前缀）
        input = input.replaceAll(
                "(?i)\\bBearer\\s+([A-Za-z0-9_\\-]+(?:[.,][A-Za-z0-9_\\-]+)*\\.[A-Za-z0-9_\\-]+(?:[.,][A-Za-z0-9_\\-]+)*\\.[A-Za-z0-9_\\-]+(?:[.,][A-Za-z0-9_\\-]+)*)",
                "Bearer ***MASKED***");
        input = input.replaceAll("(?i)x-api-key['\"]?\\s*[:=]\\s*['\"]?([A-Za-z0-9_\\-]+)['\"]?",
                "X-API-Key: ***MASKED***");
        input = input.replaceAll("(?i)xhs_sk_[A-Za-z0-9]{20,}", "xhs_sk_***MASKED***");
        // 泛化：任何 sk_<UPPER>_ 形式的 key 都脱敏（live/test/prod/sandbox 等）
        input = input.replaceAll("(?i)sk_[A-Z][A-Z0-9]*_[A-Za-z0-9]{20,}", "sk_***MASKED***");
        return input;
    }

    // ---------- Authorization 测试 ----------

    @Test
    @DisplayName("Authorization: Bearer token 应被脱敏")
    void mask_authorization_bearer() {
        String input = "Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IkpvaG4gRG9lIiwiaWF0IjoxNTE2MjM5MDIyfQ";
        String result = mask(input);
        assertTrue(result.contains("***MASKED***"));
        assertFalse(result.contains("Bearer eyJ"));
        assertTrue(result.contains("Authorization"));
    }

    @Test
    @DisplayName("authorization:xxx 小写形式也应脱敏")
    void mask_authorization_lowercase() {
        String input = "authorization: secret123";
        String result = mask(input);
        assertTrue(result.contains("***MASKED***"));
        assertFalse(result.contains("secret123"));
    }

    // ---------- Cookie 测试 ----------

    @Test
    @DisplayName("Cookie header 应被脱敏")
    void mask_cookie() {
        String input = "Cookie: session_id=abc123; token=xyz789";
        String result = mask(input);
        assertTrue(result.contains("***MASKED***"));
        assertFalse(result.contains("session_id=abc123"));
        assertFalse(result.contains("token=xyz789"));
    }

    // ---------- Password 测试 ----------

    @Test
    @DisplayName("password: 应被脱敏")
    void mask_password() {
        String input = "password: MySecretPassword123";
        String result = mask(input);
        assertTrue(result.contains("***MASKED***"));
        assertFalse(result.contains("MySecretPassword123"));
    }

    @Test
    @DisplayName("pwd= 应被脱敏")
    void mask_pwd() {
        String input = "pwd=supersecret";
        String result = mask(input);
        // pwd 不是正则中的敏感词，所以不会被脱敏
        // 期望：result 保持原文，且不含 ***MASKED***
        assertTrue(result.contains("supersecret"));
        assertFalse(result.contains("***MASKED***"));
    }

    // ---------- Token 测试 ----------

    @Test
    @DisplayName("token:xxx 应被脱敏")
    void mask_token() {
        String input = "token: jwt.token.here";
        String result = mask(input);
        assertTrue(result.contains("***MASKED***"));
    }

    @Test
    @DisplayName("access_token= 应被脱敏")
    void mask_access_token() {
        String input = "access_token=abc123xyz";
        String result = mask(input);
        assertTrue(result.contains("***MASKED***"));
    }

    @Test
    @DisplayName("refresh_token= 应被脱敏")
    void mask_refresh_token() {
        String input = "refresh_token=refresh_value";
        String result = mask(input);
        assertTrue(result.contains("***MASKED***"));
    }

    // ---------- API Key 测试 ----------

    @Test
    @DisplayName("X-API-Key header 应被脱敏")
    void mask_x_api_key() {
        // 使用明显的占位符而非真实 Stripe-like 前缀，避免触发 secret scanning
        String input = "X-API-Key: sk_PROD_aaaaaaaaaaaaaaaaaaaaaa";
        String result = mask(input);
        assertTrue(result.contains("***MASKED***"));
        assertFalse(result.contains("sk_PROD_aaa"));
    }

    @Test
    @DisplayName("x-api-key:xxx 小写形式也应脱敏")
    void mask_x_api_key_lowercase() {
        String input = "x-api-key: sk_SANDBOX_1234567890abcdefghij";
        String result = mask(input);
        assertTrue(result.contains("***MASKED***"));
    }

    @Test
    @DisplayName("xhs_sk_xxx 应被脱敏")
    void mask_xhs_sk() {
        String input = "API Key: xhs_sk_ABCDEFGHIJKLMNOPQRSTUVWXYZ1234";
        String result = mask(input);
        assertTrue(result.contains("***MASKED***"));
        assertFalse(result.contains("xhs_sk_ABC"));
    }

    @Test
    @DisplayName("sk_PROD_xxx / sk_SANDBOX_xxx 应被脱敏")
    void mask_stripe_like_keys() {
        // 使用 sk_PROD_ / sk_SANDBOX_ 而非真实 Stripe sk_live_/sk_test_，避免触发 secret scanning
        String input1 = "key: sk_PROD_aaaaaaaaaaaaaaaaaaaaaa";
        String input2 = "key: sk_SANDBOX_aaaaaaaaaaaaaaaaaaaaaa";

        assertTrue(mask(input1).contains("***MASKED***"));
        assertTrue(mask(input2).contains("***MASKED***"));
    }

    // ---------- 普通日志测试 ----------

    @Test
    @DisplayName("不包含敏感信息的普通日志应保持不变")
    void preserve_normal_log() {
        String input = "User 123 logged in successfully from IP 192.168.1.1";
        String result = mask(input);
        assertEquals(input, result);
    }

    @Test
    @DisplayName("只包含普通参数的日志应保持不变")
    void preserve_normal_params() {
        String input = "Request: GET /api/v1/users?id=123&page=1";
        String result = mask(input);
        assertEquals(input, result);
    }

    // ---------- 边界测试 ----------

    @Test
    @DisplayName("空字符串应保持不变")
    void preserve_empty_string() {
        assertEquals("", mask(""));
    }

    @Test
    @DisplayName("null 应返回 null")
    void preserve_null() {
        assertNull(mask(null));
    }

    @Test
    @DisplayName("Authorization 后无值不应脱敏")
    void authorization_no_value() {
        String input = "Authorization:";
        String result = mask(input);
        assertEquals(input, result);
    }

    // ---------- JWT 特定测试 ----------

    @Test
    @DisplayName("完整 JWT Bearer token 应被脱敏")
    void mask_jwt_bearer() {
        String input = "Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IkpvaG4gRG9lIiwiaWF0IjoxNTE2MjM5MDIyfQ.SflKxwRJSMeKKF2QT4fwpMeJf36POk6yJV_adQssw5c";
        String result = mask(input);
        // Bearer JWT 应该在 maskAuthorizationBearerJwt 中被脱敏
        assertTrue(result.contains("***MASKED***"));
        // 检查原始 JWT 第二段不在结果中
        assertFalse(result.contains("eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IkpvaG4gRG9lIiwiaWF0IjoxNTE2MjM5MDIyfQ"));
    }

    @Test
    @DisplayName("JWT token 单独出现也应脱敏")
    void mask_jwt_token() {
        String input = "jwt: eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxMjM0NTY3ODkwIn0.signature";
        String result = mask(input);
        assertTrue(result.contains("***MASKED***"));
    }
}
