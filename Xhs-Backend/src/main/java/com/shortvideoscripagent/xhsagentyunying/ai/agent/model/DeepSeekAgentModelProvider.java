package com.shortvideoscripagent.xhsagentyunying.ai.agent.model;

import com.shortvideoscripagent.xhsagentyunying.ai.AiRuntimePolicy;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@ConditionalOnProperty(name = "app.ai.mock-enabled", havingValue = "false")
@ConditionalOnProperty(name = "app.ai.deepseek.enabled", havingValue = "true")
public class DeepSeekAgentModelProvider implements AgentModelProvider {

    private final ChatModel chatModel;
    private final AiRuntimePolicy aiRuntimePolicy;

    public DeepSeekAgentModelProvider(
            @Qualifier("deepSeekChatModel") ChatModel chatModel,
            AiRuntimePolicy aiRuntimePolicy
    ) {
        this.chatModel = chatModel;
        this.aiRuntimePolicy = aiRuntimePolicy;
    }

    @Override
    public AgentLlmResponse chatWithTools(List<Message> messages, List<ToolCallback> toolCallbacks, int timeoutSeconds) {
        return AgentModelProviderSupport.chatWithTools(chatModel, aiRuntimePolicy, messages, toolCallbacks, timeoutSeconds);
    }
}
