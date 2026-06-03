package com.shortvideoscripagent.xhsagentyunying.ai.agent;

import com.shortvideoscripagent.xhsagentyunying.ai.agent.memory.ChatHistoryService;
import com.shortvideoscripagent.xhsagentyunying.ai.agent.model.AgentModelProvider;
import com.shortvideoscripagent.xhsagentyunying.ai.agent.model.AgentModelProviderRegistry;
import com.shortvideoscripagent.xhsagentyunying.ai.agent.tool.ToolContext;
import com.shortvideoscripagent.xhsagentyunying.ai.agent.tool.ToolExecutor;
import com.shortvideoscripagent.xhsagentyunying.ai.agent.tool.ToolRegistry;
import com.shortvideoscripagent.xhsagentyunying.common.exception.BusinessException;
import com.shortvideoscripagent.xhsagentyunying.domain.entity.ChatMessage;
import com.shortvideoscripagent.xhsagentyunying.domain.entity.ChatSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class AgentOrchestrator {

    private static final int CODE_AGENT_TIMEOUT = 50402;
    private static final int CONTENT_DELTA_CHUNK_SIZE = 48;

    private final AgentRuntimePolicy agentRuntimePolicy;
    private final ChatHistoryService chatHistoryService;
    private final ToolRegistry toolRegistry;
    private final ToolExecutor toolExecutor;
    private final AgentModelProviderRegistry agentModelProviderRegistry;

    public AgentResponse run(ChatSession session, AgentRequest request) {
        return run(session, request, AgentStreamListener.NOOP);
    }

    public AgentResponse run(ChatSession session, AgentRequest request, AgentStreamListener listener) {
        AgentStreamListener stream = listener == null ? AgentStreamListener.NOOP : listener;
        chatHistoryService.saveUserMessage(session, request);

        ToolContext toolContext = buildToolContext(session, request);
        List<Message> messages = new ArrayList<>(chatHistoryService.buildMessages(session, agentRuntimePolicy.maxContextMessages()));
        List<ToolTrace> traces = new ArrayList<>();
        List<AgentCard> cards = new ArrayList<>();
        AgentModelProvider provider = agentModelProviderRegistry.getProvider();

        long deadline = System.currentTimeMillis() + agentRuntimePolicy.totalTimeoutSeconds() * 1000L;
        int perStepTimeout = Math.max(agentRuntimePolicy.totalTimeoutSeconds() / agentRuntimePolicy.maxSteps(), 15);
        int maxSteps = agentRuntimePolicy.maxSteps();

        for (int step = 0; step < maxSteps; step++) {
            if (System.currentTimeMillis() >= deadline) {
                throw new BusinessException(CODE_AGENT_TIMEOUT, "agent_timeout");
            }

            stream.onStepStart(step + 1, maxSteps);

            var llm = provider.chatWithTools(
                    messages,
                    toolRegistry.toToolCallbacks(toolContext, toolExecutor),
                    perStepTimeout
            );

            if (!llm.hasToolCalls()) {
                ChatMessage saved = chatHistoryService.saveAssistantMessage(
                        session,
                        llm.content(),
                        cards.isEmpty() ? null : cards,
                        traces.isEmpty() ? null : traces
                );
                AgentResponse response = chatHistoryService.toAgentResponse(saved);
                emitContentDeltas(stream, response.content());
                stream.onDone(response);
                return response;
            }

            AssistantMessage assistantMessage = AssistantMessage.builder()
                    .content(llm.content())
                    .toolCalls(llm.toolCalls())
                    .build();
            List<ToolResponseMessage.ToolResponse> toolResponses = new ArrayList<>();

            for (AssistantMessage.ToolCall toolCall : llm.toolCalls()) {
                String toolName = toolCall.name();
                stream.onToolStart(toolName, step + 1);
                long toolStarted = System.currentTimeMillis();
                ToolExecutor.ToolExecution execution = toolExecutor.execute(toolCall, toolContext, traces);
                long latencyMs = System.currentTimeMillis() - toolStarted;
                boolean success = execution.result().success();
                String error = success ? null : execution.result().error();
                stream.onToolEnd(toolName, success, latencyMs, error);

                toolResponses.add(new ToolResponseMessage.ToolResponse(
                        execution.toolCallId(),
                        execution.toolName(),
                        execution.result().json()
                ));
                if (execution.result().cards() != null) {
                    cards.addAll(execution.result().cards());
                }
                if (execution.result().linkedTaskId() != null) {
                    chatHistoryService.updateLinkedTaskId(session, execution.result().linkedTaskId());
                    toolContext = buildToolContext(session, request);
                }
            }

            chatHistoryService.saveToolExchange(session, assistantMessage, toolResponses);
            messages.add(assistantMessage);
            messages.add(ToolResponseMessage.builder().responses(toolResponses).build());
        }

        throw new BusinessException(CODE_AGENT_TIMEOUT, "agent_timeout");
    }

    private static void emitContentDeltas(AgentStreamListener stream, String content) {
        if (content == null || content.isBlank()) {
            return;
        }
        for (int i = 0; i < content.length(); i += CONTENT_DELTA_CHUNK_SIZE) {
            stream.onDelta(content.substring(i, Math.min(i + CONTENT_DELTA_CHUNK_SIZE, content.length())));
        }
    }

    private ToolContext buildToolContext(ChatSession session, AgentRequest request) {
        return new ToolContext(
                session.getUserId(),
                session.getId(),
                session.getPersona(),
                session.getLinkedTaskId(),
                request.attachments() == null ? Map.of() : request.attachments()
        );
    }
}
