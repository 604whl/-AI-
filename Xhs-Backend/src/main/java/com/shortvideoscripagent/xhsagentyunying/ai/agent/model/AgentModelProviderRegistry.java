package com.shortvideoscripagent.xhsagentyunying.ai.agent.model;

import com.shortvideoscripagent.xhsagentyunying.ai.agent.AgentRuntimePolicy;
import com.shortvideoscripagent.xhsagentyunying.common.exception.BusinessException;
import com.shortvideoscripagent.xhsagentyunying.config.AppAiProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AgentModelProviderRegistry {

    private static final int CODE_AI_UNAVAILABLE = 50002;

    private final MockAgentModelProvider mockAgentModelProvider;
    private final ObjectProvider<OpenAiAgentModelProvider> openAiAgentModelProvider;
    private final ObjectProvider<DashScopeAgentModelProvider> dashScopeAgentModelProvider;
    private final AgentRuntimePolicy agentRuntimePolicy;
    private final AppAiProperties appAiProperties;

    public AgentModelProvider getProvider() {
        if (agentRuntimePolicy.useMockAgent()) {
            return mockAgentModelProvider;
        }
        String primary = appAiProperties.getAi().getDefaultProvider();
        if ("openai".equalsIgnoreCase(primary)) {
            OpenAiAgentModelProvider openAi = openAiAgentModelProvider.getIfAvailable();
            if (openAi != null) {
                return openAi;
            }
        }
        if ("dashscope".equalsIgnoreCase(primary)) {
            DashScopeAgentModelProvider dashScope = dashScopeAgentModelProvider.getIfAvailable();
            if (dashScope != null) {
                return dashScope;
            }
        }
        DashScopeAgentModelProvider dashScopeFallback = dashScopeAgentModelProvider.getIfAvailable();
        if (dashScopeFallback != null) {
            return dashScopeFallback;
        }
        OpenAiAgentModelProvider openAiFallback = openAiAgentModelProvider.getIfAvailable();
        if (openAiFallback != null) {
            return openAiFallback;
        }
        throw new BusinessException(CODE_AI_UNAVAILABLE, "ai_service_unavailable: no agent model provider configured");
    }
}
