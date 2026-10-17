package com.shortvideoscripagent.xhsagentyunying.ai.model;

import com.shortvideoscripagent.xhsagentyunying.config.AiRuntimeProperties;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * DashScope 文本模型模板，默认关闭。启用时与 DeepSeek 共用同一套 {@link ModelProvider} 文本调用。
 */
@Component
@ConditionalOnProperty(name = "app.ai.mock-enabled", havingValue = "false")
@ConditionalOnProperty(name = "app.ai.dashscope.enabled", havingValue = "true")
public class DashScopeModelProvider implements ModelProvider {

    private final ChatClient chatClient;
    private final AiRuntimeProperties aiRuntimeProperties;

    public DashScopeModelProvider(ChatClient chatClient, AiRuntimeProperties aiRuntimeProperties) {
        this.chatClient = chatClient;
        this.aiRuntimeProperties = aiRuntimeProperties;
    }

    @Override
    public String id() {
        return "dashscope";
    }

    @Override
    public String modelName() {
        return aiRuntimeProperties.getDashscopeChatModel();
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
