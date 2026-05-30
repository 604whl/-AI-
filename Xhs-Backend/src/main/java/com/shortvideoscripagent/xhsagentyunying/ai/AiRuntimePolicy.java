package com.shortvideoscripagent.xhsagentyunying.ai;

import com.shortvideoscripagent.xhsagentyunying.common.exception.BusinessException;
import com.shortvideoscripagent.xhsagentyunying.config.AiRuntimeProperties;
import com.shortvideoscripagent.xhsagentyunying.config.AppAiProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 统一判断 Mock AI 与真实 DashScope 调用；{@code AI_MOCK_ENABLED=false} 时不再因缺 Key 静默回退 Mock。
 */
@Component
@RequiredArgsConstructor
public class AiRuntimePolicy {

    private static final int CODE_AI_UNAVAILABLE = 50002;

    private final AppAiProperties appProperties;
    private final AiRuntimeProperties aiRuntimeProperties;

    public boolean useMockResponses() {
        return appProperties.getAi().isMockEnabled();
    }

    public boolean hasRealApiKey() {
        String apiKey = aiRuntimeProperties.getDashscopeApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            return false;
        }
        String trimmed = apiKey.trim();
        return !trimmed.startsWith("sk-placeholder")
                && !"placeholder".equalsIgnoreCase(trimmed);
    }

    public void assertRealAiAvailable() {
        if (useMockResponses()) {
            return;
        }
        if (!hasRealApiKey()) {
            throw new BusinessException(
                    CODE_AI_UNAVAILABLE,
                    "ai_not_configured: set DASHSCOPE_API_KEY or AI_MOCK_ENABLED=true"
            );
        }
    }

    public String dashscopeModel() {
        return aiRuntimeProperties.getDashscopeChatModel();
    }
}
