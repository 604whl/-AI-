package com.shortvideoscripagent.xhsagentyunying.ai.mcp;

import com.shortvideoscripagent.xhsagentyunying.ai.agent.tool.AgentTool;
import com.shortvideoscripagent.xhsagentyunying.ai.agent.tool.ToolExecutor;
import com.shortvideoscripagent.xhsagentyunying.ai.agent.tool.ToolRegistry;
import com.shortvideoscripagent.xhsagentyunying.ai.agent.tool.ToolResult;
import com.shortvideoscripagent.xhsagentyunying.config.AppAgentProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.tool.ToolCallback;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * {@link McpAgentToolCallbackProvider} 单元测试：验证暴露工具列表与 MCP 调用桥接。
 */
@ExtendWith(MockitoExtension.class)
class McpAgentToolCallbackProviderTest {

    @Mock
    private ToolRegistry toolRegistry;
    @Mock
    private ToolExecutor toolExecutor;

    private AppAgentProperties appAgentProperties;
    private McpAgentToolCallbackProvider provider;

    @BeforeEach
    void setUp() {
        appAgentProperties = new AppAgentProperties();
        appAgentProperties.getMcp().setEnabled(true);
        appAgentProperties.getMcp().setExposedTools(List.of("search_kb", "web_search", "disabled_tool"));
        appAgentProperties.getTools().setSearchKb(true);
        appAgentProperties.getTools().setWebSearch(true);
        appAgentProperties.getTools().setAnalyzeContent(false);

        provider = new McpAgentToolCallbackProvider(toolRegistry, toolExecutor, appAgentProperties, new com.fasterxml.jackson.databind.ObjectMapper());

        when(toolRegistry.requireTool("search_kb")).thenReturn(stubTool("search_kb", "检索知识库"));
        when(toolRegistry.requireTool("web_search")).thenReturn(stubTool("web_search", "联网搜索"));
    }

    @AfterEach
    void tearDown() {
        McpRuntimeContext.clear();
    }

    @Test
    void exposesOnlyEnabledToolsFromConfig() {
        ToolCallback[] callbacks = provider.getToolCallbacks();
        assertEquals(2, callbacks.length);
        assertTrue(List.of(callbacks).stream().anyMatch(cb -> "search_kb".equals(cb.getToolDefinition().name())));
        assertTrue(List.of(callbacks).stream().anyMatch(cb -> "web_search".equals(cb.getToolDefinition().name())));
    }

    @Test
    void callDelegatesToToolExecutorWithMcpContext() {
        McpRuntimeContext.bind(42L, "mcp_test_session", "agency");
        when(toolExecutor.executeDirect(eq("search_kb"), any(), any()))
                .thenReturn(ToolResult.ok(Map.of("chunks", List.of())));

        ToolCallback searchKb = List.of(provider.getToolCallbacks()).stream()
                .filter(cb -> "search_kb".equals(cb.getToolDefinition().name()))
                .findFirst()
                .orElseThrow();

        String json = searchKb.call("{\"query\":\"秋招时间线\",\"topK\":3}");
        assertTrue(json.contains("chunks"));
    }

    private static AgentTool stubTool(String name, String description) {
        return new AgentTool() {
            @Override
            public String name() {
                return name;
            }

            @Override
            public String description() {
                return description;
            }

            @Override
            public Map<String, Object> parametersSchema() {
                return Map.of("type", "object", "properties", Map.of("query", Map.of("type", "string")));
            }

            @Override
            public ToolResult execute(com.shortvideoscripagent.xhsagentyunying.ai.agent.tool.ToolContext context,
                                      Map<String, Object> arguments) {
                return ToolResult.ok(Map.of());
            }
        };
    }
}
