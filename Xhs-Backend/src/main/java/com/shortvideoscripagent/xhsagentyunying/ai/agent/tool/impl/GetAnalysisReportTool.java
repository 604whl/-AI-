package com.shortvideoscripagent.xhsagentyunying.ai.agent.tool.impl;

import com.shortvideoscripagent.xhsagentyunying.ai.agent.AgentCard;
import com.shortvideoscripagent.xhsagentyunying.ai.agent.tool.AgentTool;
import com.shortvideoscripagent.xhsagentyunying.ai.agent.tool.ToolContext;
import com.shortvideoscripagent.xhsagentyunying.ai.agent.tool.ToolResult;
import com.shortvideoscripagent.xhsagentyunying.dto.analysis.AnalysisDetailResponse;
import com.shortvideoscripagent.xhsagentyunying.service.AnalysisAppService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class GetAnalysisReportTool implements AgentTool {

    private final AnalysisAppService analysisAppService;

    @Override
    public String name() {
        return "get_analysis_report";
    }

    @Override
    public String description() {
        return "获取指定分析任务的报告摘要，含五维评分、问题与建议";
    }

    @Override
    public Map<String, Object> parametersSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "taskId", Map.of("type", "string", "description", "分析任务 ID")
                ),
                "required", List.of("taskId")
        );
    }

    @Override
    public ToolResult execute(ToolContext context, Map<String, Object> arguments) {
        String taskId = firstNonBlank(
                SearchKbTool.stringArg(arguments, "taskId"),
                context.linkedTaskId()
        );
        if (taskId.isBlank()) {
            return ToolResult.fail("taskId_required");
        }

        AnalysisDetailResponse detail = analysisAppService.getById(context.userId(), taskId);
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("taskId", detail.getId());
        summary.put("status", detail.getStatus());
        summary.put("title", detail.getTitle());
        summary.put("scores", extractScores(detail.getReport()));
        summary.put("issues", extractIssues(detail.getReport()));
        summary.put("report", detail.getReport());

        AgentCard card = AgentCard.builder()
                .type("analysis_report")
                .taskId(taskId)
                .payload(summary)
                .build();

        return ToolResult.ok(summary, List.of(card), taskId);
    }

    @SuppressWarnings("unchecked")
    private Object extractScores(Map<String, Object> report) {
        if (report == null) {
            return Map.of();
        }
        return report.getOrDefault("scores", Map.of());
    }

    @SuppressWarnings("unchecked")
    private Object extractIssues(Map<String, Object> report) {
        if (report == null || !(report.get("issues") instanceof List<?> list)) {
            return List.of();
        }
        return list.size() <= 5 ? list : list.subList(0, 5);
    }

    private static String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first.trim();
        }
        return second == null ? "" : second.trim();
    }
}
