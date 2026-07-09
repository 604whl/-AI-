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

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class GetIndustryCalendarTool implements AgentTool {

    private final WebSearchService webSearchService;
    private final UserQuotaService userQuotaService;

    @Override
    public String name() {
        return "get_industry_calendar";
    }

    @Override
    public String description() {
        return "获取节日营销、季节上新、大促节点等内容运营日历与选题包建议";
    }

    @Override
    public Map<String, Object> parametersSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "limit", Map.of("type", "integer", "default", 4)
                )
        );
    }

    @Override
    public ToolResult execute(ToolContext context, Map<String, Object> arguments) {
        if (!webSearchService.isConfigured()) {
            return ToolResult.fail("web_search_not_configured");
        }
        if (userQuotaService.remainingWebSearchQuota(context.userId()) <= 0) {
            return ToolResult.fail("web_search_quota_exceeded");
        }
        int limit = Math.min(Math.max(SearchKbTool.intArg(arguments, "limit", 4), 1), 12);
        List<Map<String, Object>> slice;
        try {
            String query = LocalDate.now().getYear() + " 小红书 营销节点 节日 大促 内容日历";
            slice = webSearchService.search(query, limit).stream()
                    .map(this::toEvent)
                    .toList();
            userQuotaService.consumeWebSearchQuota(context.userId(), context.sessionId());
        } catch (BusinessException ex) {
            return ToolResult.fail(ex.getMessage());
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("events", slice);
        payload.put("source", "web_search");

        AgentCard card = AgentCard.builder()
                .type("industry_calendar")
                .payload(payload)
                .build();

        return ToolResult.ok(payload, List.of(card));
    }

    private Map<String, Object> toEvent(Map<String, Object> item) {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("name", item.getOrDefault("title", ""));
        event.put("suggestion", item.getOrDefault("snippet", ""));
        event.put("url", item.getOrDefault("url", ""));
        return event;
    }
}
