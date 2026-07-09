package com.shortvideoscripagent.xhsagentyunying.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shortvideoscripagent.xhsagentyunying.ai.model.ModelProvider;
import com.shortvideoscripagent.xhsagentyunying.ai.model.OpenAiCompatibleHttpModelProvider;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenAiConfig {

    @Bean
    @ConditionalOnProperty(name = "app.ai.openai.enabled", havingValue = "true")
    public OpenAiChatModel openAiChatModel(
            @Value("${spring.ai.openai.api-key}") String apiKey,
            @Value("${spring.ai.openai.base-url}") String baseUrl,
            @Value("${spring.ai.openai.chat.options.model}") String model) {
        OpenAiApi api = OpenAiApi.builder()
                .apiKey(apiKey)
                .baseUrl(baseUrl)
                .build();
        OpenAiChatOptions options = OpenAiChatOptions.builder()
                .model(model)
                .build();
        return OpenAiChatModel.builder()
                .openAiApi(api)
                .defaultOptions(options)
                .build();
    }

    @Bean
    @ConditionalOnProperty(name = "app.ai.deepseek.enabled", havingValue = "true")
    public ModelProvider deepseekModelProvider(AppAiProperties appProperties, ObjectMapper objectMapper) {
        AppAiProperties.CompatibleProvider props = appProperties.getAi().getDeepseek();
        return new OpenAiCompatibleHttpModelProvider(
                "deepseek",
                props.getApiKey(),
                props.getBaseUrl(),
                props.getModel(),
                objectMapper
        );
    }

    @Bean
    @ConditionalOnProperty(name = "app.ai.zhipu.enabled", havingValue = "true")
    public ModelProvider zhipuModelProvider(AppAiProperties appProperties, ObjectMapper objectMapper) {
        AppAiProperties.CompatibleProvider props = appProperties.getAi().getZhipu();
        return new OpenAiCompatibleHttpModelProvider(
                "zhipu",
                props.getApiKey(),
                props.getBaseUrl(),
                props.getModel(),
                objectMapper
        );
    }
}
