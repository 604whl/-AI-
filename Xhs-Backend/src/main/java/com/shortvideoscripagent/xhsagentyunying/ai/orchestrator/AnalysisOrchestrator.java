package com.shortvideoscripagent.xhsagentyunying.ai.orchestrator;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shortvideoscripagent.xhsagentyunying.ai.AiRuntimePolicy;
import com.shortvideoscripagent.xhsagentyunying.ai.cover.CoverAnalysisService;
import com.shortvideoscripagent.xhsagentyunying.ai.fixture.SampleAnalysisReport;
import com.shortvideoscripagent.xhsagentyunying.ai.model.ModelProviderRegistry;
import com.shortvideoscripagent.xhsagentyunying.ai.parser.JsonReportParser;
import com.shortvideoscripagent.xhsagentyunying.ai.prompt.PromptEngine;
import com.shortvideoscripagent.xhsagentyunying.ai.rag.RagChunk;
import com.shortvideoscripagent.xhsagentyunying.ai.rag.RagContextBuilder;
import com.shortvideoscripagent.xhsagentyunying.ai.rag.RagQuery;
import com.shortvideoscripagent.xhsagentyunying.ai.rag.RagRetriever;
import com.shortvideoscripagent.xhsagentyunying.common.exception.BusinessException;
import com.shortvideoscripagent.xhsagentyunying.config.AppAiProperties;
import com.shortvideoscripagent.xhsagentyunying.config.TaskQueueProperties;
import com.shortvideoscripagent.xhsagentyunying.domain.compliance.ComplianceChecker;
import com.shortvideoscripagent.xhsagentyunying.domain.entity.AnalysisReport;
import com.shortvideoscripagent.xhsagentyunying.domain.entity.AnalysisTask;
import com.shortvideoscripagent.xhsagentyunying.domain.mapper.AnalysisReportMapper;
import com.shortvideoscripagent.xhsagentyunying.domain.mapper.AnalysisTaskMapper;
import com.shortvideoscripagent.xhsagentyunying.infra.taskqueue.TaskQueueProducer;
import com.shortvideoscripagent.xhsagentyunying.infra.taskqueue.payload.AnalysisTaskPayload;
import com.shortvideoscripagent.xhsagentyunying.service.analysis.AnalysisProgressEvent;
import com.shortvideoscripagent.xhsagentyunying.service.analysis.AnalysisStreamHub;
import jakarta.annotation.Resource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Slf4j
@Component
@RequiredArgsConstructor
public class AnalysisOrchestrator {

    private final AnalysisTaskMapper taskMapper;
    private final AnalysisReportMapper reportMapper;
    private final ObjectMapper objectMapper;
    private final ModelProviderRegistry modelProviderRegistry;
    private final PromptEngine promptEngine;
    private final JsonReportParser jsonReportParser;
    private final ComplianceChecker complianceChecker;
    private final RagRetriever ragRetriever;
    private final RagContextBuilder ragContextBuilder;
    private final CoverAnalysisService coverAnalysisService;
    private final AppAiProperties appProperties;
    private final AiRuntimePolicy aiRuntimePolicy;
    private final AnalysisStreamHub analysisStreamHub;

    // P0-1: 可选的任务队列（条件注入）
    @Autowired(required = false)
    private TaskQueueProducer taskQueueProducer;

    @Autowired(required = false)
    private TaskQueueProperties taskQueueProperties;

    @Resource(name = "aiExecutor")
    private Executor aiExecutor;

    /**
     * P0-1: 任务调度入口。
     * 根据配置选择 Redis Streams 队列或旧的 @Async 模式。
     */
    public void analyzeAsync(String taskId) {
        if (taskQueueProperties != null && taskQueueProperties.isEnabled() && taskQueueProducer != null) {
            // Redis Streams 模式：构建 payload 并投递
            AnalysisTask task = taskMapper.selectById(taskId);
            if (task != null) {
                try {
                    AnalysisTaskPayload payload = toPayload(task);
                    String payloadJson = objectMapper.writeValueAsString(payload);
                    taskQueueProducer.enqueue("ANALYSIS", taskId, payloadJson, 1);
                    // 更新状态为 queued
                    task.setStatus("queued");
                    task.setUpdatedAt(OffsetDateTime.now());
                    taskMapper.updateById(task);
                } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
                    log.error("Failed to serialize payload for task {}", taskId, ex);
                    throw new BusinessException(50002, "payload_serialization_failed");
                }
            }
        } else {
            // 旧的 @Async 模式
            analyzeAsyncDirect(taskId);
        }
    }

    private AnalysisTaskPayload toPayload(AnalysisTask task) {
        AnalysisTaskPayload p = new AnalysisTaskPayload();
        p.setTaskId(task.getId());
        p.setScenario(task.getScenario());
        p.setPersona(task.getPersona());
        p.setTitle(task.getTitle());
        p.setBody(task.getBody());
        p.setCoverImageUrl(task.getCoverImageUrl());
        p.setPublishedMetrics(task.getPublishedMetrics());
        p.setCompetitorContext(task.getCompetitorContext());
        p.setPromptVersion(task.getPromptVersion());
        p.setModelProvider(task.getModelProvider());
        p.setModelName(task.getModelName());
        return p;
    }

    /**
     * 旧的直接 @Async 模式（默认，兼容未启用 task-queue 时）。
     */
    @Async("analysisExecutor")
    public void analyzeAsyncDirect(String taskId) {
        log.info("Analysis task queued: {}", taskId);
        long startedAt = System.currentTimeMillis();

        AnalysisTask task = taskMapper.selectById(taskId);
        if (task == null) {
            log.warn("Analysis task not found: {}", taskId);
            return;
        }

        try {
            task.setStatus("processing");
            task.setUpdatedAt(OffsetDateTime.now());
            taskMapper.updateById(task);
            publishProgress(task, "processing", "started", "分析任务已开始");

            AnalysisResult result = runAnalysis(task);
            String reportJson = objectMapper.writeValueAsString(result.report());
            String coverAnalysisJson = result.coverAnalysis() == null
                    ? null
                    : objectMapper.writeValueAsString(result.coverAnalysis());
            List<?> warnings = (List<?>) result.report().getOrDefault("complianceWarnings", List.of());

            AnalysisReport analysisReport = new AnalysisReport();
            analysisReport.setTaskId(taskId);
            analysisReport.setReportJson(reportJson);
            analysisReport.setCoverAnalysis(coverAnalysisJson);
            analysisReport.setComplianceWarnings(objectMapper.writeValueAsString(warnings));
            analysisReport.setCreatedAt(OffsetDateTime.now());
            reportMapper.insertJsonb(analysisReport);

            task.setStatus("completed");
            task.setProcessingMs((int) (System.currentTimeMillis() - startedAt));
            task.setUpdatedAt(OffsetDateTime.now());
            taskMapper.updateById(task);
            publishProgress(task, "completed", "finished", "分析完成");

            log.info("Analysis task completed: {} in {}ms", taskId, task.getProcessingMs());
        } catch (Exception ex) {
            log.error("Analysis task failed: {}", taskId, ex);
            if (ex.getCause() instanceof TimeoutException || ex instanceof TimeoutException) {
                markFailed(task, "timeout", 50401);
            } else if (ex instanceof BusinessException businessException) {
                markFailed(task, mapFailureReason(businessException.getCode()), businessException.getCode());
            } else {
                markFailed(task, "ai_error", 50002);
            }
        }
    }

    private AnalysisResult runAnalysis(AnalysisTask task) throws Exception {
        if (aiRuntimePolicy.useMockResponses()) {
            log.info("Using mock analysis for task {}", task.getId());
            publishProgress(task, "processing", "text_analysis", "正在分析正文（Mock）");
            Thread.sleep(400);
            publishProgress(task, "processing", "cover_analysis", "正在分析封面（Mock）");
            Thread.sleep(400);
            Map<String, Object> report = complianceChecker.mergeIntoReport(
                    SampleAnalysisReport.build(task),
                    task.getTitle(),
                    task.getBody()
            );
            Map<String, Object> coverAnalysis = hasCover(task)
                    ? SampleAnalysisReport.buildCoverAnalysis(task.getTitle(), task.getBody())
                    : CoverAnalysisService.unavailable();
            coverAnalysisService.mergeCtrInsight(report, coverAnalysis);
            return new AnalysisResult(report, coverAnalysis);
        }

        aiRuntimePolicy.assertRealAiAvailable();

        int timeoutSeconds = appProperties.getAi().getAnalysisTimeoutSeconds();

        if (appProperties.getRag().isEnabled() && appProperties.getRag().isAnalysisEnabled()) {
            publishProgress(task, "processing", "rag", "正在检索知识库案例");
        }
        publishProgress(task, "processing", "text_analysis", "正在分析正文");
        publishProgress(task, "processing", "cover_analysis", "正在分析封面");

        CompletableFuture<Map<String, Object>> reportFuture = CompletableFuture.supplyAsync(
                () -> runTextAnalysis(task),
                aiExecutor
        );
        CompletableFuture<Map<String, Object>> coverFuture = CompletableFuture.supplyAsync(
                () -> coverAnalysisService.analyze(task),
                aiExecutor
        );

        Map<String, Object> report = reportFuture.orTimeout(timeoutSeconds, TimeUnit.SECONDS).join();
        Map<String, Object> coverAnalysis = coverFuture.orTimeout(timeoutSeconds, TimeUnit.SECONDS).join();

        publishProgress(task, "processing", "saving", "正在生成报告");
        coverAnalysisService.mergeCtrInsight(report, coverAnalysis);
        return new AnalysisResult(report, coverAnalysis);
    }

    private Map<String, Object> runTextAnalysis(AnalysisTask task) {
        String ragContext = buildRagContext(task);
        String systemPrompt = promptEngine.systemPrompt();
        String userPrompt = promptEngine.buildAnalysisUserPrompt(task, ragContext);
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
                Map<String, Object> report = jsonReportParser.parseAnalysisReport(raw, task.getScenario());
                task.setModelProvider(llm.providerId());
                task.setModelName(llm.modelName());
                taskMapper.updateById(task);
                return complianceChecker.mergeIntoReport(report, task.getTitle(), task.getBody());
            } catch (Exception ex) {
                lastError = ex;
                log.warn("Analysis LLM attempt {} failed for task {}: {}", attempt + 1, task.getId(), ex.getMessage());
            }
        }
        if (lastError instanceof BusinessException businessException) {
            throw businessException;
        }
        throw lastError == null ? new BusinessException(50002, "ai_service_unavailable") : new RuntimeException(lastError);
    }

    private static boolean hasCover(AnalysisTask task) {
        return task.getCoverImageUrl() != null && !task.getCoverImageUrl().isBlank();
    }

    private String buildRagContext(AnalysisTask task) {
        if (!appProperties.getRag().isEnabled() || !appProperties.getRag().isAnalysisEnabled()) {
            return "";
        }
        String query = ((task.getTitle() == null ? "" : task.getTitle()) + " "
                + (task.getBody() == null ? "" : task.getBody())).trim();
        if (query.isBlank()) {
            return "";
        }
        List<RagChunk> chunks = ragRetriever.retrieve(new RagQuery(
                query,
                List.of("viral_case"),
                null,
                task.getPersona(),
                appProperties.getRag().getTopK()
        ));
        return ragContextBuilder.build(chunks);
    }

    private void markFailed(AnalysisTask task, String reason, int code) {
        task.setStatus("failed");
        task.setFailureReason(reason);
        task.setFailureCode(code);
        task.setUpdatedAt(OffsetDateTime.now());
        taskMapper.updateById(task);
        publishProgress(task, "failed", "failed", "分析失败：" + reason);
    }

    private void publishProgress(AnalysisTask task, String status, String phase, String message) {
        analysisStreamHub.publish(AnalysisProgressEvent.builder()
                .taskId(task.getId())
                .status(status)
                .phase(phase)
                .message(message)
                .processingMs(task.getProcessingMs())
                .failureCode(task.getFailureCode())
                .failureReason(task.getFailureReason())
                .build());
    }

    private String mapFailureReason(int code) {
        return switch (code) {
            case 50401 -> "timeout";
            case 50003 -> "ai_error";
            default -> "unknown";
        };
    }

    private record AnalysisResult(Map<String, Object> report, Map<String, Object> coverAnalysis) {
    }
}
