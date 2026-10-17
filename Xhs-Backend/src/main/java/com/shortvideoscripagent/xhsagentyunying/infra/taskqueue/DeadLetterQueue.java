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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;

/**
 * P0-1: 死信队列（Dead Letter Queue）。
 * <p>
 * 当任务重试超过 {@link TaskQueueProperties#getMaxRetry()} 次后，
 * 任务被移入 DLQ（同时写入 Redis Stream 和数据库表 app.task_dlq）。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.task-queue.enabled", havingValue = "true", matchIfMissing = false)
public class DeadLetterQueue {

    private final StringRedisTemplate redisTemplate;
    private final JdbcTemplate jdbcTemplate;
    private final TaskQueueProperties properties;

    /**
     * 将失败任务移入死信队列。
     *
     * @param taskId       数据库任务 ID
     * @param taskType     任务类型
     * @param payload      原始载荷（JSON）
     * @param errorMessage 错误信息
     * @param errorCode    错误码
     * @param retryCount   实际重试次数
     */
    public void enqueue(String taskId, String taskType, String payload,
                        String errorMessage, Integer errorCode, int retryCount) {
        long failedAt = System.currentTimeMillis();

        // 1. XADD 到 Redis DLQ Stream
        Map<String, String> fields = Map.of(
                "originalTaskId", taskId,
                "taskType", taskType,
                "payload", payload,
                "errorMessage", errorMessage != null ? errorMessage : "",
                "errorCode", errorCode != null ? String.valueOf(errorCode) : "",
                "retryCount", String.valueOf(retryCount),
                "failedAt", String.valueOf(failedAt)
        );

        String dlqKey = properties.getDlqKey();
        StreamOperations<String, Object, Object> ops = redisTemplate.opsForStream();
        RecordId messageId = ops.add(
                StreamRecords.newRecord()
                        .in(dlqKey)
                        .ofMap(fields)
        );

        log.info("DLQ enqueued: taskId={} taskType={} retryCount={} messageId={}",
                taskId, taskType, retryCount, messageId);

        // 2. 写入数据库 app.task_dlq
        OffsetDateTime failedAtTs = OffsetDateTime.ofInstant(
                java.time.Instant.ofEpochMilli(failedAt), ZoneOffset.UTC);
        jdbcTemplate.update(
                """
                INSERT INTO app.task_dlq (original_task_id, task_type, payload, error_message, error_code, retry_count, failed_at)
                VALUES (?, ?, ?::jsonb, ?, ?, ?, ?)
                """,
                taskId, taskType, payload, errorMessage, errorCode, retryCount, failedAtTs
        );
    }

    /**
     * 从 DLQ 重新拉回并重试（人工触发）。
     *
     * @param dlqId      DLQ 记录 ID
     * @param taskId     原始任务 ID
     * @param taskType   任务类型
     * @param payload    原始载荷
     * @param retryCount 重试次数（重置为 0）
     */
    public void retryFromDlq(Long dlqId, String taskId, String taskType, String payload, int retryCount) {
        // 从 DLQ 表中删除
        if (dlqId != null) {
            jdbcTemplate.update("DELETE FROM app.task_dlq WHERE id = ?", dlqId);
        }

        // 将 analysis_task 状态重置为 queued
        jdbcTemplate.update(
                """
                UPDATE app.analysis_task
                SET status = 'queued',
                    retry_count = ?,
                    dead_letter_at = NULL,
                    updated_at = NOW()
                WHERE id = ?
                """,
                retryCount, taskId
        );

        // 重新 XADD 到主 Stream
        long scheduledAt = System.currentTimeMillis();
        Map<String, String> fields = Map.of(
                "taskId", taskId,
                "taskType", taskType,
                "payload", payload,
                "attempt", String.valueOf(retryCount + 1),
                "scheduledAt", String.valueOf(scheduledAt)
        );
        redisTemplate.opsForStream().add(
                StreamRecords.newRecord()
                        .in(properties.getStreamKey())
                        .ofMap(fields)
        );

        log.info("DLQ retry triggered: taskId={} taskType={}", taskId, taskType);
    }
}
