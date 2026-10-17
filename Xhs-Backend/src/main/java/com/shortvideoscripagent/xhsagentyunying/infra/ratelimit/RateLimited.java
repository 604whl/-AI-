package com.shortvideoscripagent.xhsagentyunying.infra.ratelimit;

import java.lang.annotation.*;

/**
 * 限流注解，配合 {@link RateLimitInterceptor} 使用。
 *
 * <h3>使用示例</h3>
 * <pre>
 * {@literal @}RateLimited(key = "login", limit = 5, window = 60, dimension = "ip")
 * {@literal @}PostMapping("/login")
 * public ApiResponse login() { ... }
 * </pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RateLimited {

    /**
     * 限流键标识（会与参数值拼接）。
     * 例如 "login"、"register"、"upload"、"ai:analysis" 等。
     */
    String key();

    /**
     * 窗口内允许的最大次数。
     */
    int limit();

    /**
     * 滑动窗口秒数。
     */
    int window();

    /**
     * 限流维度：ip / user / workspace
     */
    String dimension() default "ip";

    /**
     * HTTP 状态码（默认 429 Too Many Requests）。
     */
    int statusCode() default 429;
}
