package com.shortvideoscripagent.xhsagentyunying.ai.agent.tool.impl;

import com.shortvideoscripagent.xhsagentyunying.ai.agent.AgentCard;
import com.shortvideoscripagent.xhsagentyunying.ai.agent.tool.AgentTool;
import com.shortvideoscripagent.xhsagentyunying.ai.agent.tool.ToolContext;
import com.shortvideoscripagent.xhsagentyunying.ai.agent.tool.ToolResult;
import com.shortvideoscripagent.xhsagentyunying.dto.title.TitleGenerateRequest;
import com.shortvideoscripagent.xhsagentyunying.dto.title.TitleGenerateResponse;
import com.shortvideoscripagent.xhsagentyunying.service.TitleService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class GenerateTitlesTool implements AgentTool {

    private final TitleService titleService;

    @Override
    public String name() {
        return "generate_titles";
    }

    @Override
    public String description() {
        return "批量生成 5-10 条小红书标题变体，含 CTR 预估与运营手法标注";
    }

    @Override
    public Map<String, Object> parametersSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "taskId", Map.of("type", "string", "description", "关联分析任务 ID"),
                        "title", Map.of("type", "string"),
                        "body", Map.of("type", "string"),
                        "goal", Map.of(
                                "type", "string",
                                "enum", List.of("high_ctr", "high_collect", "high_conversion", "anxiety", "offer", "info_gap")
                        ),
                        "count", Map.of("type", "integer", "minimum", 5, "maximum", 10)
                ),
                "required", List.of("goal")
        );
    }

    @Override
    public ToolResult execute(ToolContext context, Map<String, Object> arguments) {
        TitleGenerateRequest request = new TitleGenerateRequest();
        String taskId = firstNonBlank(
                SearchKbTool.stringArg(arguments, "taskId"),
                context.linkedTaskId()
        );
        if (!taskId.isBlank()) {
            request.setAnalysisId(taskId);
        }
        request.setGoal(firstNonBlank(SearchKbTool.stringArg(arguments, "goal"), "high_ctr"));
        request.setPersona(context.persona());
        request.setTitle(firstNonBlank(
                SearchKbTool.stringArg(arguments, "title"),
                stringFromAttachments(context, "title")
        ));
        request.setBody(firstNonBlank(
                SearchKbTool.stringArg(arguments, "body"),
                stringFromAttachments(context, "body")
        ));
        request.setCount(SearchKbTool.intArg(arguments, "count", 8));

        TitleGenerateResponse response = titleService.generate(context.userId(), request);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("goal", response.getGoal());
        payload.put("titles", response.getTitles());

        AgentCard card = AgentCard.builder()
                .type("title_list")
                .taskId(response.getAnalysisId())
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
