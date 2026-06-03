package com.shortvideoscripagent.xhsagentyunying.ai.agent.tool.impl;

import com.shortvideoscripagent.xhsagentyunying.ai.agent.AgentCard;
import com.shortvideoscripagent.xhsagentyunying.ai.agent.tool.AgentTool;
import com.shortvideoscripagent.xhsagentyunying.ai.agent.tool.ToolContext;
import com.shortvideoscripagent.xhsagentyunying.ai.agent.tool.ToolResult;
import com.shortvideoscripagent.xhsagentyunying.dto.analysis.AnalysisListItemResponse;
import com.shortvideoscripagent.xhsagentyunying.dto.analysis.PaginatedResponse;
import com.shortvideoscripagent.xhsagentyunying.service.AnalysisAppService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class ListRecentAnalysesTool implements AgentTool {

    private final AnalysisAppService analysisAppService;

    @Override
    public String name() {
        return "list_recent_analyses";
    }

    @Override
    public String description() {
        return "列出用户最近的分析任务，支持关键词过滤";
    }

    @Override
    public Map<String, Object> parametersSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "limit", Map.of("type", "integer", "default", 5, "maximum", 20),
                        "keyword", Map.of("type", "string")
                )
        );
    }

    @Override
    public ToolResult execute(ToolContext context, Map<String, Object> arguments) {
        int limit = Math.min(Math.max(SearchKbTool.intArg(arguments, "limit", 5), 1), 20);
        String keyword = SearchKbTool.stringArg(arguments, "keyword");

        PaginatedResponse<AnalysisListItemResponse> page = analysisAppService.list(
                context.userId(),
                1,
                limit,
                null,
                null,
                keyword.isBlank() ? null : keyword
        );

        List<Map<String, Object>> items = new ArrayList<>();
        for (AnalysisListItemResponse item : page.getItems()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("taskId", item.getId());
            row.put("title", item.getTitle());
            row.put("scenario", item.getScenario());
            row.put("status", item.getStatus());
            row.put("createdAt", item.getCreatedAt());
            row.put("overallScore", extractOverallScore(item.getReport()));
            items.add(row);
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("items", items);
        payload.put("total", page.getTotal());

        AgentCard card = AgentCard.builder()
                .type("recent_analyses")
                .payload(payload)
                .build();

        return ToolResult.ok(payload, List.of(card));
    }

    @SuppressWarnings("unchecked")
    private Object extractOverallScore(Map<String, Object> report) {
        if (report == null || !(report.get("scores") instanceof Map<?, ?> scores)) {
            return null;
        }
        Object viral = scores.get("viral");
        if (viral instanceof Map<?, ?> viralMap && viralMap.get("score") != null) {
            return viralMap.get("score");
        }
        return null;
    }
}
