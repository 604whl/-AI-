package com.shortvideoscripagent.xhsagentyunying.ai.model;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

@Component
public class DashScopeModelProvider implements ModelProvider {

    private final ChatClient chatClient;

    public DashScopeModelProvider(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder.build();
    }

    @Override
    public String id() {
        return "dashscope";
    }

    @Override
    public String chat(String systemPrompt, String userPrompt) {
        return chatClient.prompt()
                .system(systemPrompt)
                .user(userPrompt)
                .call()
                .content();
    }

    @Override
    public boolean supportsVision() {
        return true;
    }
}
