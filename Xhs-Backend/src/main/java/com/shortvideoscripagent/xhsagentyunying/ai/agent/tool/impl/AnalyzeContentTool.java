package com.shortvideoscripagent.xhsagentyunying.ai.agent.tool.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shortvideoscripagent.xhsagentyunying.ai.agent.AgentCard;
import com.shortvideoscripagent.xhsagentyunying.ai.agent.tool.AgentTool;
import com.shortvideoscripagent.xhsagentyunying.ai.agent.tool.ToolContext;
import com.shortvideoscripagent.xhsagentyunying.ai.agent.tool.ToolResult;
import com.shortvideoscripagent.xhsagentyunying.common.exception.BusinessException;
import com.shortvideoscripagent.xhsagentyunying.config.AppAiProperties;
import com.shortvideoscripagent.xhsagentyunying.domain.entity.AnalysisReport;
import com.shortvideoscripagent.xhsagentyunying.domain.entity.AnalysisTask;
import com.shortvideoscripagent.xhsagentyunying.domain.mapper.AnalysisReportMapper;
import com.shortvideoscripagent.xhsagentyunying.domain.mapper.AnalysisTaskMapper;
import com.shortvideoscripagent.xhsagentyunying.dto.analysis.AnalysisCreateRequest;
import com.shortvideoscripagent.xhsagentyunying.dto.analysis.AnalysisCreateResponse;
import com.shortvideoscripagent.xhsagentyunying.service.AnalysisAppService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class AnalyzeContentTool implements AgentTool {

    private final AnalysisAppService analysisAppService;
    private final AnalysisTaskMapper taskMapper;
    private final AnalysisReportMapper reportMapper;
    private final AppAiProperties appAiProperties;
    private final ObjectMapper objectMapper;

    @Override
    public String name() {
        return "analyze_content";
    }

    @Override
    public String description() {
        return "分析小红书笔记草稿，返回五维评分、问题诊断与优化建议";
    }

    @Override
    public Map<String, Object> parametersSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "title", Map.of("type", "string"),
                        "body", Map.of("type", "string"),
                        "scenario", Map.of("type", "string", "enum", List.of("draft", "published", "competitor")),
                        "persona", Map.of("type", "string", "enum", List.of("agency", "mentor", "senior")),
                        "coverImageUrl", Map.of("type", "string")
                )
        );
    }

    @Override
    public ToolResult execute(ToolContext context, Map<String, Object> arguments) {
        String title = firstNonBlank(
                SearchKbTool.stringArg(arguments, "title"),
                stringFromAttachments(context, "title")
        );
        String body = firstNonBlank(
                SearchKbTool.stringArg(arguments, "body"),
                stringFromAttachments(context, "body")
        );
        if (title.isBlank() && body.isBlank()) {
            return ToolResult.fail("title_or_body_required");
        }

        AnalysisCreateRequest request = new AnalysisCreateRequest();
        request.setScenario(firstNonBlank(SearchKbTool.stringArg(arguments, "scenario"), "draft"));
        request.setPersona(firstNonBlank(
                SearchKbTool.stringArg(arguments, "persona"),
                context.persona()
        ));
        request.setTitle(title.isBlank() ? null : title);
        request.setBody(body.isBlank() ? null : body);
        String cover = firstNonBlank(
                SearchKbTool.stringArg(arguments, "coverImageUrl"),
                stringFromAttachments(context, "coverImageUrl")
        );
        request.setCoverImageUrl(cover.isBlank() ? null : cover);

        try {
            AnalysisCreateResponse created = analysisAppService.create(context.userId(), request);
            AnalysisTask completed = waitForCompletion(created.getId(), appAiProperties.getAi().getAnalysisTimeoutSeconds() + 15);
            Map<String, Object> summary = buildReportSummary(completed.getId());

            AgentCard card = AgentCard.builder()
                    .type("analysis_report")
                    .taskId(completed.getId())
                    .payload(summary)
                    .build();

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("taskId", completed.getId());
            result.put("status", completed.getStatus());
            result.put("reportSummary", summary);
            return ToolResult.ok(result, List.of(card), completed.getId());
        } catch (BusinessException ex) {
            return ToolResult.fail(ex.getMessage());
        } catch (Exception ex) {
            log.warn("analyze_content failed: {}", ex.getMessage());
            return ToolResult.fail("analyze_content_failed");
        }
    }

    private AnalysisTask waitForCompletion(String taskId, int timeoutSeconds) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutSeconds * 1000L;
        while (System.currentTimeMillis() < deadline) {
            AnalysisTask task = taskMapper.selectById(taskId);
            if (task == null) {
                throw new BusinessException(40401, "analysis_not_found");
            }
            if ("completed".equals(task.getStatus())) {
                return task;
            }
            if ("failed".equals(task.getStatus())) {
                throw new BusinessException(50002, "analysis_failed");
            }
            Thread.sleep(1000L);
        }
        throw new BusinessException(50401, "analysis_timeout");
    }

    private Map<String, Object> buildReportSummary(String taskId) {
        AnalysisReport report = reportMapper.selectById(taskId);
        if (report == null || report.getReportJson() == null) {
            return Map.of("taskId", taskId);
        }
        try {
            Map<String, Object> full = objectMapper.readValue(report.getReportJson(), new TypeReference<>() {
            });
            Map<String, Object> summary = new LinkedHashMap<>();
            summary.put("contentType", full.get("contentType"));
            summary.put("scores", full.get("scores"));
            summary.put("topIssues", topItems(full.get("issues"), 3));
            summary.put("topOptimizations", topOptimizationItems(full.get("optimizations"), 3));
            if (report.getCoverAnalysis() != null && !report.getCoverAnalysis().isBlank()) {
                try {
                    summary.put("coverAnalysis", objectMapper.readValue(report.getCoverAnalysis(), new TypeReference<>() {
                    }));
                } catch (Exception ignored) {
                }
            }
            return summary;
        } catch (Exception ex) {
            return Map.of("taskId", taskId);
        }
    }

    @SuppressWarnings("unchecked")
    private List<?> topItems(Object issues, int limit) {
        if (issues instanceof List<?> list) {
            return list.size() <= limit ? list : list.subList(0, limit);
        }
        return List.of();
    }

    @SuppressWarnings("unchecked")
    private List<?> topOptimizationItems(Object optimizations, int limit) {
        if (!(optimizations instanceof Map<?, ?> map)) {
            return List.of();
        }
        List<Object> flat = new java.util.ArrayList<>();
        map.values().forEach(value -> {
            if (value instanceof List<?> list) {
                flat.addAll(list);
            }
        });
        return flat.size() <= limit ? flat : flat.subList(0, limit);
    }

    private static String stringFromAttachments(ToolContext context, String key) {
        if (context.attachments() == null) {
            return "";
        }
        Object value = context.attachments().get(key);
        return value == null ? "" : String.valueOf(value).trim();
    }

    private static String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first.trim();
        }
        return second == null ? "" : second.trim();
    }
}
