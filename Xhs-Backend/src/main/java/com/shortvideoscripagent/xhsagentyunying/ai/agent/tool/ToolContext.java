package com.shortvideoscripagent.xhsagentyunying.ai.agent.tool;

import java.util.Map;

public record ToolContext(
        Long userId,
        String workspaceId,
        String sessionId,
        String persona,
        String linkedTaskId,
        Map<String, Object> attachments
) {
}
