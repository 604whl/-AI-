package com.shortvideoscripagent.xhsagentyunying.config;

import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
@ConditionalOnProperty(name = "app.ai.mock-enabled", havingValue = "false")
public class VisionChatClientConfig {

    @Bean
    @Primary
    public ChatClient chatClient(ChatClient.Builder chatClientBuilder) {
        return chatClientBuilder.build();
    }

    @Bean("visionChatClient")
    public ChatClient visionChatClient(
            ChatClient.Builder chatClientBuilder,
            @Value("${app.ai.dashscope.vision-model:${DASHSCOPE_VISION_MODEL:qwen-vl-plus}}") String visionModel) {
        DashScopeChatOptions options = DashScopeChatOptions.builder()
                .withModel(visionModel)
                .build();
        return chatClientBuilder
                .defaultOptions(options)
                .build();
    }
}
