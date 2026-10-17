package com.shortvideoscripagent.xhsagentyunying.infra.taskqueue.handler;

import com.shortvideoscripagent.xhsagentyunying.ai.cover.CoverAnalysisService;
import com.shortvideoscripagent.xhsagentyunying.common.exception.BusinessException;
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
 * P0-1: 封面分析任务处理器。
 * <p>
 * 处理类型：COVER_ANALYSIS
 * </p>
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "app.task-queue.enabled", havingValue = "true", matchIfMissing = false)
public class CoverTaskHandler implements TaskHandler {

    @Autowired
    private AnalysisTaskMapper taskMapper;
    @Autowired
    private CoverAnalysisService coverAnalysisService;
    @Autowired
    private AnalysisStreamHub analysisStreamHub;
    @Autowired
    private TaskQueueProperties taskQueueProperties;
    @Autowired
    private TransactionTemplate transactionTemplate;

    @Override
    public String taskType() {
        return "COVER_ANALYSIS";
    }

    @Override
    public void handle(String taskId, Map<String, Object> payload) throws Exception {
        log.info("CoverTaskHandler: taskId={}", taskId);
        long startedAt = System.currentTimeMillis();
        String workerId = taskQueueProperties.getWorkerId();

        final String finalWorkerId = workerId;
        transactionTemplate.executeWithoutResult(status -> {
            int updated = taskMapper.update(null,
                    new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<AnalysisTask>()
                            .eq(AnalysisTask::getId, taskId)
                            .in(AnalysisTask::getStatus, List.of("pending", "queued"))
                            .set(AnalysisTask::getStatus, "running")
                            .set(AnalysisTask::getStartedAt, OffsetDateTime.now())
                            .set(AnalysisTask::getWorkerId, finalWorkerId)
                            .set(AnalysisTask::getUpdatedAt, OffsetDateTime.now())
            );
            if (updated == 0) {
                log.warn("Cover task {} not in expected state", taskId);
                status.setRollbackOnly();
            }
        });

        try {
            String coverUrl = payload.get("coverImageUrl") != null ? String.valueOf(payload.get("coverImageUrl")) : null;
            coverAnalysisService.analyzeFromPayload(coverUrl);

            int processingMs = (int) (System.currentTimeMillis() - startedAt);
            transactionTemplate.executeWithoutResult(txStatus -> {
                taskMapper.update(null,
                        new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<AnalysisTask>()
                                .eq(AnalysisTask::getId, taskId)
                                .set(AnalysisTask::getStatus, "completed")
                                .set(AnalysisTask::getProcessingMs, processingMs)
                                .set(AnalysisTask::getFinishedAt, OffsetDateTime.now())
                                .set(AnalysisTask::getUpdatedAt, OffsetDateTime.now())
                );
            });
            publishProgress(taskId, "completed", "finished", "封面分析完成", null, null, processingMs);
            log.info("Cover task completed: {} in {}ms", taskId, processingMs);
        } catch (Exception ex) {
            log.error("Cover task failed: {}", taskId, ex);
            String reason = mapFailureReason(ex);
            int code = extractErrorCode(ex);
            int processingMs = (int) (System.currentTimeMillis() - startedAt);
            transactionTemplate.executeWithoutResult(txStatus -> {
                taskMapper.update(null,
                        new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<AnalysisTask>()
                                .eq(AnalysisTask::getId, taskId)
                                .set(AnalysisTask::getStatus, "failed")
                                .set(AnalysisTask::getFailureReason, reason)
                                .set(AnalysisTask::getFailureCode, code)
                                .set(AnalysisTask::getFinishedAt, OffsetDateTime.now())
                                .set(AnalysisTask::getUpdatedAt, OffsetDateTime.now())
                );
            });
            publishProgress(taskId, "failed", "failed", "封面分析失败", code, reason, processingMs);
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
