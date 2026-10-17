package com.shortvideoscripagent.xhsagentyunying.infra.logging;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.pattern.CompositeConverter;

import java.util.regex.Pattern;

/**
 * Logback 日志脱敏转换器。
 * <p>
 * 自动将日志消息中的敏感字段（Authorization、Cookie、password、token、API Key 等）
 * 替换为 ***MASKED***，防止敏感信息写入日志。
 * </p>
 *
 * <h3>使用方式（在 logback-spring.xml 中）</h3>
 * <pre>
 * &lt;conversionRule conversionWord="maskSensitive"
 *                  converterClass="com.shortvideoscripagent.xhsagentyunying.infra.logging.SensitiveMaskingConverter" /&gt;
 *
 * &lt;pattern&gt;%d{yyyy-MM-dd HH:mm:ss} [%thread] %-5level %maskSensitive(%msg)%n&lt;/pattern&gt;
 * </pre>
 */
public class SensitiveMaskingConverter extends CompositeConverter<ILoggingEvent> {

    /**
     * 匹配敏感字段 key=value 模式。
     * 支持：key: value、key=value、key = value 等格式。
     * 不区分大小写。
     * 注意：value 段允许逗号（用于匹配包含逗号的完整 JWT/JSON 段）。
     */
    private static final Pattern SENSITIVE_PATTERN = Pattern.compile(
            "(?i)"                                    // case insensitive
                    + "("                              // start capture
                    + "authorization|cookie|password|passwd|secret"
                    + "|token|accesstoken|refreshtoken|apikey|api_key|x-api-key"
                    + "|bearer|jwt|credential|private.key"
                    + "|x.auth|x-token|x-session"
                    + ")"                              // sensitive key name
                    + "\\s*[:=]\\s*"                   // separator
                    + "([^\\s&;\\n\\r]+)",            // capture value (allow comma, exclude &;\n\r)
            Pattern.CASE_INSENSITIVE
    );

    private static final String MASKED = "***MASKED***";

    @Override
    protected String transform(ILoggingEvent event, String in) {
        if (in == null || in.isEmpty()) {
            return in;
        }
        String masked = in;
        // 第一步：直接处理"Authorization: Bearer <JWT>"模式（JWT 段可能含逗号，需要独立处理）
        masked = maskAuthorizationBearerJwt(masked);
        // 第二步：用通用 key=value 模式脱敏（保守，不含空格）
        masked = SENSITIVE_PATTERN.matcher(masked).replaceAll("$1: ***MASKED***");
        // 第三步：兜底处理剩余的已知 key 字符串
        masked = maskKnownPatterns(masked);
        return masked;
    }

    /**
     * 直接处理"Authorization: Bearer <完整 JWT>"模式。
     * 完整 JWT 段可能含逗号，通用正则无法一次匹配，需要在 SENSITIVE_PATTERN 之前先处理。
     */
    private String maskAuthorizationBearerJwt(String input) {
        // 匹配 "Authorization: Bearer xxx.yyy.zzz"，xxx/yyy/zzz 可含逗号、下划线、短横线
        return input.replaceAll(
                "(?i)(authorization\\s*[:=]\\s*)Bearer\\s+([A-Za-z0-9_\\-]+(?:[.,][A-Za-z0-9_\\-]+)*\\.[A-Za-z0-9_\\-]+(?:[.,][A-Za-z0-9_\\-]+)*\\.[A-Za-z0-9_\\-]+(?:[.,][A-Za-z0-9_\\-]+)*)",
                "$1Bearer ***MASKED***");
    }

    /**
     * 已知高风险模式的兜底 mask。
     */
    private String maskKnownPatterns(String input) {
        // 纯 Bearer token（不含 Authorization: 前缀，例如裸 JWT 输出）
        input = input.replaceAll(
                "(?i)\\bBearer\\s+([A-Za-z0-9_\\-]+(?:[.,][A-Za-z0-9_\\-]+)*\\.[A-Za-z0-9_\\-]+(?:[.,][A-Za-z0-9_\\-]+)*\\.[A-Za-z0-9_\\-]+(?:[.,][A-Za-z0-9_\\-]+)*)",
                "Bearer ***MASKED***");
        // x-api-key header style
        input = input.replaceAll("(?i)x-api-key['\"]?\\s*[:=]\\s*['\"]?([A-Za-z0-9_\\-]+)['\"]?",
                "X-API-Key: ***MASKED***");
        // xhs_sk_ prefix API keys
        input = input.replaceAll("(?i)xhs_sk_[A-Za-z0-9]{20,}",
                "xhs_sk_***MASKED***");
        // 泛化：任何 sk_<UPPER>_<value> 形式的 key 都脱敏（live/test/prod/sandbox 等）
        input = input.replaceAll("(?i)sk_[A-Z][A-Z0-9]*_[A-Za-z0-9]{20,}",
                "sk_***MASKED***");
        return input;
    }
}
