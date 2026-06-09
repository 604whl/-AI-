package com.shortvideoscripagent.xhsagentyunying.ai.agent.tool.impl;

import com.shortvideoscripagent.xhsagentyunying.ai.agent.AgentCard;
import com.shortvideoscripagent.xhsagentyunying.ai.agent.tool.AgentTool;
import com.shortvideoscripagent.xhsagentyunying.ai.agent.tool.ToolContext;
import com.shortvideoscripagent.xhsagentyunying.ai.agent.tool.ToolResult;
import com.shortvideoscripagent.xhsagentyunying.ai.agent.web.WebSearchService;
import com.shortvideoscripagent.xhsagentyunying.common.exception.BusinessException;
import com.shortvideoscripagent.xhsagentyunying.service.UserQuotaService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class WebSearchTool implements AgentTool {

    private final WebSearchService webSearchService;
    private final UserQuotaService userQuotaService;

    @Override
    public String name() {
        return "web_search";
    }

    @Override
    public String description() {
        return "联网搜索行业动态、平台趋势、热点话题等实时信息";
    }

    @Override
    public Map<String, Object> parametersSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "query", Map.of("type", "string", "description", "搜索关键词"),
                        "maxResults", Map.of("type", "integer", "default", 5)
                ),
                "required", List.of("query")
        );
    }

    @Override
    public ToolResult execute(ToolContext context, Map<String, Object> arguments) {
        String query = SearchKbTool.stringArg(arguments, "query");
        if (query.isBlank()) {
            return ToolResult.fail("query_required");
        }

        try {
            userQuotaService.consumeWebSearchQuota(context.userId(), context.sessionId());
        } catch (BusinessException ex) {
            return ToolResult.fail(ex.getMessage());
        }

        int maxResults = SearchKbTool.intArg(arguments, "maxResults", 5);
        List<Map<String, Object>> results = webSearchService.search(query, maxResults);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("query", query);
        payload.put("results", results);

        AgentCard card = AgentCard.builder()
                .type("web_search")
                .payload(payload)
                .build();

        return ToolResult.ok(payload, List.of(card));
    }
}
