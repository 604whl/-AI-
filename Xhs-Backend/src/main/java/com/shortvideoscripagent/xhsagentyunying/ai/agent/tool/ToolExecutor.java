package com.shortvideoscripagent.xhsagentyunying.ai.agent.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shortvideoscripagent.xhsagentyunying.ai.agent.ToolTrace;
import com.shortvideoscripagent.xhsagentyunying.domain.entity.AgentToolLog;
import com.shortvideoscripagent.xhsagentyunying.domain.mapper.AgentToolLogMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class ToolExecutor {

    private final ToolRegistry toolRegistry;
    private final AgentToolLogMapper agentToolLogMapper;
    private final ObjectMapper objectMapper;

    public ToolExecution execute(AssistantMessage.ToolCall toolCall, ToolContext context, List<ToolTrace> traces) {
        long started = System.currentTimeMillis();
        String toolName = toolCall.name();
        Map<String, Object> args = parseArguments(toolCall.arguments());
        ToolResult result = executeDirect(toolName, context, args);
        int latencyMs = (int) (System.currentTimeMillis() - started);
        traces.add(ToolTrace.builder()
                .tool(toolName)
                .success(result.success())
                .latencyMs(latencyMs)
                .error(result.error())
                .build());
        persistLog(context, toolName, args, result, latencyMs);
        return new ToolExecution(toolCall.id(), toolName, result);
    }

    public ToolResult executeDirect(String toolName, ToolContext context, Map<String, Object> args) {
        try {
            AgentTool tool = toolRegistry.requireTool(toolName);
            return tool.execute(context, args == null ? Map.of() : args);
        } catch (Exception ex) {
            log.warn("Tool {} failed: {}", toolName, ex.getMessage());
            return ToolResult.fail(ex.getMessage() == null ? "tool_execution_failed" : ex.getMessage());
        }
    }

    private Map<String, Object> parseArguments(String arguments) {
        if (arguments == null || arguments.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(arguments, new com.fasterxml.jackson.core.type.TypeReference<>() {
            });
        } catch (Exception ex) {
            return Map.of();
        }
    }

    private void persistLog(ToolContext context, String toolName, Map<String, Object> args, ToolResult result, int latencyMs) {
        try {
            AgentToolLog log = new AgentToolLog();
            log.setSessionId(context.sessionId());
            log.setUserId(context.userId());
            log.setToolName(toolName);
            log.setInputJson(objectMapper.writeValueAsString(args));
            log.setOutputSummary(truncate(result.json(), 2000));
            log.setSuccess(result.success());
            log.setLatencyMs(latencyMs);
            log.setCreatedAt(OffsetDateTime.now());
            agentToolLogMapper.insert(log);
        } catch (Exception ex) {
            log.debug("Failed to persist agent tool log: {}", ex.getMessage());
        }
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return "";
        }
        return value.length() <= max ? value : value.substring(0, max);
    }

    public record ToolExecution(String toolCallId, String toolName, ToolResult result) {
    }

    public static List<com.shortvideoscripagent.xhsagentyunying.ai.agent.AgentCard> mergeCards(List<ToolExecution> executions) {
        List<com.shortvideoscripagent.xhsagentyunying.ai.agent.AgentCard> cards = new ArrayList<>();
        for (ToolExecution execution : executions) {
            if (execution.result().cards() != null) {
                cards.addAll(execution.result().cards());
            }
        }
        return cards;
    }
}
