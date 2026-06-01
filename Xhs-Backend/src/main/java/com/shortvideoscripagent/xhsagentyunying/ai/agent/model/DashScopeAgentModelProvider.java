package com.shortvideoscripagent.xhsagentyunying.ai.agent.model;

import com.shortvideoscripagent.xhsagentyunying.ai.AiRuntimePolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
public class DashScopeAgentModelProvider implements AgentModelProvider {

    private final ChatModel chatModel;
    private final AiRuntimePolicy aiRuntimePolicy;

    @Override
    public AgentLlmResponse chatWithTools(List<Message> messages, List<ToolCallback> toolCallbacks, int timeoutSeconds) {
        aiRuntimePolicy.assertRealAiAvailable();

        ToolCallingChatOptions options = ToolCallingChatOptions.builder()
                .toolCallbacks(toolCallbacks)
                .internalToolExecutionEnabled(false)
                .build();

        Prompt prompt = new Prompt(messages, options);
        ChatResponse response = CompletableFuture
                .supplyAsync(() -> chatModel.call(prompt))
                .orTimeout(timeoutSeconds, TimeUnit.SECONDS)
                .join();

        AssistantMessage output = response.getResult().getOutput();
        List<AssistantMessage.ToolCall> toolCalls = output.getToolCalls();
        boolean hasToolCalls = toolCalls != null && !toolCalls.isEmpty();
        return new AgentLlmResponse(output.getText(), hasToolCalls ? toolCalls : List.of(), hasToolCalls);
    }
}
