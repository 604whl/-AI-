package com.shortvideoscripagent.xhsagentyunying.ai.model;

import com.shortvideoscripagent.xhsagentyunying.common.exception.BusinessException;
import com.shortvideoscripagent.xhsagentyunying.config.AppAiProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Mock 模式下占位 {@link ModelProvider}。业务层在 mock 模式下应走 {@code SampleAnalysisReport}。
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.ai.mock-enabled", havingValue = "true")
public class MockModelProvider implements ModelProvider {

    private static final int CODE_AI_UNAVAILABLE = 50002;

    private final AppAiProperties appProperties;

    @Override
    public String id() {
        String provider = appProperties.getAi().getDefaultProvider();
        return provider == null || provider.isBlank() ? "deepseek" : provider.trim();
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
    public boolean supportsVision() {
        return false;
    }
}
