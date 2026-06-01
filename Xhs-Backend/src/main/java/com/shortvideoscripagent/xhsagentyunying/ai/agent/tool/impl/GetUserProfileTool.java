package com.shortvideoscripagent.xhsagentyunying.ai.agent.tool.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shortvideoscripagent.xhsagentyunying.ai.agent.tool.AgentTool;
import com.shortvideoscripagent.xhsagentyunying.ai.agent.tool.ToolContext;
import com.shortvideoscripagent.xhsagentyunying.ai.agent.tool.ToolResult;
import com.shortvideoscripagent.xhsagentyunying.domain.entity.User;
import com.shortvideoscripagent.xhsagentyunying.domain.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class GetUserProfileTool implements AgentTool {

    private final UserMapper userMapper;
    private final ObjectMapper objectMapper;

    @Override
    public String name() {
        return "get_user_profile";
    }

    @Override
    public String description() {
        return "获取当前用户的运营人设与 Agent 偏好设置";
    }

    @Override
    public Map<String, Object> parametersSchema() {
        return Map.of("type", "object", "properties", Map.of());
    }

    @Override
    public ToolResult execute(ToolContext context, Map<String, Object> arguments) {
        User user = userMapper.selectById(context.userId());
        if (user == null) {
            return ToolResult.fail("user_not_found");
        }

        Map<String, Object> profile = new LinkedHashMap<>();
        profile.put("displayName", user.getDisplayName());
        profile.put("defaultPersona", user.getDefaultPersona() == null ? "agency" : user.getDefaultPersona());
        profile.put("dailyAnalysisQuota", user.getDailyQuota());
        profile.put("agentPreferences", parsePreferences(user.getAgentPreferences()));

        return ToolResult.ok(profile);
    }

    private Map<String, Object> parsePreferences(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() {
            });
        } catch (Exception ex) {
            return Map.of();
        }
    }
}
