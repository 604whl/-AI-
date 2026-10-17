package com.shortvideoscripagent.xhsagentyunying.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * DashScope ChatClient 模板：仅在 {@code app.ai.dashscope.enabled=true} 时生效。
 * 默认生产路径使用 DeepSeek，不创建视觉多模态客户端。
 */
@Configuration
@ConditionalOnProperty(name = "app.ai.mock-enabled", havingValue = "false")
@ConditionalOnProperty(name = "app.ai.dashscope.enabled", havingValue = "true")
public class DashScopeChatClientConfig {

    @Bean
    @Primary
    public ChatClient chatClient(ChatClient.Builder chatClientBuilder) {
        return chatClientBuilder.build();
    }
}
