package com.shortvideoscripagent.xhsagentyunying.ai.agent;

import com.shortvideoscripagent.xhsagentyunying.ai.agent.memory.ChatHistoryService;
import com.shortvideoscripagent.xhsagentyunying.ai.agent.model.AgentModelProvider;
import com.shortvideoscripagent.xhsagentyunying.ai.agent.model.AgentModelProviderRegistry;
import com.shortvideoscripagent.xhsagentyunying.ai.agent.model.MockAgentModelProvider;
import com.shortvideoscripagent.xhsagentyunying.ai.agent.tool.ToolExecutor;
import com.shortvideoscripagent.xhsagentyunying.ai.agent.tool.ToolRegistry;
import com.shortvideoscripagent.xhsagentyunying.ai.agent.tool.ToolResult;
import com.shortvideoscripagent.xhsagentyunying.domain.entity.ChatMessage;
import com.shortvideoscripagent.xhsagentyunying.domain.entity.ChatSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AgentOrchestratorTest {

    @Mock
    private AgentRuntimePolicy agentRuntimePolicy;
    @Mock
    private ChatHistoryService chatHistoryService;
    @Mock
    private ToolRegistry toolRegistry;
    @Mock
    private ToolExecutor toolExecutor;
    @Mock
    private AgentModelProviderRegistry agentModelProviderRegistry;

    private AgentOrchestrator agentOrchestrator;
    private final MockAgentModelProvider mockAgentModelProvider = new MockAgentModelProvider();

    @BeforeEach
    void setUp() {
        when(agentRuntimePolicy.maxContextMessages()).thenReturn(20);
        when(agentRuntimePolicy.maxSteps()).thenReturn(8);
        when(agentRuntimePolicy.totalTimeoutSeconds()).thenReturn(120);
        when(agentModelProviderRegistry.getProvider()).thenReturn(mockAgentModelProvider);

        agentOrchestrator = new AgentOrchestrator(
                agentRuntimePolicy,
                chatHistoryService,
                toolRegistry,
                toolExecutor,
                agentModelProviderRegistry
        );
    }

    @Test
    void runMockAgentWithDraftAttachments() {
        ChatSession session = new ChatSession();
        session.setId("sess_test001");
        session.setUserId(1L);
        session.setPersona("agency");
        session.setCreatedAt(OffsetDateTime.now());
        session.setUpdatedAt(OffsetDateTime.now());

        when(chatHistoryService.buildMessages(session, 20)).thenReturn(List.of(
                new UserMessage("""
                        帮我分析这篇笔记

                        【标题】
                        26届英国留学生秋招时间线

                        【正文】
                        正文测试内容，包含秋招焦虑与投递节奏建议。
                        """)
        ));
        when(toolRegistry.toToolCallbacks(any(), any())).thenReturn(List.of());
        when(toolExecutor.execute(any(AssistantMessage.ToolCall.class), any(), anyList())).thenAnswer(invocation -> {
            AssistantMessage.ToolCall toolCall = invocation.getArgument(0);
            return new ToolExecutor.ToolExecution(
                    toolCall.id(),
                    toolCall.name(),
                    ToolResult.ok(Map.of("ok", true))
            );
        });
        when(chatHistoryService.saveAssistantMessage(any(), any(), any(), any())).thenAnswer(invocation -> {
            ChatMessage message = new ChatMessage();
            message.setId(1L);
            message.setRole("assistant");
            message.setContent(invocation.getArgument(1));
            return message;
        });
        when(chatHistoryService.toAgentResponse(any())).thenAnswer(invocation -> {
            ChatMessage message = invocation.getArgument(0);
            return AgentResponse.builder()
                    .messageId(message.getId())
                    .role(message.getRole())
                    .content(message.getContent())
                    .cards(List.of())
                    .toolTraces(List.of())
                    .build();
        });

        AgentRequest request = AgentRequest.builder()
                .userId(1L)
                .sessionId("sess_test001")
                .content("帮我分析这篇笔记")
                .attachments(Map.of(
                        "title", "26届英国留学生秋招时间线",
                        "body", "正文测试内容，包含秋招焦虑与投递节奏建议。"
                ))
                .build();

        AgentResponse response = agentOrchestrator.run(session, request);
        assertNotNull(response);
        assertFalse(response.content().isBlank());
    }
}
