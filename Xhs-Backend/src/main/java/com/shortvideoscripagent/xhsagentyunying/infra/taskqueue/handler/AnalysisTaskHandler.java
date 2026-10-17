package com.shortvideoscripagent.xhsagentyunying.infra.taskqueue.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shortvideoscripagent.xhsagentyunying.ai.AiRuntimePolicy;
import com.shortvideoscripagent.xhsagentyunying.ai.cover.CoverAnalysisService;
import com.shortvideoscripagent.xhsagentyunying.ai.fixture.SampleAnalysisReport;
import com.shortvideoscripagent.xhsagentyunying.ai.model.ModelProviderRegistry;
import com.shortvideoscripagent.xhsagentyunying.ai.parser.JsonReportParser;
import com.shortvideoscripagent.xhsagentyunying.ai.prompt.PromptEngine;
import com.shortvideoscripagent.xhsagentyunying.common.exception.BusinessException;
import com.shortvideoscripagent.xhsagentyunying.config.AppAiProperties;
import com.shortvideoscripagent.xhsagentyunying.config.TaskQueueProperties;
import com.shortvideoscripagent.xhsagentyunying.domain.compliance.ComplianceChecker;
import com.shortvideoscripagent.xhsagentyunying.domain.entity.AnalysisReport;
import com.shortvideoscripagent.xhsagentyunying.domain.entity.AnalysisTask;
import com.shortvideoscripagent.xhsagentyunying.domain.mapper.AnalysisReportMapper;
import com.shortvideoscripagent.xhsagentyunying.domain.mapper.AnalysisTaskMapper;
import com.shortvideoscripagent.xhsagentyunying.infra.taskqueue.TaskHandler;
import com.shortvideoscripagent.xhsagentyunying.quota.AtomicQuotaGuard;
import com.shortvideoscripagent.xhsagentyunying.quota.QuotaCheckResult;
import com.shortvideoscripagent.xhsagentyunying.quota.QuotaExceededException;
import com.shortvideoscripagent.xhsagentyunying.quota.QuotaRefundService;
import com.shortvideoscripagent.xhsagentyunying.service.analysis.AnalysisProgressEvent;
import com.shortvideoscripagent.xhsagentyunying.service.analysis.AnalysisStreamHub;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * P0-1: 笔记分析任务处理器。
 * <p>
 * 处理类型：ANALYSIS
 * </p>
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "app.task-queue.enabled", havingValue = "true", matchIfMissing = false)
public class AnalysisTaskHandler implements TaskHandler {

    @Autowired
    private AnalysisTaskMapper taskMapper;
    @Autowired
    private AnalysisReportMapper reportMapper;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private ModelProviderRegistry modelProviderRegistry;
    @Autowired
    private PromptEngine promptEngine;
    @Autowired
    private JsonReportParser jsonReportParser;
    @Autowired
    private ComplianceChecker complianceChecker;
    @Autowired
    private CoverAnalysisService coverAnalysisService;
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
    @Autowired
    private AtomicQuotaGuard atomicQuotaGuard;
    @Autowired
    private QuotaRefundService quotaRefundService;

    @Resource(name = "aiExecutor")
    private Executor aiExecutor;

    @Override
    public String taskType() {
        return "ANALYSIS";
    }

    @Override
    public void handle(String taskId, Map<String, Object> payload) throws Exception {
        log.info("AnalysisTaskHandler: taskId={}", taskId);
        long startedAt = System.currentTimeMillis();
        String workerId = taskQueueProperties.getWorkerId();

        // P0-2: 从 payload 提取 workspaceId
        String workspaceIdStr = payload != null ? String.valueOf(payload.get("workspaceId")) : null;
        if (workspaceIdStr == null || workspaceIdStr.equals("null")) {
            log.error("workspaceId not found in payload for task {}", taskId);
            throw new IllegalStateException("workspaceId required in payload");
        }
        java.util.UUID workspaceId = java.util.UUID.fromString(workspaceIdStr);

        // P0-2: 进入 handler 时先扣减配额，失败直接返回（任务不会执行）
        java.util.UUID ledgerId;
        try {
            QuotaCheckResult consumeResult = atomicQuotaGuard.tryConsume(
                    workspaceId,
                    "ANALYSIS",
                    java.util.UUID.fromString(taskId),
                    "task-start"
            );
            if (!consumeResult.isOk()) {
                log.warn("Quota exceeded for workspace {} on task {}", workspaceId, taskId);
                // 标记任务为 quota_exceeded 状态
                transactionTemplate.executeWithoutResult(txStatus -> {
                    taskMapper.update(null,
                            new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<AnalysisTask>()
                                    .eq(AnalysisTask::getId, taskId)
                                    .in(AnalysisTask::getStatus, List.of("pending", "queued"))
                                    .set(AnalysisTask::getStatus, "failed")
                                    .set(AnalysisTask::getFailureReason, "quota_exceeded")
                                    .set(AnalysisTask::getFailureCode, 42901)
                                    .set(AnalysisTask::getFinishedAt, OffsetDateTime.now())
                                    .set(AnalysisTask::getUpdatedAt, OffsetDateTime.now())
                    );
                });
                publishProgress(taskId, "failed", "failed", "配额不足", 42901, "quota_exceeded", null);
                throw new QuotaExceededException("quota_exceeded");
            }
            ledgerId = consumeResult.ledgerId();
        } catch (QuotaExceededException ex) {
            throw ex; // 直接传递
        } catch (Exception ex) {
            log.warn("Unexpected error during quota consume for task {}: {}", taskId, ex.getMessage());
            ledgerId = null;
        }

        // 1. 状态更新为 running（事务中）
        final String finalWorkerId = workerId;
        final java.util.UUID finalLedgerId = ledgerId;
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
                log.warn("Task {} not in expected state, may be processed by another worker", taskId);
                status.setRollbackOnly();
            }
        });

        try {
            // 2. 执行分析（使用 payload 构建 AnalysisTask 对象）
            AnalysisResult result = runAnalysis(taskId, payload);
            String reportJson = objectMapper.writeValueAsString(result.report());
            String coverAnalysisJson = result.coverAnalysis() == null
                    ? null
                    : objectMapper.writeValueAsString(result.coverAnalysis());
            @SuppressWarnings("unchecked")
            List<?> warnings = (List<?>) result.report().getOrDefault("complianceWarnings", List.of());

            // 3. 保存报告 + 更新状态为 completed（事务中）
            int processingMs = (int) (System.currentTimeMillis() - startedAt);
            transactionTemplate.executeWithoutResult(txStatus -> {
                AnalysisReport analysisReport = new AnalysisReport();
                analysisReport.setTaskId(taskId);
                analysisReport.setReportJson(reportJson);
                analysisReport.setCoverAnalysis(coverAnalysisJson);
                try {
                    analysisReport.setComplianceWarnings(objectMapper.writeValueAsString(warnings));
                } catch (Exception e) {
                    analysisReport.setComplianceWarnings("[]");
                }
                analysisReport.setCreatedAt(OffsetDateTime.now());
                reportMapper.insertJsonb(analysisReport);

                taskMapper.update(null,
                        new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<AnalysisTask>()
                                .eq(AnalysisTask::getId, taskId)
                                .set(AnalysisTask::getStatus, "completed")
                                .set(AnalysisTask::getProcessingMs, processingMs)
                                .set(AnalysisTask::getFinishedAt, OffsetDateTime.now())
                                .set(AnalysisTask::getUpdatedAt, OffsetDateTime.now())
                );
            });

            publishProgress(taskId, "completed", "finished", "分析完成", null, null, processingMs);
            log.info("Analysis task completed: {} in {}ms (quota consumed ledgerId={})", taskId, processingMs, finalLedgerId);

        } catch (Exception ex) {
            log.error("Analysis task failed: {}", taskId, ex);
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
            publishProgress(taskId, "failed", "failed", "分析失败：" + reason, code, reason, processingMs);

            // P0-2: 任务失败时自动冲正配额
            if (finalLedgerId != null) {
                try {
                    quotaRefundService.refund(finalLedgerId, "task-failed:" + reason);
                } catch (Exception refundEx) {
                    log.warn("Failed to refund quota for task {}: {}", taskId, refundEx.getMessage());
                }
            }
            throw ex;
        }
    }

    private AnalysisResult runAnalysis(String taskId, Map<String, Object> payload) throws Exception {
        if (aiRuntimePolicy.useMockResponses()) {
            log.info("Using mock analysis for task {}", taskId);
            publishProgress(taskId, "processing", "text_analysis", "正在分析正文（Mock）", null, null, null);
            Thread.sleep(400);
            publishProgress(taskId, "processing", "cover_analysis", "正在分析封面（Mock）", null, null, null);
            Thread.sleep(400);

            Map<String, Object> report = complianceChecker.mergeIntoReport(
                    SampleAnalysisReport.buildFromPayload(payload),
                    String.valueOf(payload.getOrDefault("title", "")),
                    String.valueOf(payload.getOrDefault("body", ""))
            );
            boolean hasCover = payload.get("coverImageUrl") != null && !String.valueOf(payload.get("coverImageUrl")).isBlank();
            Map<String, Object> coverAnalysis = hasCover
                    ? SampleAnalysisReport.buildCoverAnalysis(
                            String.valueOf(payload.getOrDefault("title", "")),
                            String.valueOf(payload.getOrDefault("body", "")))
                    : CoverAnalysisService.unavailable();
            coverAnalysisService.mergeCtrInsight(report, coverAnalysis);
            return new AnalysisResult(report, coverAnalysis);
        }

        aiRuntimePolicy.assertRealAiAvailable();
        int timeoutSeconds = appProperties.getAi().getAnalysisTimeoutSeconds();

        publishProgress(taskId, "processing", "text_analysis", "正在分析正文", null, null, null);
        publishProgress(taskId, "processing", "cover_analysis", "正在分析封面", null, null, null);

        CompletableFuture<Map<String, Object>> reportFuture = CompletableFuture.supplyAsync(
                () -> runTextAnalysis(taskId, payload), aiExecutor
        );
        CompletableFuture<Map<String, Object>> coverFuture = CompletableFuture.supplyAsync(
                () -> coverAnalysisService.analyzeFromPayload(
                        payload.get("coverImageUrl") != null ? String.valueOf(payload.get("coverImageUrl")) : null),
                aiExecutor
        );

        Map<String, Object> report = reportFuture.orTimeout(timeoutSeconds, TimeUnit.SECONDS).join();
        Map<String, Object> coverAnalysis = coverFuture.orTimeout(timeoutSeconds, TimeUnit.SECONDS).join();

        coverAnalysisService.mergeCtrInsight(report, coverAnalysis);
        return new AnalysisResult(report, coverAnalysis);
    }

    private Map<String, Object> runTextAnalysis(String taskId, Map<String, Object> payload) {
        String ragContext = "";
        String systemPrompt = promptEngine.systemPrompt();
        String userPrompt = promptEngine.buildAnalysisUserPromptFromPayload(payload, ragContext);
        int timeoutSeconds = appProperties.getAi().getAnalysisTimeoutSeconds();
        int maxRetries = appProperties.getAi().getAnalysisMaxRetries();

        Exception lastError = null;
        for (int attempt = 0; attempt <= maxRetries; attempt++) {
            try {
                ModelProviderRegistry.ChatResult llm = CompletableFuture
                        .supplyAsync(() -> modelProviderRegistry.chatWithFallback(systemPrompt, userPrompt), aiExecutor)
                        .orTimeout(timeoutSeconds, TimeUnit.SECONDS)
                        .join();
                String raw = llm.content();
                return jsonReportParser.parseAnalysisReport(raw, String.valueOf(payload.getOrDefault("scenario", "agency")));
            } catch (Exception ex) {
                lastError = ex;
                log.warn("Analysis LLM attempt {} failed for task {}: {}", attempt + 1, taskId, ex.getMessage());
            }
        }
        if (lastError instanceof BusinessException businessException) {
            throw businessException;
        }
        throw lastError == null ? new BusinessException(50002, "ai_service_unavailable") : new RuntimeException(lastError);
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
        if (ex.getCause() instanceof TimeoutException || ex instanceof TimeoutException) {
            return "timeout";
        }
        if (ex instanceof BusinessException businessException) {
            return switch (businessException.getCode()) {
                case 50401 -> "timeout";
                case 50003 -> "ai_error";
                default -> "unknown";
            };
        }
        return "ai_error";
    }

    private int extractErrorCode(Exception ex) {
        if (ex instanceof BusinessException be) {
            return be.getCode();
        }
        if (ex.getCause() instanceof TimeoutException) {
            return 50401;
        }
        return 50002;
    }

    private record AnalysisResult(Map<String, Object> report, Map<String, Object> coverAnalysis) {}
}
