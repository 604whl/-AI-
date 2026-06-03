package com.shortvideoscripagent.xhsagentyunying.ai.mcp;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shortvideoscripagent.xhsagentyunying.ai.agent.tool.AgentTool;
import com.shortvideoscripagent.xhsagentyunying.ai.agent.tool.ToolExecutor;
import com.shortvideoscripagent.xhsagentyunying.ai.agent.tool.ToolRegistry;
import com.shortvideoscripagent.xhsagentyunying.config.AppAgentProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 将内部 {@link AgentTool} 桥接为 Spring AI {@link ToolCallback}，供 MCP Server 自动发布。
 * <p>
 * Spring AI MCP Starter 的 {@code ToolCallbackConverterAutoConfiguration} 会收集容器中所有
 * {@link ToolCallbackProvider}，把返回的 {@link ToolCallback} 注册为 MCP {@code tools/list}
 * 与 {@code tools/call} 的处理目标。
 * </p>
 *
 * <h3>暴露范围</h3>
 * 仅 {@link AppAgentProperties#getExposedToolNames()} 中配置、且
 * {@link AppAgentProperties#isToolEnabled(String)} 为 true 的工具会被发布。
 *
 * <h3>执行链路</h3>
 * <pre>
 * MCP Client → POST /api/mcp → McpAuthFilter 绑定上下文
 *           → MCP tools/call → ToolCallback.call(json)
 *           → ToolExecutor.executeDirect → AgentTool.execute
 * </pre>
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.agent.mcp", name = "enabled", havingValue = "true")
public class McpAgentToolCallbackProvider implements ToolCallbackProvider {

    private final ToolRegistry toolRegistry;
    private final ToolExecutor toolExecutor;
    private final AppAgentProperties appAgentProperties;
    private final ObjectMapper objectMapper;

    @Override
    public ToolCallback[] getToolCallbacks() {
        List<ToolCallback> callbacks = new ArrayList<>();
        for (String toolName : appAgentProperties.getExposedToolNames()) {
            if (!appAgentProperties.isToolEnabled(toolName)) {
                log.debug("MCP skip tool {}: disabled in app.agent.tools", toolName);
                continue;
            }
            try {
                AgentTool tool = toolRegistry.requireTool(toolName);
                callbacks.add(toMcpCallback(tool));
            } catch (IllegalArgumentException ex) {
                log.warn("MCP skip tool {}: {}", toolName, ex.getMessage());
            }
        }
        return callbacks.toArray(new ToolCallback[0]);
    }

    /**
     * 为单个 AgentTool 构建 MCP 可调用的 ToolCallback。
     * schema 与 Chat Agent 共用 {@link AgentTool#parametersSchema()}，保证参数一致。
     */
    private ToolCallback toMcpCallback(AgentTool tool) {
        String schemaJson = schemaJson(tool.parametersSchema());
        ToolDefinition definition = ToolDefinition.builder()
                .name(tool.name())
                .description(tool.description())
                .inputSchema(schemaJson)
                .build();

        return new ToolCallback() {
            @Override
            public ToolDefinition getToolDefinition() {
                return definition;
            }

            /**
             * MCP 客户端传入 JSON 字符串参数；在此解析并委派给 ToolExecutor。
             * 用户身份来自 {@link McpRuntimeContext}（由 {@link McpAuthFilter} 注入）。
             */
            @Override
            public String call(String toolInput) {
                Map<String, Object> args = parseArgs(toolInput);
                return toolExecutor.executeDirect(tool.name(), McpRuntimeContext.toToolContext(), args).json();
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
            log.warn("MCP tool args parse failed, use empty map: {}", ex.getMessage());
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
