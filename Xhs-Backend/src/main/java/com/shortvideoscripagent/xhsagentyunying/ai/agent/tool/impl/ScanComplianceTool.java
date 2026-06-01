package com.shortvideoscripagent.xhsagentyunying.ai.agent.tool.impl;

import com.shortvideoscripagent.xhsagentyunying.ai.agent.AgentCard;
import com.shortvideoscripagent.xhsagentyunying.ai.agent.tool.AgentTool;
import com.shortvideoscripagent.xhsagentyunying.ai.agent.tool.ToolContext;
import com.shortvideoscripagent.xhsagentyunying.ai.agent.tool.ToolResult;
import com.shortvideoscripagent.xhsagentyunying.domain.compliance.ComplianceChecker;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class ScanComplianceTool implements AgentTool {

    private final ComplianceChecker complianceChecker;

    @Override
    public String name() {
        return "scan_compliance";
    }

    @Override
    public String description() {
        return "扫描标题与正文中的合规风险，如绝对化承诺、虚假权威等";
    }

    @Override
    public Map<String, Object> parametersSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "title", Map.of("type", "string"),
                        "body", Map.of("type", "string")
                )
        );
    }

    @Override
    public ToolResult execute(ToolContext context, Map<String, Object> arguments) {
        String title = firstNonBlank(
                SearchKbTool.stringArg(arguments, "title"),
                stringFromAttachments(context, "title")
        );
        String body = firstNonBlank(
                SearchKbTool.stringArg(arguments, "body"),
                stringFromAttachments(context, "body")
        );

        List<Map<String, Object>> warnings = complianceChecker.scan(title, body);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("warnings", warnings);

        AgentCard card = AgentCard.builder()
                .type("compliance_warnings")
                .payload(payload)
                .build();

        return ToolResult.ok(payload, List.of(card));
    }

    private static String stringFromAttachments(ToolContext context, String key) {
        if (context.attachments() == null) {
            return "";
        }
        Object value = context.attachments().get(key);
        return value == null ? "" : String.valueOf(value).trim();
    }

    private static String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first.trim();
        }
        return second == null ? "" : second.trim();
    }
}
