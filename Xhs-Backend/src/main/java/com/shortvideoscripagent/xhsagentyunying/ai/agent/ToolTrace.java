package com.shortvideoscripagent.xhsagentyunying.ai.agent;

import lombok.Builder;

@Builder
public record ToolTrace(
        String tool,
        boolean success,
        int latencyMs,
        String error
) {
}
