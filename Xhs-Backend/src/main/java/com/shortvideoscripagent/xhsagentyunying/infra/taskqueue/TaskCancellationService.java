package com.shortvideoscripagent.xhsagentyunying.infra.taskqueue;

import com.shortvideoscripagent.xhsagentyunying.common.api.WorkspaceContext;
import com.shortvideoscripagent.xhsagentyunying.common.exception.BusinessException;
import com.shortvideoscripagent.xhsagentyunying.domain.entity.AnalysisTask;
import com.shortvideoscripagent.xhsagentyunying.domain.mapper.AnalysisTaskMapper;
import com.shortvideoscripagent.xhsagentyunying.infra.metrics.BusinessMetrics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.Set;

/**
 * 任务取消服务。
 * <p>
 * 取消策略：
 * <ol>
 *   <li>仅允许 {@code pending} / {@code running} / {@code queued} 状态的任务被取消</li>
 *   <li>用 SQL UPDATE 原子条件更新，避免与 worker 写竞态</li>
 *   <li>对 Redis Stream 写一个"取消信号" key，Worker 在调用 handler 前检查</li>
 *   <li>Handler 应在关键步骤前再次校验状态（防御性）</li>
 *   <li>如果任务在 DLQ 之前已完成，记录指标但不报错</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TaskCancellationService {

    private static final int CODE_NOT_FOUND = 40401;
    private static final int CODE_INVALID_STATE = 40901;

    private static final String CANCEL_KEY_PREFIX = "xhs:task:cancel:";

    /** 仅允许取消的源头状态 */
    private static final Set<String> CANCELLABLE = Set.of("pending", "running", "queued");

    private final AnalysisTaskMapper analysisTaskMapper;
    private final StringRedisTemplate redisTemplate;
    private final BusinessMetrics metrics;

    /**
     * 取消任务（仅任务所有者或 workspace 成员可调用）。
     *
     * @return 取消结果状态
     */
    @Transactional
    public CancelResult cancel(String taskId) {
        String workspaceId = WorkspaceContext.requireWorkspaceId();

        AnalysisTask task = analysisTaskMapper.selectById(taskId);
        if (task == null) {
            throw new BusinessException(CODE_NOT_FOUND, "task_not_found");
        }
        if (!workspaceId.equals(task.getWorkspaceId())) {
            // workspace 不一致，视为越权访问
            throw new BusinessException(CODE_NOT_FOUND, "task_not_found");
        }

        String currentStatus = task.getStatus();
        if (currentStatus == null) {
            throw new BusinessException(CODE_INVALID_STATE, "task_status_undefined");
        }

        if (!CANCELLABLE.contains(currentStatus)) {
            // 终态/已完成，不能取消
            throw new BusinessException(CODE_INVALID_STATE,
                    "task_not_cancellable_in_status: " + currentStatus);
        }

        // 1) 原子化 SQL 取消
        int rows = analysisTaskMapper.cancelIfActive(taskId, workspaceId);
        if (rows == 0) {
            // 并发：状态已被 worker 改写
            log.info("Cancel race: taskId={} status changed concurrently", taskId);
            return new CancelResult(taskId, "race_lost", currentStatus);
        }

        // 2) Redis 写"取消信号" key（worker 启动 handler 前会检查）
        try {
            redisTemplate.opsForValue().set(
                    CANCEL_KEY_PREFIX + taskId,
                    "1",
                    Duration.ofHours(1)
            );
        } catch (Exception ex) {
            log.warn("Failed to set cancel signal key for taskId={}: {}", taskId, ex.getMessage());
        }

        // 3) 指标
        metrics.taskCancelled(detectTaskType(task));

        log.info("Task cancelled: taskId={} priorStatus={} workspaceId={}", taskId, currentStatus, workspaceId);
        return new CancelResult(taskId, "cancelled", currentStatus);
    }

    /**
     * Worker 在执行 handler 前检查是否被取消；返回 true 表示已取消应中止。
     */
    public boolean isCancelled(String taskId) {
        try {
            Boolean exists = redisTemplate.hasKey(CANCEL_KEY_PREFIX + taskId);
            return Boolean.TRUE.equals(exists);
        } catch (Exception ex) {
            log.warn("Failed to read cancel signal for taskId={}: {}", taskId, ex.getMessage());
            return false;
        }
    }

    /**
     * 清理 Redis 取消信号（任务完成/失败/DLQ 时调用）。
     */
    public void clearSignal(String taskId) {
        try {
            redisTemplate.delete(CANCEL_KEY_PREFIX + taskId);
        } catch (Exception ex) {
            log.debug("Failed to clear cancel signal for taskId={}: {}", taskId, ex.getMessage());
        }
    }

    private String detectTaskType(AnalysisTask task) {
        if (task == null || task.getScenario() == null) {
            return "unknown";
        }
        return switch (task.getScenario()) {
            case "title" -> "title_generation";
            case "body" -> "body_generation";
            case "cover" -> "cover_analysis";
            case "compliance" -> "compliance_scan";
            default -> "analysis";
        };
    }

    public record CancelResult(String taskId, String result, String priorStatus) {
    }
}
