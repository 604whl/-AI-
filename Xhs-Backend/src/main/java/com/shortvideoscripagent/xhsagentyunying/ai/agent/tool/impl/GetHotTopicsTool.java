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
public class GetHotTopicsTool implements AgentTool {

    private final WebSearchService webSearchService;
    private final UserQuotaService userQuotaService;

    @Override
    public String name() {
        return "get_hot_topics";
    }

    @Override
    public String description() {
        return "获取小红书全平台当前热门选题方向与关键词建议";
    }

    @Override
    public Map<String, Object> parametersSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "limit", Map.of("type", "integer", "default", 5)
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
        int limit = Math.min(Math.max(SearchKbTool.intArg(arguments, "limit", 5), 1), 10);
        List<Map<String, Object>> slice;
        try {
            slice = webSearchService.search("小红书 当前热门选题 趋势 内容运营", limit).stream()
                    .map(this::toTopic)
                    .toList();
            userQuotaService.consumeWebSearchQuota(context.userId(), context.sessionId());
        } catch (BusinessException ex) {
            return ToolResult.fail(ex.getMessage());
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("topics", slice);
        payload.put("source", "web_search");

        AgentCard card = AgentCard.builder()
                .type("hot_topics")
                .payload(payload)
                .build();

        return ToolResult.ok(payload, List.of(card));
    }

    private Map<String, Object> toTopic(Map<String, Object> item) {
        Map<String, Object> topic = new LinkedHashMap<>();
        topic.put("tag", item.getOrDefault("title", ""));
        topic.put("direction", item.getOrDefault("snippet", ""));
        topic.put("keywords", List.of());
        topic.put("url", item.getOrDefault("url", ""));
        return topic;
    }
}
