package com.shortvideoscripagent.xhsagentyunying.infra.ratelimit;

import com.shortvideoscripagent.xhsagentyunying.common.api.RequestContext;
import com.shortvideoscripagent.xhsagentyunying.common.api.WorkspaceContext;
import com.shortvideoscripagent.xhsagentyunying.common.exception.BusinessException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Collections;

/**
 * 限流拦截器，配合 {@link RateLimited} 注解使用。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RateLimitInterceptor implements HandlerInterceptor {

    private static final int CODE_RATELIMITED = 42901;
    private static final int CODE_WORKSPACE_REQUIRED = 40003;

    private final StringRedisTemplate redisTemplate;
    private final DefaultRedisScript<Long> script = buildScript();

    private static final String FIXED_WINDOW_SCRIPT = """
            local key = KEYS[1]
            local window = tonumber(ARGV[1])
            local limit = tonumber(ARGV[2])
            local now = tonumber(ARGV[3])
            redis.call('ZREMRANGEBYSCORE', key, '-inf', now - window)
            local count = redis.call('ZCARD', key)
            if count >= limit then return 0 end
            redis.call('ZADD', key, now, now .. '-' .. math.random())
            redis.call('EXPIRE', key, window)
            return 1
            """;

    private DefaultRedisScript<Long> buildScript() {
        DefaultRedisScript<Long> s = new DefaultRedisScript<>();
        s.setScriptText(FIXED_WINDOW_SCRIPT);
        s.setResultType(Long.class);
        return s;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if (!(handler instanceof HandlerMethod hm)) {
            return true;
        }
        RateLimited annotation = hm.getMethodAnnotation(RateLimited.class);
        if (annotation == null) {
            return true;
        }

        String dimension = annotation.dimension();
        String keySuffix = resolveKey(annotation, annotation.key(), request);
        String limitKey = buildRedisKey(dimension, keySuffix);
        int limit = annotation.limit();
        int window = annotation.window();
        long now = System.currentTimeMillis() / 1000;

        try {
            Long result = redisTemplate.execute(
                    script,
                    Collections.singletonList(limitKey),
                    String.valueOf(window),
                    String.valueOf(limit),
                    String.valueOf(now)
            );
            if (result == null || result == 0) {
                log.warn("Rate limit exceeded: dimension={} key={} limit={} window={}s",
                        dimension, limitKey, limit, window);
                response.setStatus(annotation.statusCode());
                response.setContentType("application/json;charset=UTF-8");
                response.setCharacterEncoding("UTF-8");
                String body = "{\"code\":" + CODE_RATELIMITED + ",\"message\":\"rate_limit_exceeded\"}";
                response.getWriter().write(body);
                response.getWriter().flush();
                return false;
            }
        } catch (BusinessException e) {
            throw e; // P1-1: propagate workspace_id_required
        } catch (Exception e) {
            log.error("Rate limit check error: {}", e.getMessage());
            // Redis 异常时降级放行，避免影响核心流程
        }
        return true;
    }

    private String resolveKey(RateLimited annotation, String keyPrefix, HttpServletRequest request) {
        String resolved = keyPrefix;
        Long userId = RequestContext.getUserId();
        String workspaceId = WorkspaceContext.getWorkspaceId();
        String clientIp = getClientIp(request);

        // P1-1: fail-closed when dimension=workspace but workspaceId is null
        if ("workspace".equals(annotation.dimension()) && workspaceId == null) {
            throw new BusinessException(CODE_WORKSPACE_REQUIRED, "workspace_id_required");
        }

        // 支持 ${userId}、${workspaceId}、${ip} 占位符替换
        if (resolved.contains("${userId}") && userId != null) {
            resolved = resolved.replace("${userId}", userId.toString());
        }
        if (resolved.contains("${workspaceId}") && workspaceId != null) {
            resolved = resolved.replace("${workspaceId}", workspaceId);
        }
        if (resolved.contains("${ip}")) {
            resolved = resolved.replace("${ip}", clientIp);
        }
        return resolved;
    }

    private String buildRedisKey(String dimension, String keySuffix) {
        return "ratelimit:" + dimension + ":" + keySuffix;
    }

    private String getClientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split("[,\\s]")[0].trim();
        }
        String xri = request.getHeader("X-Real-IP");
        if (xri != null && !xri.isBlank()) {
            return xri.trim();
        }
        return request.getRemoteAddr();
    }
}
