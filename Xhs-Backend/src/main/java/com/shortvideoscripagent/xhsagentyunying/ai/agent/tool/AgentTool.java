package com.shortvideoscripagent.xhsagentyunying.ai.agent.tool;

import java.util.Map;

public interface AgentTool {

    String name();

    String description();

    Map<String, Object> parametersSchema();

    ToolResult execute(ToolContext context, Map<String, Object> arguments);
}
