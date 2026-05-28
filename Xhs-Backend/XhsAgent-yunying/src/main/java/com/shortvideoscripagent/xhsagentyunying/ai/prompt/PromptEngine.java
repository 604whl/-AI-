package com.shortvideoscripagent.xhsagentyunying.ai.prompt;

import com.shortvideoscripagent.xhsagentyunying.domain.entity.AnalysisTask;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@Component
public class PromptEngine {

    public static final String RUBRIC_VERSION = "rubric-1.0.0";

    private final String systemPrompt;

    public PromptEngine() throws IOException {
        ClassPathResource resource = new ClassPathResource("prompts/analysis-system.txt");
        this.systemPrompt = StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8);
    }

    public String systemPrompt() {
        return systemPrompt;
    }

    public String buildAnalysisUserPrompt(AnalysisTask task, String ragContext) {
        StringBuilder sb = new StringBuilder();
        sb.append("【分析场景】").append(scenarioLabel(task.getScenario())).append('\n');
        sb.append("【运营人设】").append(personaLabel(task.getPersona())).append('\n');
        if (task.getTitle() != null && !task.getTitle().isBlank()) {
            sb.append("【标题】\n").append(task.getTitle().trim()).append('\n');
        }
        if (task.getBody() != null && !task.getBody().isBlank()) {
            sb.append("【正文】\n").append(task.getBody().trim()).append('\n');
        }
        if (task.getCoverImageUrl() != null && !task.getCoverImageUrl().isBlank()) {
            sb.append("【封面】已提供图片 URL（CTR 维度须评估封面；若无法看图则在 ctr.reason 注明「未含封面视觉分析」）\n");
            sb.append(task.getCoverImageUrl()).append('\n');
        } else {
            sb.append("【封面】无，CTR 仅评标题，须在 ctr.reason 注明「未含封面」。\n");
        }
        if (ragContext != null && !ragContext.isBlank()) {
            sb.append("\n【参考案例（RAG）】\n").append(ragContext).append('\n');
        }
        sb.append("\n请输出完整 JSON 分析报告。");
        return sb.toString();
    }

    public String buildOptimizeDraftUserPrompt(AnalysisTask task, Map<String, Object> report, String tone, int maxLength) {
        StringBuilder sb = new StringBuilder();
        sb.append("基于以下分析结果，生成优化后的小红书笔记草稿。\n");
        sb.append("人设：").append(personaLabel(task.getPersona())).append('\n');
        sb.append("语气风格：").append(tone).append('\n');
        sb.append("正文目标字数：约 ").append(maxLength).append(" 字以内\n");
        sb.append("原标题：").append(nullToEmpty(task.getTitle())).append('\n');
        sb.append("原正文：\n").append(nullToEmpty(task.getBody())).append("\n\n");
        sb.append("分析报告摘要（JSON）：\n").append(report).append('\n');
        sb.append("""
                仅输出 JSON：
                {
                  "optimizedTitle": "string",
                  "optimizedBody": "string",
                  "structureOutline": ["Hook", "放大", "经历", "结果", "CTA"],
                  "cta": "string",
                  "complianceWarnings": []
                }
                """);
        return sb.toString();
    }

    private static String scenarioLabel(String scenario) {
        return switch (scenario == null ? "draft" : scenario) {
            case "published" -> "已发复盘";
            case "competitor" -> "竞品学习";
            default -> "草稿优化";
        };
    }

    private static String personaLabel(String persona) {
        return switch (persona == null ? "agency" : persona) {
            case "mentor" -> "导师 IP";
            case "senior" -> "学长学姐";
            default -> "机构号";
        };
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
