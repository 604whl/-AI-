package com.shortvideoscripagent.xhsagentyunying.auth;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@RequiredArgsConstructor
public class RefreshTokenStore {

    private static final String KEY_PREFIX = "auth:refresh:";

    private final StringRedisTemplate redisTemplate;

    public void save(String jti, Long userId, long ttlSeconds) {
        redisTemplate.opsForValue().set(
                key(jti),
                String.valueOf(userId),
                Duration.ofSeconds(ttlSeconds)
        );
    }

    public Long getUserId(String jti) {
        String value = redisTemplate.opsForValue().get(key(jti));
        if (value == null || value.isBlank()) {
            return null;
        }
        return Long.parseLong(value);
    }

    public void revoke(String jti) {
        redisTemplate.delete(key(jti));
    }

    private String key(String jti) {
        return KEY_PREFIX + jti;
    }
}
