package com.shortvideoscripagent.xhsagentyunying.ai.agent.tool.impl;

import com.shortvideoscripagent.xhsagentyunying.ai.agent.AgentCard;
import com.shortvideoscripagent.xhsagentyunying.ai.agent.tool.AgentTool;
import com.shortvideoscripagent.xhsagentyunying.ai.agent.tool.ToolContext;
import com.shortvideoscripagent.xhsagentyunying.ai.agent.tool.ToolResult;
import com.shortvideoscripagent.xhsagentyunying.ai.agent.data.AgentTopicDataService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class GetHotTopicsTool implements AgentTool {

    private final AgentTopicDataService agentTopicDataService;

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
        int limit = Math.min(Math.max(SearchKbTool.intArg(arguments, "limit", 5), 1), 10);
        List<Map<String, Object>> topics = agentTopicDataService.hotTopics();
        List<Map<String, Object>> slice = topics.size() <= limit ? topics : topics.subList(0, limit);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("topics", slice);

        AgentCard card = AgentCard.builder()
                .type("hot_topics")
                .payload(payload)
                .build();

        return ToolResult.ok(payload, List.of(card));
    }
}
