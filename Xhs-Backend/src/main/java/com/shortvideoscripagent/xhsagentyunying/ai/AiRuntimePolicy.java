package com.shortvideoscripagent.xhsagentyunying.ai;

import com.shortvideoscripagent.xhsagentyunying.common.exception.BusinessException;
import com.shortvideoscripagent.xhsagentyunying.config.AiRuntimeProperties;
import com.shortvideoscripagent.xhsagentyunying.config.AppAiProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

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
        for (String providerId : configuredProviderIds()) {
            if (hasProviderKey(providerId)) {
                return true;
            }
        }
        return false;
    }

    private boolean hasProviderKey(String providerId) {
        return switch (providerId) {
            case "dashscope" -> usableKey(aiRuntimeProperties.getDashscopeApiKey());
            case "openai" -> appProperties.getAi().getOpenai().isEnabled()
                    && usableKey(aiRuntimeProperties.getOpenaiApiKey());
            case "deepseek" -> appProperties.getAi().getDeepseek().isEnabled()
                    && usableKey(appProperties.getAi().getDeepseek().getApiKey());
            case "zhipu" -> appProperties.getAi().getZhipu().isEnabled()
                    && usableKey(appProperties.getAi().getZhipu().getApiKey());
            default -> false;
        };
    }

    private List<String> configuredProviderIds() {
        List<String> ids = new ArrayList<>();
        addIfPresent(ids, appProperties.getAi().getDefaultProvider());
        for (String id : appProperties.getAi().getFallbackProviders()) {
            addIfPresent(ids, id);
        }
        return ids;
    }

    private static boolean usableKey(String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            return false;
        }
        String trimmed = apiKey.trim();
        return !trimmed.startsWith("sk-placeholder")
                && !trimmed.contains("在此填写")
                && !"placeholder".equalsIgnoreCase(trimmed);
    }

    private static void addIfPresent(List<String> ids, String id) {
        if (id == null || id.isBlank()) {
            return;
        }
        String normalized = id.trim();
        if (!ids.contains(normalized)) {
            ids.add(normalized);
        }
    }

    public void assertRealAiAvailable() {
        if (useMockResponses()) {
            return;
        }
        if (!hasRealApiKey()) {
            throw new BusinessException(
                    CODE_AI_UNAVAILABLE,
                    "ai_not_configured: set DASHSCOPE_API_KEY, DEEPSEEK_API_KEY, ZHIPU_API_KEY, or AI_MOCK_ENABLED=true"
            );
        }
    }

    public String dashscopeModel() {
        return aiRuntimeProperties.getDashscopeChatModel();
    }
}
