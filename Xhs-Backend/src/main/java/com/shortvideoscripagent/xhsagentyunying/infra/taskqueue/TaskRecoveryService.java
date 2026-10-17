package com.shortvideoscripagent.xhsagentyunying.infra.taskqueue;

import com.shortvideoscripagent.xhsagentyunying.config.TaskQueueProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

/**
 * P0-1: 任务恢复服务。
 * <p>
 * 职责：
 * <ul>
 *   <li>应用启动时（ApplicationReadyEvent）接管孤儿任务</li>
 *   <li>定时扫描（每 60s）心跳过期的 running 任务，重新入队</li>
 * </ul>
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.task-queue.enabled", havingValue = "true", matchIfMissing = false)
public class TaskRecoveryService {

    private static final String HEARTBEAT_KEY_PREFIX = "xhs:tasks:heartbeat:";

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TaskQueueProperties properties;

    @Autowired
    private DeadLetterQueue deadLetterQueue;

    /**
     * 应用就绪后触发恢复扫描。
     */
    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        log.info("TaskRecoveryService starting recovery scan on startup...");
        recoverOrphanTasks();
    }

    /**
     * 定时恢复扫描（每 60 秒）。
     */
    @Scheduled(fixedDelayString = "${app.task-queue.recovery-scan-interval-ms:60000}")
    public void scheduledRecovery() {
        recoverOrphanTasks();
    }

    /**
     * 恢复孤儿任务：查询数据库 status='running' 且心跳过期的任务，重新 XADD。
     */
    public void recoverOrphanTasks() {
        try {
            // 1. 恢复孤儿 running 任务
            List<OrphanTask> orphans = findOrphanRunningTasks();
            for (OrphanTask orphan : orphans) {
                try {
                    recoverTask(orphan);
                } catch (Exception ex) {
                    log.error("Failed to recover orphan task: taskId={}", orphan.taskId(), ex);
                }
            }

            // 2. 恢复卡住的 queued 任务（超过 10 分钟未被消费）
            List<OrphanTask> staleQueued = findStaleQueuedTasks();
            for (OrphanTask orphan : staleQueued) {
                try {
                    recoverTask(orphan);
                } catch (Exception ex) {
                    log.error("Failed to recover stale queued task: taskId={}", orphan.taskId(), ex);
                }
            }

            if (!orphans.isEmpty() || !staleQueued.isEmpty()) {
                log.info("Recovery scan complete: {} running orphans, {} stale queued",
                        orphans.size(), staleQueued.size());
            }
        } catch (Exception ex) {
            log.error("Recovery scan failed", ex);
        }
    }

    // ── private ─────────────────────────────────────────────────────────────

    private List<OrphanTask> findOrphanRunningTasks() {
        String sql = """
            SELECT id, COALESCE(idempotency_key, 'ANALYSIS') as task_type, retry_count
            FROM app.analysis_task
            WHERE status = 'running'
              AND worker_id IS NOT NULL
            LIMIT 100
            """;
        return jdbcTemplate.query(sql, (rs, rowNum) -> {
            String workerId = rs.getString("id");
            // 简化：直接用 worker_id 列
            return new OrphanTask(
                    rs.getString("id"),
                    rs.getString("task_type"),
                    null,
                    rs.getInt("retry_count")
            );
        }).stream()
                .filter(o -> {
                    String workerId = jdbcTemplate.queryForObject(
                            "SELECT worker_id FROM app.analysis_task WHERE id = ?",
                            String.class,
                            o.taskId()
                    );
                    return workerId == null || !isWorkerAlive(workerId);
                })
                .toList();
    }

    private List<OrphanTask> findStaleQueuedTasks() {
        // 10 分钟以上未开始执行的 queued 任务
        String sql = """
            SELECT id, COALESCE(idempotency_key, 'ANALYSIS') as task_type, retry_count
            FROM app.analysis_task
            WHERE status = 'queued'
              AND created_at < NOW() - INTERVAL '10 minutes'
            LIMIT 100
            """;
        return jdbcTemplate.query(sql, (rs, rowNum) -> new OrphanTask(
                rs.getString("id"),
                rs.getString("task_type"),
                null,
                rs.getInt("retry_count")
        ));
    }

    private void recoverTask(OrphanTask orphan) {
        String taskId = orphan.taskId();
        int retryCount = orphan.retryCount();

        // 重置任务状态为 queued，清除 worker_id
        int updated = jdbcTemplate.update(
                """
                UPDATE app.analysis_task
                SET status = 'queued',
                    worker_id = NULL,
                    retry_count = ?,
                    updated_at = NOW()
                WHERE id = ? AND status IN ('running', 'queued')
                """,
                retryCount, taskId
        );

        if (updated == 0) {
            log.debug("Task {} already processed, skipping recovery", taskId);
            return;
        }

        // 重新 XADD
        long scheduledAt = System.currentTimeMillis();
        Map<String, String> fields = Map.of(
                "taskId", taskId,
                "taskType", orphan.taskType(),
                "payload", orphan.payload() != null ? orphan.payload() : "{}",
                "attempt", String.valueOf(retryCount + 1),
                "scheduledAt", String.valueOf(scheduledAt)
        );
        redisTemplate.opsForStream().add(
                StreamRecords.newRecord()
                        .in(properties.getStreamKey())
                        .ofMap(fields)
        );

        log.info("Recovered orphan task: taskId={} taskType={}", taskId, orphan.taskType());
    }

    private boolean isWorkerAlive(String workerId) {
        if (workerId == null || workerId.isBlank()) {
            return false;
        }
        String key = HEARTBEAT_KEY_PREFIX + workerId;
        return Boolean.TRUE.equals(redisTemplate.hasKey(key));
    }

    private record OrphanTask(
            String taskId,
            String taskType,
            String payload,
            int retryCount
    ) {}
}
