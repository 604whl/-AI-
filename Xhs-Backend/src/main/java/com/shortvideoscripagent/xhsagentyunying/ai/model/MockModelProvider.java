package com.shortvideoscripagent.xhsagentyunying.ai.model;

import com.shortvideoscripagent.xhsagentyunying.common.exception.BusinessException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Mock 模式下占位 {@link ModelProvider}，避免注入 DashScope {@code ChatClient}。
 * 业务层在 mock 模式下应走 {@code SampleAnalysisReport}，不应调用本类。
 */
@Component
@ConditionalOnProperty(name = "app.ai.mock-enabled", havingValue = "true")
public class MockModelProvider implements ModelProvider {

    private static final int CODE_AI_UNAVAILABLE = 50002;

    @Override
    public String id() {
        return "dashscope";
    }

    @Override
    public String modelName() {
        return "mock";
    }

    @Override
    public String chat(String systemPrompt, String userPrompt) {
        throw new BusinessException(CODE_AI_UNAVAILABLE, "ai_mock_mode: LLM chat is disabled");
    }

    @Override
    public String chatWithImage(String systemPrompt, String userPrompt, byte[] imageBytes, String mimeType) {
        throw new BusinessException(CODE_AI_UNAVAILABLE, "ai_mock_mode: vision chat is disabled");
    }

    @Override
    public boolean supportsVision() {
        return true;
    }
}
