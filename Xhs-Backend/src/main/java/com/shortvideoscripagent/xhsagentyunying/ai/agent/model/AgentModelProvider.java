package com.shortvideoscripagent.xhsagentyunying.ai.agent.model;

import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.tool.ToolCallback;

import java.util.List;

public interface AgentModelProvider {

    AgentLlmResponse chatWithTools(List<Message> messages, List<ToolCallback> toolCallbacks, int timeoutSeconds);

    record AgentLlmResponse(
            String content,
            List<org.springframework.ai.chat.messages.AssistantMessage.ToolCall> toolCalls,
            boolean hasToolCalls
    ) {
    }
}
