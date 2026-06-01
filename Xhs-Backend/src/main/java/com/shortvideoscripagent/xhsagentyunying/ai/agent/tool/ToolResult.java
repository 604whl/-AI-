package com.shortvideoscripagent.xhsagentyunying.ai.agent.tool;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Builder;

import java.util.List;
import java.util.Map;

@Builder
public record ToolResult(
        boolean success,
        String json,
        List<com.shortvideoscripagent.xhsagentyunying.ai.agent.AgentCard> cards,
        String linkedTaskId,
        String error
) {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static ToolResult ok(Map<String, Object> data) {
        return ToolResult.builder()
                .success(true)
                .json(toJson(data))
                .build();
    }

    public static ToolResult ok(String json) {
        return ToolResult.builder().success(true).json(json).build();
    }

    public static ToolResult ok(Map<String, Object> data, List<com.shortvideoscripagent.xhsagentyunying.ai.agent.AgentCard> cards) {
        return ToolResult.builder()
                .success(true)
                .json(toJson(data))
                .cards(cards)
                .build();
    }

    public static ToolResult ok(Map<String, Object> data, List<com.shortvideoscripagent.xhsagentyunying.ai.agent.AgentCard> cards, String linkedTaskId) {
        return ToolResult.builder()
                .success(true)
                .json(toJson(data))
                .cards(cards)
                .linkedTaskId(linkedTaskId)
                .build();
    }

    public static ToolResult fail(String error) {
        return ToolResult.builder()
                .success(false)
                .json(toJson(Map.of("error", error)))
                .error(error)
                .build();
    }

    private static String toJson(Map<String, Object> data) {
        try {
            return MAPPER.writeValueAsString(data);
        } catch (JsonProcessingException ex) {
            return "{\"error\":\"json_serialize_failed\"}";
        }
    }
}
