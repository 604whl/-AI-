package com.shortvideoscripagent.xhsagentyunying.ai.agent;

import com.shortvideoscripagent.xhsagentyunying.ai.AiRuntimePolicy;
import com.shortvideoscripagent.xhsagentyunying.config.AppAgentProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AgentRuntimePolicy {

    private final AppAgentProperties appAgentProperties;
    private final AiRuntimePolicy aiRuntimePolicy;

    public boolean isEnabled() {
        return appAgentProperties.isEnabled();
    }

    public boolean useMockAgent() {
        return appAgentProperties.isMockEnabled() || aiRuntimePolicy.useMockResponses();
    }

    public int maxSteps() {
        return appAgentProperties.getMaxSteps();
    }

    public int totalTimeoutSeconds() {
        return appAgentProperties.getTotalTimeoutSeconds();
    }

    public int maxContextMessages() {
        return appAgentProperties.getMaxContextMessages();
    }
}
