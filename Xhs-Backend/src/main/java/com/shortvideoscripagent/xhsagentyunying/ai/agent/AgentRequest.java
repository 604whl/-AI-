package com.shortvideoscripagent.xhsagentyunying.ai.agent;

import lombok.Builder;

import java.util.List;
import java.util.Map;

@Builder
public record AgentRequest(
        Long userId,
        String sessionId,
        String content,
        Map<String, Object> attachments
) {
}
