package com.shortvideoscripagent.xhsagentyunying.ai.model;

import com.shortvideoscripagent.xhsagentyunying.common.exception.BusinessException;
import com.shortvideoscripagent.xhsagentyunying.config.AppAiProperties;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

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
}
