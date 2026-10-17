package com.shortvideoscripagent.xhsagentyunying.infra.taskqueue.handler;

import com.shortvideoscripagent.xhsagentyunying.ai.AiRuntimePolicy;
import com.shortvideoscripagent.xhsagentyunying.common.exception.BusinessException;
import com.shortvideoscripagent.xhsagentyunying.config.AppAiProperties;
import com.shortvideoscripagent.xhsagentyunying.config.TaskQueueProperties;
import com.shortvideoscripagent.xhsagentyunying.domain.entity.AnalysisTask;
import com.shortvideoscripagent.xhsagentyunying.domain.mapper.AnalysisTaskMapper;
import com.shortvideoscripagent.xhsagentyunying.infra.taskqueue.TaskHandler;
import com.shortvideoscripagent.xhsagentyunying.service.analysis.AnalysisProgressEvent;
import com.shortvideoscripagent.xhsagentyunying.service.analysis.AnalysisStreamHub;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;

/**
 * P0-1: 标题生成任务处理器。
 * <p>
 * 处理类型：TITLE_GENERATION
 * </p>
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "app.task-queue.enabled", havingValue = "true", matchIfMissing = false)
public class TitleTaskHandler implements TaskHandler {

    @Autowired
    private AnalysisTaskMapper taskMapper;
    @Autowired
    private AppAiProperties appProperties;
    @Autowired
    private AiRuntimePolicy aiRuntimePolicy;
    @Autowired
    private AnalysisStreamHub analysisStreamHub;
    @Autowired
    private TaskQueueProperties taskQueueProperties;
    @Autowired
    private TransactionTemplate transactionTemplate;

    @Override
    public String taskType() {
        return "TITLE_GENERATION";
    }

    @Override
    public void handle(String taskId, Map<String, Object> payload) throws Exception {
        log.info("TitleTaskHandler: taskId={}", taskId);
        long startedAt = System.currentTimeMillis();
        String effectiveTaskId = payload.get("analysisId") != null ? String.valueOf(payload.get("analysisId")) : taskId;
        String workerId = taskQueueProperties.getWorkerId();

        final String finalWorkerId = workerId;
        transactionTemplate.executeWithoutResult(status -> {
            int updated = taskMapper.update(null,
                    new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<AnalysisTask>()
                            .eq(AnalysisTask::getId, effectiveTaskId)
                            .in(AnalysisTask::getStatus, List.of("pending", "queued"))
                            .set(AnalysisTask::getStatus, "running")
                            .set(AnalysisTask::getStartedAt, OffsetDateTime.now())
                            .set(AnalysisTask::getWorkerId, finalWorkerId)
                            .set(AnalysisTask::getUpdatedAt, OffsetDateTime.now())
            );
            if (updated == 0) {
                log.warn("Task {} not in expected state", effectiveTaskId);
                status.setRollbackOnly();
            }
        });

        try {
            int processingMs = (int) (System.currentTimeMillis() - startedAt);
            transactionTemplate.executeWithoutResult(txStatus -> {
                taskMapper.update(null,
                        new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<AnalysisTask>()
                                .eq(AnalysisTask::getId, effectiveTaskId)
                                .set(AnalysisTask::getStatus, "completed")
                                .set(AnalysisTask::getProcessingMs, processingMs)
                                .set(AnalysisTask::getFinishedAt, OffsetDateTime.now())
                                .set(AnalysisTask::getUpdatedAt, OffsetDateTime.now())
                );
            });
            publishProgress(effectiveTaskId, "completed", "finished", "标题生成完成", null, null, processingMs);
            log.info("Title task completed: {} in {}ms", effectiveTaskId, processingMs);
        } catch (Exception ex) {
            log.error("Title task failed: {}", effectiveTaskId, ex);
            String reason = mapFailureReason(ex);
            int code = extractErrorCode(ex);
            transactionTemplate.executeWithoutResult(txStatus -> {
                taskMapper.update(null,
                        new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<AnalysisTask>()
                                .eq(AnalysisTask::getId, effectiveTaskId)
                                .set(AnalysisTask::getStatus, "failed")
                                .set(AnalysisTask::getFailureReason, reason)
                                .set(AnalysisTask::getFailureCode, code)
                                .set(AnalysisTask::getFinishedAt, OffsetDateTime.now())
                                .set(AnalysisTask::getUpdatedAt, OffsetDateTime.now())
                );
            });
            publishProgress(effectiveTaskId, "failed", "failed", "标题生成失败", code, reason, null);
            throw ex;
        }
    }

    private void publishProgress(String taskId, String status, String phase, String message,
                                Integer failureCode, String failureReason, Integer processingMs) {
        try {
            analysisStreamHub.publish(AnalysisProgressEvent.builder()
                    .taskId(taskId)
                    .status(status)
                    .phase(phase)
                    .message(message)
                    .processingMs(processingMs)
                    .failureCode(failureCode)
                    .failureReason(failureReason)
                    .build());
        } catch (Exception ex) {
            log.debug("Failed to publish SSE progress for task {}: {}", taskId, ex.getMessage());
        }
    }

    private String mapFailureReason(Exception ex) {
        if (ex instanceof BusinessException businessException) {
            return switch (businessException.getCode()) {
                case 50401 -> "timeout";
                case 50003 -> "ai_error";
                default -> "unknown";
            };
        }
        return ex.getCause() instanceof TimeoutException ? "timeout" : "ai_error";
    }

    private int extractErrorCode(Exception ex) {
        if (ex instanceof BusinessException be) {
            return be.getCode();
        }
        return ex.getCause() instanceof TimeoutException ? 50401 : 50002;
    }
}
