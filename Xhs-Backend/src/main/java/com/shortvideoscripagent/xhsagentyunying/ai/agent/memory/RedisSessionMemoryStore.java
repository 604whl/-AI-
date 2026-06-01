package com.shortvideoscripagent.xhsagentyunying.ai.agent.memory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shortvideoscripagent.xhsagentyunying.config.AppAgentProperties;
import com.shortvideoscripagent.xhsagentyunying.domain.entity.ChatSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class RedisSessionMemoryStore implements SessionMemoryStore {

    private static final String SESSION_KEY_PREFIX = "agent:session:";
    private static final String CONTEXT_KEY_PREFIX = "agent:ctx:";

    private final StringRedisTemplate redisTemplate;
    private final AppAgentProperties appAgentProperties;
    private final ObjectMapper objectMapper;

    @Override
    public void touchSession(ChatSession session) {
        try {
            String key = SESSION_KEY_PREFIX + session.getId();
            redisTemplate.opsForHash().put(key, "persona", session.getPersona() == null ? "agency" : session.getPersona());
            if (session.getLinkedTaskId() != null) {
                redisTemplate.opsForHash().put(key, "linkedTaskId", session.getLinkedTaskId());
            }
            redisTemplate.expire(key, ttl());
        } catch (Exception ex) {
            log.debug("Redis session touch skipped: {}", ex.getMessage());
        }
    }

    @Override
    public void cacheContextSnippet(String sessionId, Map<String, Object> snippet) {
        try {
            String key = CONTEXT_KEY_PREFIX + sessionId;
            redisTemplate.opsForList().leftPush(key, objectMapper.writeValueAsString(snippet));
            redisTemplate.opsForList().trim(key, 0, 49);
            redisTemplate.expire(key, ttl());
        } catch (Exception ex) {
            log.debug("Redis context cache skipped: {}", ex.getMessage());
        }
    }

    @Override
    public void clearSession(String sessionId) {
        try {
            redisTemplate.delete(SESSION_KEY_PREFIX + sessionId);
            redisTemplate.delete(CONTEXT_KEY_PREFIX + sessionId);
        } catch (Exception ex) {
            log.debug("Redis session clear skipped: {}", ex.getMessage());
        }
    }

    private Duration ttl() {
        return Duration.ofHours(Math.max(appAgentProperties.getSessionTtlHours(), 1));
    }
}
