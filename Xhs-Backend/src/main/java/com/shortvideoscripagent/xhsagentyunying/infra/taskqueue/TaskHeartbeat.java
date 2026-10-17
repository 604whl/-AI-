package com.shortvideoscripagent.xhsagentyunying.infra.taskqueue;

import com.shortvideoscripagent.xhsagentyunying.config.TaskQueueProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * P0-1: 任务心跳服务。
 * <p>
 * Worker 每 5 秒写心跳到 Redis SET，心跳 TTL=30s。
 * {@link TaskRecoveryService} 通过检查心跳是否存在来判断 worker 是否存活。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.task-queue.enabled", havingValue = "true", matchIfMissing = false)
public class TaskHeartbeat {

    private static final String HEARTBEAT_KEY_PREFIX = "xhs:tasks:heartbeat:";

    private final StringRedisTemplate redisTemplate;
    private final TaskQueueProperties properties;

    /**
     * 写心跳（Worker 启动后由 TaskQueueConsumer 调用）。
     */
    public void heartbeat() {
        String key = HEARTBEAT_KEY_PREFIX + properties.getWorkerId();
        int ttlSeconds = properties.getHeartbeatTimeoutSeconds();
        redisTemplate.opsForValue().set(key, String.valueOf(System.currentTimeMillis()), Duration.ofSeconds(ttlSeconds));
    }

    /**
     * 检查 worker 心跳是否存在。
     */
    public boolean isWorkerAlive(String workerId) {
        String key = HEARTBEAT_KEY_PREFIX + workerId;
        return Boolean.TRUE.equals(redisTemplate.hasKey(key));
    }

    /**
     * 注销 worker（可选：用于优雅停机）。
     */
    public void unregister() {
        String key = HEARTBEAT_KEY_PREFIX + properties.getWorkerId();
        redisTemplate.delete(key);
        log.info("Worker {} unregistered from heartbeat", properties.getWorkerId());
    }
}
