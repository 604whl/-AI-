package com.shortvideoscripagent.xhsagentyunying.ai.agent.tool.impl;

import com.shortvideoscripagent.xhsagentyunying.ai.agent.AgentCard;
import com.shortvideoscripagent.xhsagentyunying.ai.agent.tool.AgentTool;
import com.shortvideoscripagent.xhsagentyunying.ai.agent.tool.ToolContext;
import com.shortvideoscripagent.xhsagentyunying.ai.agent.tool.ToolResult;
import com.shortvideoscripagent.xhsagentyunying.ai.cover.CoverAnalysisService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class AnalyzeCoverTool implements AgentTool {

    private final CoverAnalysisService coverAnalysisService;

    @Override
    public String name() {
        return "analyze_cover";
    }

    @Override
    public String description() {
        return "分析小红书笔记封面图的视觉质量、关键词与 CTR 影响";
    }

    @Override
    public Map<String, Object> parametersSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "coverImageUrl", Map.of("type", "string", "description", "封面图 URL"),
                        "title", Map.of("type", "string"),
                        "body", Map.of("type", "string")
                ),
                "required", List.of("coverImageUrl")
        );
    }

    @Override
    public ToolResult execute(ToolContext context, Map<String, Object> arguments) {
        String cover = firstNonBlank(
                SearchKbTool.stringArg(arguments, "coverImageUrl"),
                stringFromAttachments(context, "coverImageUrl")
        );
        if (cover.isBlank()) {
            return ToolResult.fail("cover_image_url_required");
        }

        String title = firstNonBlank(
                SearchKbTool.stringArg(arguments, "title"),
                stringFromAttachments(context, "title")
        );
        String body = firstNonBlank(
                SearchKbTool.stringArg(arguments, "body"),
                stringFromAttachments(context, "body")
        );

        Map<String, Object> analysis = coverAnalysisService.analyzeByContext(cover, title, body);

        AgentCard card = AgentCard.builder()
                .type("cover_analysis")
                .payload(analysis)
                .build();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("coverAnalysis", analysis);
        return ToolResult.ok(result, List.of(card));
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
