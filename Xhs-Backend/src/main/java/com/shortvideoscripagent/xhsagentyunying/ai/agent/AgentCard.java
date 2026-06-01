package com.shortvideoscripagent.xhsagentyunying.ai.agent;

import lombok.Builder;

import java.util.Map;

@Builder
public record AgentCard(
        String type,
        String taskId,
        Map<String, Object> payload
) {
}
