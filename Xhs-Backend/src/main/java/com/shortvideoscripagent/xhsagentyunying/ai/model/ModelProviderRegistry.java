package com.shortvideoscripagent.xhsagentyunying.ai.model;

import com.shortvideoscripagent.xhsagentyunying.common.exception.BusinessException;
import com.shortvideoscripagent.xhsagentyunying.config.AppAiProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Component
public class ModelProviderRegistry {

    private final Map<String, ModelProvider> providers;
    private final AppAiProperties appProperties;

    public ModelProviderRegistry(List<ModelProvider> providerList, AppAiProperties appProperties) {
        this.providers = providerList.stream()
                .collect(Collectors.toMap(ModelProvider::id, Function.identity()));
        this.appProperties = appProperties;
    }

    public ModelProvider getDefault() {
        return getRequired(appProperties.getAi().getDefaultProvider());
    }

    public ModelProvider getRequired(String id) {
        ModelProvider provider = providers.get(id);
        if (provider == null) {
            throw new BusinessException(50002, "ai_service_unavailable: provider " + id);
        }
        return provider;
    }

    public ChatResult chatWithFallback(String systemPrompt, String userPrompt) {
        List<ModelProvider> candidates = textProvidersInOrder();
        RuntimeException lastError = null;
        for (ModelProvider provider : candidates) {
            try {
                String content = provider.chat(systemPrompt, userPrompt);
                return new ChatResult(provider.id(), provider.modelName(), content);
            } catch (RuntimeException ex) {
                lastError = ex;
                log.warn("LLM provider {} failed, trying next provider if available: {}", provider.id(), ex.getMessage());
            }
        }
        if (lastError != null) {
            throw lastError;
        }
        throw new BusinessException(50002, "ai_service_unavailable");
    }

    private List<ModelProvider> textProvidersInOrder() {
        List<String> ids = new ArrayList<>();
        addIfPresent(ids, appProperties.getAi().getDefaultProvider());
        for (String id : appProperties.getAi().getFallbackProviders()) {
            addIfPresent(ids, id);
        }
        List<ModelProvider> ordered = ids.stream()
                .map(providers::get)
                .filter(provider -> provider != null)
                .toList();
        if (ordered.isEmpty()) {
            throw new BusinessException(50002, "ai_service_unavailable");
        }
        return ordered;
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

    public record ChatResult(String providerId, String modelName, String content) {
    }
}
