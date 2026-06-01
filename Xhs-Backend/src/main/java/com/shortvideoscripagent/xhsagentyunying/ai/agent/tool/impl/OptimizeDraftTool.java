package com.shortvideoscripagent.xhsagentyunying.ai.agent.tool.impl;

import com.shortvideoscripagent.xhsagentyunying.ai.agent.AgentCard;
import com.shortvideoscripagent.xhsagentyunying.ai.agent.tool.AgentTool;
import com.shortvideoscripagent.xhsagentyunying.ai.agent.tool.ToolContext;
import com.shortvideoscripagent.xhsagentyunying.ai.agent.tool.ToolResult;
import com.shortvideoscripagent.xhsagentyunying.common.exception.BusinessException;
import com.shortvideoscripagent.xhsagentyunying.dto.analysis.OptimizeDraftRequest;
import com.shortvideoscripagent.xhsagentyunying.dto.analysis.OptimizeDraftResponse;
import com.shortvideoscripagent.xhsagentyunying.service.AnalysisAppService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class OptimizeDraftTool implements AgentTool {

    private final AnalysisAppService analysisAppService;

    @Override
    public String name() {
        return "optimize_draft";
    }

    @Override
    public String description() {
        return "基于已完成的分析报告，生成优化后的小红书笔记标题与正文";
    }

    @Override
    public Map<String, Object> parametersSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "taskId", Map.of("type", "string", "description", "已完成分析的任务 ID"),
                        "tone", Map.of("type", "string", "description", "语气风格，如 default / professional / casual"),
                        "maxLength", Map.of("type", "integer", "default", 1200)
                ),
                "required", List.of("taskId")
        );
    }

    @Override
    public ToolResult execute(ToolContext context, Map<String, Object> arguments) {
        String taskId = firstNonBlank(
                SearchKbTool.stringArg(arguments, "taskId"),
                context.linkedTaskId()
        );
        if (taskId.isBlank()) {
            return ToolResult.fail("taskId_required");
        }

        OptimizeDraftRequest request = new OptimizeDraftRequest();
        request.setIncludeTitle(true);
        String tone = SearchKbTool.stringArg(arguments, "tone");
        if (!tone.isBlank()) {
            request.setTone(tone);
        }
        int maxLength = SearchKbTool.intArg(arguments, "maxLength", 1200);
        if (maxLength > 0) {
            request.setMaxLength(maxLength);
        }

        try {
            OptimizeDraftResponse draft = analysisAppService.optimizeDraft(context.userId(), taskId, request);

            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("optimizedTitle", draft.getOptimizedTitle());
            payload.put("optimizedBody", draft.getOptimizedBody());
            payload.put("structureOutline", draft.getStructureOutline());
            payload.put("cta", draft.getCta());
            payload.put("wordCount", draft.getWordCount());
            payload.put("complianceWarnings", draft.getComplianceWarnings());

            AgentCard card = AgentCard.builder()
                    .type("optimize_draft")
                    .taskId(taskId)
                    .payload(payload)
                    .build();

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("taskId", taskId);
            result.put("draft", payload);
            return ToolResult.ok(result, List.of(card), taskId);
        } catch (BusinessException ex) {
            return ToolResult.fail(ex.getMessage());
        }
    }

    private static String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first.trim();
        }
        return second == null ? "" : second.trim();
    }
}
