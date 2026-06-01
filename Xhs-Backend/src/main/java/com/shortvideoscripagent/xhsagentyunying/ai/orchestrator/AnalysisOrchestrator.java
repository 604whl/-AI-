package com.shortvideoscripagent.xhsagentyunying.ai.orchestrator;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shortvideoscripagent.xhsagentyunying.ai.AiRuntimePolicy;
import com.shortvideoscripagent.xhsagentyunying.ai.cover.CoverAnalysisService;
import com.shortvideoscripagent.xhsagentyunying.ai.fixture.SampleAnalysisReport;
import com.shortvideoscripagent.xhsagentyunying.ai.model.ModelProvider;
import com.shortvideoscripagent.xhsagentyunying.ai.model.ModelProviderRegistry;
import com.shortvideoscripagent.xhsagentyunying.ai.parser.JsonReportParser;
import com.shortvideoscripagent.xhsagentyunying.ai.prompt.PromptEngine;
import com.shortvideoscripagent.xhsagentyunying.ai.rag.RagChunk;
import com.shortvideoscripagent.xhsagentyunying.ai.rag.RagContextBuilder;
import com.shortvideoscripagent.xhsagentyunying.ai.rag.RagQuery;
import com.shortvideoscripagent.xhsagentyunying.ai.rag.RagRetriever;
import com.shortvideoscripagent.xhsagentyunying.common.exception.BusinessException;
import com.shortvideoscripagent.xhsagentyunying.config.AiRuntimeProperties;
import com.shortvideoscripagent.xhsagentyunying.config.AppAiProperties;
import com.shortvideoscripagent.xhsagentyunying.domain.compliance.ComplianceChecker;
import com.shortvideoscripagent.xhsagentyunying.domain.entity.AnalysisReport;
import com.shortvideoscripagent.xhsagentyunying.domain.entity.AnalysisTask;
import com.shortvideoscripagent.xhsagentyunying.domain.mapper.AnalysisReportMapper;
import com.shortvideoscripagent.xhsagentyunying.domain.mapper.AnalysisTaskMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
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
    private final AiRuntimeProperties aiRuntimeProperties;
    private final AiRuntimePolicy aiRuntimePolicy;

    @Async("analysisExecutor")
    public void analyzeAsync(String taskId) {
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
            Thread.sleep(800);
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

        CompletableFuture<Map<String, Object>> reportFuture = CompletableFuture.supplyAsync(() -> runTextAnalysis(task));
        CompletableFuture<Map<String, Object>> coverFuture = CompletableFuture.supplyAsync(() -> coverAnalysisService.analyze(task));

        Map<String, Object> report = reportFuture.orTimeout(timeoutSeconds, TimeUnit.SECONDS).join();
        Map<String, Object> coverAnalysis = coverFuture.orTimeout(timeoutSeconds, TimeUnit.SECONDS).join();

        coverAnalysisService.mergeCtrInsight(report, coverAnalysis);
        return new AnalysisResult(report, coverAnalysis);
    }

    private Map<String, Object> runTextAnalysis(AnalysisTask task) {
        String ragContext = buildRagContext(task);
        String systemPrompt = promptEngine.systemPrompt();
        String userPrompt = promptEngine.buildAnalysisUserPrompt(task, ragContext);
        ModelProvider provider = modelProviderRegistry.getDefault();
        int timeoutSeconds = appProperties.getAi().getAnalysisTimeoutSeconds();
        int maxRetries = appProperties.getAi().getAnalysisMaxRetries();

        task.setModelProvider(provider.id());
        task.setModelName(aiRuntimeProperties.getDashscopeChatModel());
        taskMapper.updateById(task);

        Exception lastError = null;
        for (int attempt = 0; attempt <= maxRetries; attempt++) {
            try {
                String raw = CompletableFuture
                        .supplyAsync(() -> provider.chat(systemPrompt, userPrompt))
                        .orTimeout(timeoutSeconds, TimeUnit.SECONDS)
                        .join();
                Map<String, Object> report = jsonReportParser.parseAnalysisReport(raw, task.getScenario());
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
