package com.shortvideoscripagent.xhsagentyunying.infra.taskqueue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shortvideoscripagent.xhsagentyunying.config.TaskQueueProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.StreamReadOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;

/**
 * P0-1: 任务队列消费者。
 * <p>
 * 使用 Redis Stream XREADGROUP 消费消息，将任务路由到 {@link TaskRegistry} 对应的 {@link TaskHandler}。
 * </p>
 * <p>
 * 关键行为：
 * <ul>
 *   <li>使用 Consumer Group 保证消息不重复消费</li>
 *   <li>失败后指数退避重试（最多 3 次），超过后入 DLQ</li>
 *   <li>心跳写入 {@link TaskHeartbeat}</li>
 *   <li>ACK 消息在处理成功后</li>
 * </ul>
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.task-queue.enabled", havingValue = "true", matchIfMissing = false)
public class TaskQueueConsumer {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final TaskQueueProperties properties;
    private final TaskRegistry taskRegistry;
    private final TaskHeartbeat heartbeat;
    private final TaskQueueProducer producer;
    private final DeadLetterQueue deadLetterQueue;

    /**
     * 解析消息载荷为 Map。
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> deserializePayload(String json, String taskType) {
        try {
            return objectMapper.readValue(json, Map.class);
        } catch (Exception ex) {
            log.warn("Failed to deserialize payload for taskType={}: {}", taskType, ex.getMessage());
            return Map.of("raw", json);
        }
    }

    private Integer extractErrorCode(Exception ex) {
        if (ex instanceof com.shortvideoscripagent.xhsagentyunying.common.exception.BusinessException be) {
            return be.getCode();
        }
        return 50000;
    }

    // 注意：完整的消费循环实现在 TaskQueueConsumerRunner 中
}
