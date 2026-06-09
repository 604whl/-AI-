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
public class GetIndustryCalendarTool implements AgentTool {

    private final AgentTopicDataService agentTopicDataService;

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
        int limit = Math.min(Math.max(SearchKbTool.intArg(arguments, "limit", 4), 1), 12);
        List<Map<String, Object>> events = agentTopicDataService.industryCalendar();
        List<Map<String, Object>> slice = events.size() <= limit ? events : events.subList(0, limit);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("events", slice);

        AgentCard card = AgentCard.builder()
                .type("industry_calendar")
                .payload(payload)
                .build();

        return ToolResult.ok(payload, List.of(card));
    }
}
