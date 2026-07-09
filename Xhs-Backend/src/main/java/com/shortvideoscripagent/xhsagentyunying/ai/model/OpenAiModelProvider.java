package com.shortvideoscripagent.xhsagentyunying.ai.model;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 可选 OpenAI 兼容提供商（DeepSeek 等可配置 base-url）。
 */
@Component
@ConditionalOnProperty(name = "app.ai.openai.enabled", havingValue = "true")
@ConditionalOnBean(OpenAiChatModel.class)
public class OpenAiModelProvider implements ModelProvider {

    private final ChatClient chatClient;
    private final String modelName;

    public OpenAiModelProvider(
            OpenAiChatModel openAiChatModel,
            @Value("${spring.ai.openai.chat.options.model:gpt-4o-mini}") String modelName
    ) {
        this.chatClient = ChatClient.builder(openAiChatModel).build();
        this.modelName = modelName;
    }

    @Override
    public String id() {
        return "openai";
    }

    @Override
    public String modelName() {
        return modelName;
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
        return false;
    }
}
