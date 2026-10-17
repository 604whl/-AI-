package com.shortvideoscripagent.xhsagentyunying.infra.ratelimit;

import com.shortvideoscripagent.xhsagentyunying.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

/**
 * 基于 Redis Lua 脚本的固定窗口限流器。
 * <p>
 * 所有操作原子化，避免竞争条件。
 * </p>
 *
 * <h3>限流维度</h3>
 * <ul>
 *   <li>IP — 登录、注册等无登录态操作</li>
 *   <li>userId — 个人上传等已认证操作</li>
 *   <li>workspaceId — workspace 级 AI 调用、导出等</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RedisRateLimiter {

    private static final int CODE_RATELIMITED = 42901;

    private final StringRedisTemplate redisTemplate;

    /** 固定窗口 Lua 脚本：key, windowSeconds, maxCount → 1=允许, 0=拒绝 */
    private static final String FIXED_WINDOW_SCRIPT = """
            local key = KEYS[1]
            local window = tonumber(ARGV[1])
            local limit = tonumber(ARGV[2])
            local now = tonumber(ARGV[3])

            redis.call('ZREMRANGEBYSCORE', key, '-inf', now - window)
            local count = redis.call('ZCARD', key)

            if count >= limit then
                return 0
            end

            redis.call('ZADD', key, now, now .. '-' .. math.random())
            redis.call('EXPIRE', key, window)
            return 1
            """;

    private final DefaultRedisScript<Long> script = buildScript();

    private DefaultRedisScript<Long> buildScript() {
        DefaultRedisScript<Long> s = new DefaultRedisScript<>();
        s.setScriptText(FIXED_WINDOW_SCRIPT);
        s.setResultType(Long.class);
        return s;
    }

    /**
     * 检查是否允许通过。
     *
     * @param dimension   限流维度（ip / userId / workspaceId）
     * @param key         限流键名
     * @param limit       窗口内最大次数
     * @param windowSeconds 窗口秒数
     * @return true = 允许，false = 限流
     */
    public boolean check(String dimension, String key, int limit, int windowSeconds) {
        return tryAcquire(dimension, key, limit, windowSeconds, false);
    }

    /**
     * 检查并通过，超限抛异常。
     *
     * @throws BusinessException (429) 超限
     */
    public void require(String dimension, String key, int limit, int windowSeconds) {
        boolean allowed = tryAcquire(dimension, key, limit, windowSeconds, false);
        if (!allowed) {
            log.warn("Rate limit exceeded: dimension={} key={} limit={} window={}s",
                    dimension, key, limit, windowSeconds);
            throw new BusinessException(CODE_RATELIMITED, "rate_limit_exceeded");
        }
    }

    /**
     * 主动扣减计数（用于 AI 调用等事后计量场景）。
     *
     * @return 扣减后的剩余次数
     */
    public long consume(String dimension, String key, int limit, int windowSeconds) {
        String redisKey = buildKey(dimension, key);
        try {
            Long result = redisTemplate.execute(
                    script,
                    Collections.singletonList(redisKey),
                    String.valueOf(windowSeconds),
                    String.valueOf(limit),
                    String.valueOf(System.currentTimeMillis() / 1000)
            );
            if (result == null || result == 0) {
                throw new BusinessException(CODE_RATELIMITED, "rate_limit_exceeded");
            }
            Long remaining = redisTemplate.opsForZSet().zCard(redisKey);
            return remaining != null ? remaining : limit;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("Redis rate limit error: {}", e.getMessage());
            // Redis 异常时降级放行，避免影响核心流程
            return limit;
        }
    }

    /**
     * 获取剩余次数（不扣减）。
     */
    public long remaining(String dimension, String key, int limit, int windowSeconds) {
        String redisKey = buildKey(dimension, key);
        try {
            long now = System.currentTimeMillis() / 1000;
            redisTemplate.opsForZSet().removeRangeByScore(redisKey, Double.NEGATIVE_INFINITY, now - windowSeconds);
            Long count = redisTemplate.opsForZSet().zCard(redisKey);
            return Math.max(0, limit - (count != null ? count : 0));
        } catch (Exception e) {
            log.error("Redis rate limit remaining check error: {}", e.getMessage());
            return limit;
        }
    }

    private boolean tryAcquire(String dimension, String key, int limit, int windowSeconds, boolean async) {
        String redisKey = buildKey(dimension, key);
        try {
            Long result = redisTemplate.execute(
                    script,
                    Collections.singletonList(redisKey),
                    String.valueOf(windowSeconds),
                    String.valueOf(limit),
                    String.valueOf(System.currentTimeMillis() / 1000)
            );
            return result != null && result == 1;
        } catch (Exception e) {
            log.error("Redis rate limit error: {}", e.getMessage());
            // Redis 异常时降级放行
            return true;
        }
    }

    private String buildKey(String dimension, String key) {
        return "ratelimit:" + dimension + ":" + key;
    }

    /* ========== 快捷方法 ========== */

    public void requireLogin(String ip, int limit, int windowSeconds) {
        require("ip", "login:" + ip, limit, windowSeconds);
    }

    public void requireRegister(String ip, int limit, int windowSeconds) {
        require("ip", "register:" + ip, limit, windowSeconds);
    }

    public void requireAiCall(String workspaceId, String action, int limit, int windowSeconds) {
        require("workspace", workspaceId + ":" + action, limit, windowSeconds);
    }

    public void requireUpload(String userId, int limit, int windowSeconds) {
        require("user", userId + ":upload", limit, windowSeconds);
    }

    public void requireExport(String workspaceId, int limit, int windowSeconds) {
        require("workspace", workspaceId + ":export", limit, windowSeconds);
    }
}
