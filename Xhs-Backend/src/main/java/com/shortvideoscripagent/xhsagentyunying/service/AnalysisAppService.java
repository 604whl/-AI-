package com.shortvideoscripagent.xhsagentyunying.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shortvideoscripagent.xhsagentyunying.ai.AiRuntimePolicy;
import com.shortvideoscripagent.xhsagentyunying.ai.model.ModelProviderRegistry;
import com.shortvideoscripagent.xhsagentyunying.ai.cover.CoverAnalysisService;
import com.shortvideoscripagent.xhsagentyunying.ai.orchestrator.AnalysisOrchestrator;
import com.shortvideoscripagent.xhsagentyunying.ai.parser.JsonReportParser;
import com.shortvideoscripagent.xhsagentyunying.ai.prompt.PromptEngine;
import com.shortvideoscripagent.xhsagentyunying.common.exception.BusinessException;
import com.shortvideoscripagent.xhsagentyunying.config.AiRuntimeProperties;
import com.shortvideoscripagent.xhsagentyunying.config.AppAiProperties;
import com.shortvideoscripagent.xhsagentyunying.domain.compliance.ComplianceChecker;
import com.shortvideoscripagent.xhsagentyunying.domain.entity.AnalysisReport;
import com.shortvideoscripagent.xhsagentyunying.domain.entity.AnalysisTask;
import com.shortvideoscripagent.xhsagentyunying.domain.mapper.AnalysisReportMapper;
import com.shortvideoscripagent.xhsagentyunying.domain.mapper.AnalysisTaskMapper;
import com.shortvideoscripagent.xhsagentyunying.dto.analysis.AnalysisCreateRequest;
import com.shortvideoscripagent.xhsagentyunying.dto.analysis.AnalysisCreateResponse;
import com.shortvideoscripagent.xhsagentyunying.dto.analysis.AnalysisDetailResponse;
import com.shortvideoscripagent.xhsagentyunying.dto.analysis.AnalysisListItemResponse;
import com.shortvideoscripagent.xhsagentyunying.dto.analysis.BodyGenerateRequest;
import com.shortvideoscripagent.xhsagentyunying.dto.analysis.BodyGenerateResponse;
import com.shortvideoscripagent.xhsagentyunying.dto.analysis.OptimizeDraftRequest;
import com.shortvideoscripagent.xhsagentyunying.dto.analysis.OptimizeDraftResponse;
import com.shortvideoscripagent.xhsagentyunying.dto.analysis.PaginatedResponse;
import com.shortvideoscripagent.xhsagentyunying.service.analysis.AnalysisProgressEvent;
import com.shortvideoscripagent.xhsagentyunying.service.analysis.AnalysisStreamHub;
import lombok.RequiredArgsConstructor;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AnalysisAppService {

    private static final int CODE_NOT_FOUND = 40401;
    private static final int CODE_BAD_REQUEST = 40001;
    private static final int CODE_NOT_COMPLETED = 40901;
    private static final int CODE_ALREADY_PROCESSING = 40902;
    private static final int CODE_AI_UNAVAILABLE = 50002;
    private static final int CODE_AI_TIMEOUT = 50401;
    private static final int CODE_BODY_QUOTA_EXCEEDED = 42905;

    private final AnalysisTaskMapper taskMapper;
    private final AnalysisReportMapper reportMapper;
    private final AnalysisOrchestrator analysisOrchestrator;
    private final ObjectMapper objectMapper;
    private final UserQuotaService userQuotaService;
    private final ModelProviderRegistry modelProviderRegistry;
    private final PromptEngine promptEngine;
    private final JsonReportParser jsonReportParser;
    private final ComplianceChecker complianceChecker;
    private final AppAiProperties appProperties;
    private final AiRuntimeProperties aiRuntimeProperties;
    private final AiRuntimePolicy aiRuntimePolicy;
    private final AnalysisStreamHub analysisStreamHub;

    @Resource(name = "aiExecutor")
    private Executor aiExecutor;

    @Transactional
    public AnalysisCreateResponse create(Long userId, AnalysisCreateRequest request) {
        validateCreateRequest(request);
        assertNoConcurrentTask(userId);

        OffsetDateTime now = OffsetDateTime.now();
        AnalysisTask task = new AnalysisTask();
        task.setId(generateTaskId());
        task.setUserId(userId);
        task.setScenario(request.getScenario());
        task.setPersona(request.getPersona() == null ? "agency" : request.getPersona());
        task.setTitle(trimToNull(request.getTitle()));
        task.setBody(trimToNull(request.getBody()));
        task.setCoverImageUrl(trimToNull(request.getCoverImageUrl()));
        task.setPublishedMetrics(serializeJson(request.getPublishedMetrics()));
        task.setCompetitorContext(serializeJson(request.getCompetitorContext()));
        task.setStatus("pending");
        task.setPromptVersion(PromptEngine.RUBRIC_VERSION);
        task.setModelProvider(appProperties.getAi().getDefaultProvider());
        task.setModelName(aiRuntimeProperties.getDashscopeChatModel());
        task.setCreatedAt(now);
        task.setUpdatedAt(now);
        taskMapper.insert(task);
        analysisStreamHub.publish(toProgressEvent(task));

        userQuotaService.consumeAnalysisQuota(userId, task.getId());
        analysisOrchestrator.analyzeAsync(task.getId());
        return new AnalysisCreateResponse(task.getId(), task.getStatus());
    }

    public AnalysisDetailResponse getById(Long userId, String id) {
        AnalysisTask task = requireOwnedTask(userId, id);
        AnalysisReport report = reportMapper.selectById(id);
        return toDetail(task, report);
    }

    public SseEmitter streamProgress(Long userId, String id) {
        AnalysisTask task = requireOwnedTask(userId, id);
        long timeoutMs = (appProperties.getAi().getAnalysisTimeoutSeconds() + 120L) * 1000L;
        analysisStreamHub.publish(toProgressEvent(task));
        return analysisStreamHub.subscribe(id, timeoutMs);
    }

    public PaginatedResponse<AnalysisListItemResponse> list(
            Long userId,
            int page,
            int size,
            String status,
            String scenario,
            String keyword
    ) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(size, 1), 50);
        int offset = (safePage - 1) * safeSize;

        LambdaQueryWrapper<AnalysisTask> wrapper = buildListQuery(userId, status, scenario, keyword)
                .orderByDesc(AnalysisTask::getCreatedAt)
                .last("LIMIT " + safeSize + " OFFSET " + offset);

        List<AnalysisTask> tasks = taskMapper.selectList(wrapper);
        Long total = taskMapper.selectCount(buildListQuery(userId, status, scenario, keyword));

        Map<String, AnalysisReport> reportsByTaskId = loadReportsByTaskId(tasks);
        List<AnalysisListItemResponse> items = tasks.stream()
                .map(task -> toListItem(task, reportsByTaskId.get(task.getId())))
                .toList();

        return new PaginatedResponse<>(items, total == null ? 0 : total, safePage, safeSize);
    }

    @Transactional
    public void delete(Long userId, String id) {
        requireOwnedTask(userId, id);
        reportMapper.deleteById(id);
        taskMapper.deleteById(id);
    }

    public OptimizeDraftResponse optimizeDraft(Long userId, String id, OptimizeDraftRequest request) {
        AnalysisTask task = requireOwnedTask(userId, id);
        if (!"completed".equals(task.getStatus())) {
            throw new BusinessException(CODE_NOT_COMPLETED, "analysis_not_completed");
        }
        AnalysisReport stored = reportMapper.selectById(id);
        Map<String, Object> report = parseReport(stored);
        if (report == null) {
            throw new BusinessException(CODE_NOT_COMPLETED, "analysis_not_completed");
        }

        if (aiRuntimePolicy.useMockResponses()) {
            return buildMockOptimizeDraft(task, report);
        }

        aiRuntimePolicy.assertRealAiAvailable();

        String tone = request.getTone() == null ? "default" : request.getTone();
        int maxLength = request.getMaxLength() > 0 ? request.getMaxLength() : 1200;
        String userPrompt = promptEngine.buildOptimizeDraftUserPrompt(task, report, tone, maxLength);
        String raw = callModel(promptEngine.systemPrompt(), userPrompt);
        Map<String, Object> draft = jsonReportParser.parseOptimizeDraft(raw);
        complianceChecker.mergeIntoReport(draft, String.valueOf(draft.get("optimizedTitle")), String.valueOf(draft.get("optimizedBody")));

        String body = String.valueOf(draft.get("optimizedBody"));
        String optimizedTitle = request.isIncludeTitle()
                ? truncateTitle(String.valueOf(draft.get("optimizedTitle")))
                : truncateTitle(task.getTitle());
        return OptimizeDraftResponse.builder()
                .analysisId(id)
                .optimizedTitle(optimizedTitle)
                .optimizedBody(body)
                .structureOutline(castStringList(draft.get("structureOutline")))
                .cta(String.valueOf(draft.getOrDefault("cta", "")))
                .complianceWarnings(castWarningList(draft.get("complianceWarnings")))
                .promptVersion(PromptEngine.RUBRIC_VERSION)
                .wordCount(body.length())
                .build();
    }

    public BodyGenerateResponse generateBody(Long userId, String id, BodyGenerateRequest request) {
        AnalysisTask task = requireOwnedTask(userId, id);
        if (!"completed".equals(task.getStatus())) {
            throw new BusinessException(CODE_NOT_COMPLETED, "analysis_not_completed");
        }
        AnalysisReport stored = reportMapper.selectById(id);
        Map<String, Object> report = parseReport(stored);
        if (report == null) {
            throw new BusinessException(CODE_NOT_COMPLETED, "analysis_not_completed");
        }
        boolean hasTitle = task.getTitle() != null && !task.getTitle().isBlank();
        boolean hasBody = task.getBody() != null && !task.getBody().isBlank();
        if (!hasTitle && !hasBody) {
            throw new BusinessException(CODE_BAD_REQUEST, "title or body is required");
        }

        if (userQuotaService.remainingBodyGenerationQuota(userId) <= 0) {
            throw new BusinessException(CODE_BODY_QUOTA_EXCEEDED, "body_quota_exceeded");
        }

        String goal = request.getGoal() == null || request.getGoal().isBlank() ? "high_conversion" : request.getGoal();
        String tone = request.getTone() == null || request.getTone().isBlank() ? "default" : request.getTone();
        int maxLength = request.getMaxLength() == null ? 900 : request.getMaxLength();

        if (aiRuntimePolicy.useMockResponses()) {
            BodyGenerateResponse response = buildMockBodyGenerate(task, report, goal);
            userQuotaService.consumeBodyGenerationQuota(userId, id);
            return response;
        }

        aiRuntimePolicy.assertRealAiAvailable();

        String userPrompt = promptEngine.buildBodyGenerateUserPrompt(
                task,
                report,
                goal,
                tone,
                maxLength,
                request.getKeywords()
        );
        String raw = callModel(promptEngine.bodySystemPrompt(), userPrompt);
        JsonReportParser.ParsedBodyGenerate parsed = jsonReportParser.parseBodyGenerate(raw);

        Map<String, Object> complianceHolder = new java.util.LinkedHashMap<>();
        complianceHolder.put("complianceWarnings", parsed.complianceWarnings());
        complianceChecker.mergeIntoReport(complianceHolder, task.getTitle(), parsed.body());
        userQuotaService.consumeBodyGenerationQuota(userId, id);

        return BodyGenerateResponse.builder()
                .analysisId(id)
                .goal(goal)
                .body(parsed.body())
                .structureOutline(parsed.structureOutline())
                .cta(parsed.cta())
                .complianceWarnings(castWarningList(complianceHolder.get("complianceWarnings")))
                .promptVersion(PromptEngine.BODY_PROMPT_VERSION)
                .wordCount(parsed.body().length())
                .build();
    }

    private void assertNoConcurrentTask(Long userId) {
        Long active = taskMapper.selectCount(new LambdaQueryWrapper<AnalysisTask>()
                .eq(AnalysisTask::getUserId, userId)
                .in(AnalysisTask::getStatus, List.of("pending", "processing")));
        if (active != null && active > 0) {
            throw new BusinessException(CODE_ALREADY_PROCESSING, "analysis_already_processing");
        }
    }

    private OptimizeDraftResponse buildMockOptimizeDraft(AnalysisTask task, Map<String, Object> report) {
        String optimizedTitle = task.getTitle() == null || task.getTitle().isBlank()
                ? "一张图讲清｜完整攻略（优化版）"
                : task.getTitle() + "（优化版）";
        optimizedTitle = truncateTitle(optimizedTitle);
        String optimizedBody = task.getBody() == null || task.getBody().isBlank()
                ? "开篇用反常识结论抓住注意力…\n\n（此为 Mock 优化稿，配置真实 API Key 后可生成完整正文）"
                : task.getBody() + "\n\n---\n[Mock 优化建议已根据报告结构调整段落与 CTA]";
        return OptimizeDraftResponse.builder()
                .analysisId(task.getId())
                .optimizedTitle(optimizedTitle)
                .optimizedBody(optimizedBody)
                .structureOutline(List.of("Hook：痛点反问", "放大：情绪共鸣", "干货：分步拆解", "结果：可执行清单", "CTA：评论区领资料"))
                .cta("评论「资料」领取完整清单")
                .complianceWarnings(castWarningList(report.get("complianceWarnings")))
                .promptVersion(PromptEngine.RUBRIC_VERSION)
                .wordCount(optimizedBody.length())
                .build();
    }

    private BodyGenerateResponse buildMockBodyGenerate(AnalysisTask task, Map<String, Object> report, String goal) {
        String cta = switch (task.getPersona() == null ? "agency" : task.getPersona()) {
            case "mentor" -> "如果你也遇到类似问题，可以把你的情况发我，我帮你拆一下优先级。";
            case "senior" -> "你们有没有遇到过类似情况？可以在评论区聊聊。";
            default -> "想要更完整的整理思路，可以评论「资料」领取。";
        };
        String sourceTitle = task.getTitle() == null || task.getTitle().isBlank() ? "这个主题" : task.getTitle().trim();
        String sourceBody = task.getBody() == null ? "" : task.getBody().trim();
        String subject = sourceBody.isBlank() ? sourceTitle : "这段素材";
        String body = sourceBody.isBlank()
                ? """
                %s，其实可以从一个很具体的场景切入。

                先把用户最容易遇到的问题讲清楚，再补充为什么这个问题会反复出现。这样开头不会空，也更容易让读者判断「这是不是在说我」。

                接着可以给出你的观察、经验或方法，把内容拆成几个可执行的小点。不要急着堆结论，先让读者看到前因后果。

                最后收束到一个明确动作：收藏、评论、咨询或继续关注，让正文从内容价值自然过渡到互动。

                %s
                """.formatted(sourceTitle, cta).trim()
                : """
                别急着把信息一次性全抛出来，%s更适合先改成一个「读者能立刻代入」的开头。

                第一段先点出核心问题：读者为什么会在这个场景里卡住，以及继续看下去能获得什么判断。

                第二段把原素材里的重点重新拆开，不照搬原句，而是按「背景 - 关键发现 - 可执行动作」重新组织，让信息更像一篇完整笔记。

                第三段给出明确建议：哪些内容可以保留，哪些表达需要前置，哪些细节可以变成清单，方便收藏和转发。

                最后用一个轻量 CTA 收束，不强行推销，也不承诺结果，只把下一步行动讲清楚。

                %s
                """.formatted(subject, cta).trim();
        Map<String, Object> complianceHolder = new java.util.LinkedHashMap<>();
        complianceHolder.put("complianceWarnings", report.get("complianceWarnings"));
        complianceChecker.mergeIntoReport(complianceHolder, task.getTitle(), body);
        return BodyGenerateResponse.builder()
                .analysisId(task.getId())
                .goal(goal)
                .body(body)
                .structureOutline(List.of(
                        new BodyGenerateResponse.StructureSection("hook", "围绕原始标题或正文的核心问题切入"),
                        new BodyGenerateResponse.StructureSection("problem_amplification", "放大原始素材里的痛点或信息差"),
                        new BodyGenerateResponse.StructureSection("real_experience", "承接原始素材中的经历、观点或观察"),
                        new BodyGenerateResponse.StructureSection("result_showcase", "给出围绕原主题的结论或可执行建议"),
                        new BodyGenerateResponse.StructureSection("cta", "根据人设输出自然互动引导")
                ))
                .cta(cta)
                .complianceWarnings(castWarningList(complianceHolder.get("complianceWarnings")))
                .promptVersion(PromptEngine.BODY_PROMPT_VERSION)
                .wordCount(body.length())
                .build();
    }

    private LambdaQueryWrapper<AnalysisTask> buildListQuery(
            Long userId,
            String status,
            String scenario,
            String keyword
    ) {
        LambdaQueryWrapper<AnalysisTask> wrapper = new LambdaQueryWrapper<AnalysisTask>()
                .eq(AnalysisTask::getUserId, userId);

        if (status != null && !status.isBlank()) {
            wrapper.eq(AnalysisTask::getStatus, status.trim());
        }
        if (scenario != null && !scenario.isBlank()) {
            wrapper.eq(AnalysisTask::getScenario, scenario.trim());
        }
        if (keyword != null && !keyword.isBlank()) {
            String kw = keyword.trim();
            wrapper.and(w -> w.like(AnalysisTask::getTitle, kw).or().like(AnalysisTask::getBody, kw));
        }
        return wrapper;
    }

    private AnalysisTask requireOwnedTask(Long userId, String id) {
        AnalysisTask task = taskMapper.selectById(id);
        if (task == null || !task.getUserId().equals(userId)) {
            throw new BusinessException(CODE_NOT_FOUND, "analysis not found");
        }
        return task;
    }

    private void validateCreateRequest(AnalysisCreateRequest request) {
        boolean hasTitle = request.getTitle() != null && !request.getTitle().isBlank();
        boolean hasBody = request.getBody() != null && !request.getBody().isBlank();
        if (!hasTitle && !hasBody) {
            throw new BusinessException(CODE_BAD_REQUEST, "title or body is required");
        }
    }

    private AnalysisDetailResponse toDetail(AnalysisTask task, AnalysisReport report) {
        Map<String, Object> reportMap = parseReport(report);
        Map<String, Object> failure = null;
        if ("failed".equals(task.getStatus())) {
            failure = Map.of(
                    "reason", task.getFailureReason() == null ? "unknown" : task.getFailureReason(),
                    "code", task.getFailureCode() == null ? 50001 : task.getFailureCode(),
                    "message", failureMessage(task.getFailureReason())
            );
        }

        return AnalysisDetailResponse.builder()
                .id(task.getId())
                .status(task.getStatus())
                .scenario(task.getScenario())
                .persona(task.getPersona())
                .title(task.getTitle())
                .body(task.getBody())
                .coverImageUrl(task.getCoverImageUrl())
                .publishedMetrics(parseJsonMap(task.getPublishedMetrics()))
                .competitorContext(parseJsonMap(task.getCompetitorContext()))
                .promptVersion(task.getPromptVersion())
                .model(task.getModelProvider())
                .processingMs(task.getProcessingMs())
                .createdAt(task.getCreatedAt())
                .updatedAt(task.getUpdatedAt())
                .failure(failure)
                .report(reportMap)
                .coverAnalysis(resolveCoverAnalysis(task, report, reportMap))
                .build();
    }

    private AnalysisListItemResponse toListItem(AnalysisTask task, AnalysisReport report) {
        Map<String, Object> reportMap = parseReport(report);
        Map<String, Object> listReport = null;
        if (reportMap != null && reportMap.get("scores") != null) {
            listReport = Map.of("scores", reportMap.get("scores"));
        }

        return AnalysisListItemResponse.builder()
                .id(task.getId())
                .status(task.getStatus())
                .scenario(task.getScenario())
                .persona(task.getPersona())
                .title(task.getTitle())
                .createdAt(task.getCreatedAt())
                .updatedAt(task.getUpdatedAt())
                .report(listReport)
                .build();
    }

    private Map<String, AnalysisReport> loadReportsByTaskId(List<AnalysisTask> tasks) {
        if (tasks == null || tasks.isEmpty()) {
            return Map.of();
        }
        List<String> taskIds = tasks.stream().map(AnalysisTask::getId).toList();
        return reportMapper.selectBatchIds(taskIds).stream()
                .collect(Collectors.toMap(AnalysisReport::getTaskId, Function.identity(), (a, b) -> a));
    }

    private Map<String, Object> parseReport(AnalysisReport report) {
        if (report == null || report.getReportJson() == null || report.getReportJson().isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(report.getReportJson(), new TypeReference<>() {
            });
        } catch (Exception ex) {
            return null;
        }
    }

    private Map<String, Object> resolveCoverAnalysis(
            AnalysisTask task,
            AnalysisReport stored,
            Map<String, Object> reportMap
    ) {
        if (stored != null && stored.getCoverAnalysis() != null && !stored.getCoverAnalysis().isBlank()) {
            Map<String, Object> parsed = parseJsonMap(stored.getCoverAnalysis());
            if (parsed != null) {
                return parsed;
            }
        }
        if (task.getCoverImageUrl() == null || task.getCoverImageUrl().isBlank()) {
            return CoverAnalysisService.unavailable("not_provided");
        }
        return CoverAnalysisService.unavailable("analysis_not_available");
    }

    private String failureMessage(String reason) {
        return switch (reason == null ? "unknown" : reason) {
            case "timeout" -> "analysis timeout";
            case "ai_error" -> "ai service error";
            default -> "analysis failed";
        };
    }

    @SuppressWarnings("unchecked")
    private List<String> castStringList(Object value) {
        if (value instanceof List<?> list) {
            return list.stream().map(String::valueOf).toList();
        }
        return List.of();
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> castWarningList(Object value) {
        if (value instanceof List<?> list) {
            return list.stream()
                    .filter(Map.class::isInstance)
                    .map(item -> (Map<String, Object>) item)
                    .toList();
        }
        return List.of();
    }

    private AnalysisProgressEvent toProgressEvent(AnalysisTask task) {
        String phase = switch (task.getStatus() == null ? "pending" : task.getStatus()) {
            case "processing" -> "processing";
            case "completed" -> "finished";
            case "failed" -> "failed";
            default -> "pending";
        };
        String message = switch (task.getStatus() == null ? "pending" : task.getStatus()) {
            case "completed" -> "分析完成";
            case "failed" -> failureMessage(task.getFailureReason());
            case "processing" -> "分析进行中";
            default -> "等待分析";
        };
        return AnalysisProgressEvent.builder()
                .taskId(task.getId())
                .status(task.getStatus())
                .phase(phase)
                .message(message)
                .processingMs(task.getProcessingMs())
                .failureCode(task.getFailureCode())
                .failureReason(task.getFailureReason())
                .build();
    }

    private String generateTaskId() {
        return "ana_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String truncateTitle(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() > 20 ? trimmed.substring(0, 20) : trimmed;
    }

    private String callModel(String systemPrompt, String userPrompt) {
        try {
            return CompletableFuture
                    .supplyAsync(() -> modelProviderRegistry.chatWithFallback(systemPrompt, userPrompt).content(), aiExecutor)
                    .orTimeout(appProperties.getAi().getAnalysisTimeoutSeconds(), TimeUnit.SECONDS)
                    .join();
        } catch (CompletionException ex) {
            Throwable cause = unwrapCompletionException(ex);
            if (cause instanceof BusinessException businessException) {
                throw businessException;
            }
            if (cause instanceof TimeoutException) {
                throw new BusinessException(CODE_AI_TIMEOUT, "ai_timeout");
            }
            throw new BusinessException(CODE_AI_UNAVAILABLE, "ai_service_error");
        }
    }

    private Throwable unwrapCompletionException(CompletionException ex) {
        Throwable cause = ex;
        while (cause instanceof CompletionException && cause.getCause() != null) {
            cause = cause.getCause();
        }
        return cause;
    }

    private String serializeJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            throw new BusinessException(CODE_BAD_REQUEST, "invalid_request");
        }
    }

    private Map<String, Object> parseJsonMap(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() {
            });
        } catch (Exception ex) {
            return null;
        }
    }
}
