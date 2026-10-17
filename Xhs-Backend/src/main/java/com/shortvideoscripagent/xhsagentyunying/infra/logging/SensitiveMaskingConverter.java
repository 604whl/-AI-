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
                    + "([^\\s,&;\\n\\r]+)",            // capture value (no whitespace/special chars)
            Pattern.CASE_INSENSITIVE
    );

    private static final String MASKED = "***MASKED***";

    @Override
    protected String transform(ILoggingEvent event, String in) {
        if (in == null || in.isEmpty()) {
            return in;
        }
        // 脱敏处理
        String masked = SENSITIVE_PATTERN.matcher(in).replaceAll("$1: ***MASKED***");
        // 额外兜底：直接 mask 已知的 key 字符串
        masked = maskKnownPatterns(masked);
        return masked;
    }

    /**
     * 已知高风险模式的兜底 mask。
     * 例如 JWT token 结构：Bearer eyJhbGci...
     */
    private String maskKnownPatterns(String input) {
        // JWT Bearer token
        input = input.replaceAll("(?i)Bearer\\s+([A-Za-z0-9_\\-]+\\.[A-Za-z0-9_\\-]+\\.[A-Za-z0-9_\\-]+)",
                "Bearer ***MASKED***");
        // x-api-key header style
        input = input.replaceAll("(?i)x-api-key['\"]?\\s*[:=]\\s*['\"]?([A-Za-z0-9_\\-]+)['\"]?",
                "X-API-Key: ***MASKED***");
        // xhs_sk_ prefix API keys (Stripe-like)
        input = input.replaceAll("(?i)xhs_sk_[A-Za-z0-9]{20,}",
                "xhs_sk_***MASKED***");
        // Generic sk_live / sk_test API keys
        input = input.replaceAll("(?i)sk_(live|test)_[A-Za-z0-9]{20,}",
                "sk_***MASKED***");
        return input;
    }
}
