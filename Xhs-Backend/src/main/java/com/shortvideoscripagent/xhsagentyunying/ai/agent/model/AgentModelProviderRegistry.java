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
    private final ObjectProvider<DeepSeekAgentModelProvider> deepSeekAgentModelProvider;
    private final ObjectProvider<OpenAiAgentModelProvider> openAiAgentModelProvider;
    private final ObjectProvider<DashScopeAgentModelProvider> dashScopeAgentModelProvider;
    private final AgentRuntimePolicy agentRuntimePolicy;
    private final AppAiProperties appAiProperties;

    public AgentModelProvider getProvider() {
        if (agentRuntimePolicy.useMockAgent()) {
            return mockAgentModelProvider;
        }
        String primary = appAiProperties.getAi().getDefaultProvider();
        AgentModelProvider matched = resolve(primary);
        if (matched != null) {
            return matched;
        }
        AgentModelProvider deepSeek = deepSeekAgentModelProvider.getIfAvailable();
        if (deepSeek != null) {
            return deepSeek;
        }
        AgentModelProvider openAi = openAiAgentModelProvider.getIfAvailable();
        if (openAi != null) {
            return openAi;
        }
        AgentModelProvider dashScope = dashScopeAgentModelProvider.getIfAvailable();
        if (dashScope != null) {
            return dashScope;
        }
        throw new BusinessException(CODE_AI_UNAVAILABLE, "ai_service_unavailable: no agent model provider configured");
    }

    private AgentModelProvider resolve(String primary) {
        if (primary == null) {
            return null;
        }
        return switch (primary.trim().toLowerCase()) {
            case "deepseek" -> deepSeekAgentModelProvider.getIfAvailable();
            case "openai" -> openAiAgentModelProvider.getIfAvailable();
            case "dashscope" -> dashScopeAgentModelProvider.getIfAvailable();
            default -> null;
        };
    }
}
