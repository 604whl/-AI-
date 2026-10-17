package com.shortvideoscripagent.xhsagentyunying.infra.taskqueue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shortvideoscripagent.xhsagentyunying.config.TaskQueueProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.StreamOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Map;

/**
 * P0-1: 任务队列生产者。
 * <p>
 * 将任务投递到 Redis Stream（XADD），支持事务后置投递。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.task-queue.enabled", havingValue = "true", matchIfMissing = false)
public class TaskQueueProducer {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final TaskQueueProperties properties;

    /**
     * 投递任务到 Redis Stream。
     * <p>
     * 如果当前有活跃事务，则在事务提交后再投递（确保数据库 COMMIT 后才有任务入队）。
     * </p>
     *
     * @param taskType        任务类型，如 "ANALYSIS"
     * @param taskId         数据库任务 ID
     * @param payloadJson    JSON 序列化的任务载荷
     * @param attempt        当前尝试次数
     */
    public void enqueue(String taskType, String taskId, String payloadJson, int attempt) {
        long scheduledAt = System.currentTimeMillis();

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(
                    new TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            doXadd(taskType, taskId, payloadJson, attempt, scheduledAt);
                        }
                    }
            );
        } else {
            doXadd(taskType, taskId, payloadJson, attempt, scheduledAt);
        }
    }

    /**
     * 重试调度（由 TaskQueueConsumer 调用）。
     */
    public void scheduleRetry(String taskId, String taskType, String payload, int nextAttempt) {
        long scheduledAt = System.currentTimeMillis()
                + properties.retryDelaySeconds(nextAttempt) * 1000L;
        doXadd(taskType, taskId, payload, nextAttempt, scheduledAt);
        log.info("Scheduled retry taskId={} attempt={}", taskId, nextAttempt);
    }

    // ── private ─────────────────────────────────────────────────────────────

    private void doXadd(String taskType, String taskId, String payloadJson, int attempt, long scheduledAt) {
        Map<String, String> fields = Map.of(
                "taskId", taskId,
                "taskType", taskType,
                "payload", payloadJson,
                "attempt", String.valueOf(attempt),
                "scheduledAt", String.valueOf(scheduledAt)
        );

        String streamKey = properties.getStreamKey();
        StreamOperations<String, Object, Object> ops = redisTemplate.opsForStream();
        RecordId messageId = ops.add(
                StreamRecords.newRecord()
                        .in(streamKey)
                        .ofMap(fields)
        );

        log.debug("XADD {} {} taskId={} attempt={} messageId={}",
                streamKey, taskType, taskId, attempt, messageId);
    }
}
