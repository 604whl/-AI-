package com.shortvideoscripagent.xhsagentyunying.ai.agent.model;

import com.shortvideoscripagent.xhsagentyunying.ai.agent.AgentRuntimePolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AgentModelProviderRegistry {

    private final MockAgentModelProvider mockAgentModelProvider;
    private final DashScopeAgentModelProvider dashScopeAgentModelProvider;
    private final AgentRuntimePolicy agentRuntimePolicy;

    public AgentModelProvider getProvider() {
        return agentRuntimePolicy.useMockAgent() ? mockAgentModelProvider : dashScopeAgentModelProvider;
    }
}
