package com.shortvideoscripagent.xhsagentyunying.ai.agent;

import lombok.Builder;

import java.util.List;
import java.util.Map;

@Builder
public record AgentResponse(
        Long messageId,
        String role,
        String content,
        List<AgentCard> cards,
        List<ToolTrace> toolTraces
) {
}
