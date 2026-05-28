package com.shortvideoscripagent.xhsagentyunying.config;

import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Data
@Component
public class AiRuntimeProperties {

    @Value("${spring.ai.dashscope.api-key:}")
    private String dashscopeApiKey;

    @Value("${spring.ai.dashscope.chat.options.model:qwen-plus}")
    private String dashscopeChatModel;
}
