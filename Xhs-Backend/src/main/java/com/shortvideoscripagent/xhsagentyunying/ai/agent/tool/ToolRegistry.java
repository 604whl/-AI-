package com.shortvideoscripagent.xhsagentyunying.ai.agent.tool;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shortvideoscripagent.xhsagentyunying.config.AppAgentProperties;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class ToolRegistry {

    private final Map<String, AgentTool> toolsByName;
    private final AppAgentProperties appAgentProperties;
    private final ObjectMapper objectMapper;

    public ToolRegistry(List<AgentTool> tools, AppAgentProperties appAgentProperties, ObjectMapper objectMapper) {
        this.toolsByName = tools.stream().collect(Collectors.toMap(AgentTool::name, Function.identity(), (a, b) -> a));
        this.appAgentProperties = appAgentProperties;
        this.objectMapper = objectMapper;
    }

    public List<AgentTool> enabledTools() {
        return toolsByName.values().stream()
                .filter(tool -> appAgentProperties.isToolEnabled(tool.name()))
                .toList();
    }

    public AgentTool requireTool(String name) {
        AgentTool tool = toolsByName.get(name);
        if (tool == null || !appAgentProperties.isToolEnabled(name)) {
            throw new IllegalArgumentException("tool_not_enabled: " + name);
        }
        return tool;
    }

    public List<ToolCallback> toToolCallbacks(ToolContext context, ToolExecutor executor) {
        return enabledTools().stream()
                .map(tool -> toCallback(tool, context, executor))
                .toList();
    }

    private ToolCallback toCallback(AgentTool tool, ToolContext context, ToolExecutor executor) {
        String schema = schemaJson(tool.parametersSchema());
        ToolDefinition definition = ToolDefinition.builder()
                .name(tool.name())
                .description(tool.description())
                .inputSchema(schema)
                .build();

        return new ToolCallback() {
            @Override
            public ToolDefinition getToolDefinition() {
                return definition;
            }

            @Override
            public String call(String toolInput) {
                Map<String, Object> args = parseArgs(toolInput);
                return executor.executeDirect(tool.name(), context, args).json();
            }
        };
    }

    private Map<String, Object> parseArgs(String toolInput) {
        if (toolInput == null || toolInput.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(toolInput, new TypeReference<>() {
            });
        } catch (Exception ex) {
            return Map.of();
        }
    }

    private String schemaJson(Map<String, Object> schema) {
        try {
            return objectMapper.writeValueAsString(schema);
        } catch (Exception ex) {
            return "{\"type\":\"object\",\"properties\":{}}";
        }
    }
}
