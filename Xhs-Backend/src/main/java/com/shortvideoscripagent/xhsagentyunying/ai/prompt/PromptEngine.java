package com.shortvideoscripagent.xhsagentyunying.ai.prompt;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shortvideoscripagent.xhsagentyunying.domain.entity.AnalysisTask;
import com.shortvideoscripagent.xhsagentyunying.dto.title.TitleGenerateRequest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@Component
public class PromptEngine {

    public static final String RUBRIC_VERSION = "rubric-1.0.0";
    public static final String TITLE_PROMPT_VERSION = "title-1.0.0";
    public static final String BODY_PROMPT_VERSION = "body-1.0.1";

    private final String systemPrompt;
    private final ObjectMapper objectMapper;

    public PromptEngine(ObjectMapper objectMapper) throws IOException {
        this.objectMapper = objectMapper;
        ClassPathResource resource = new ClassPathResource("prompts/analysis-system.txt");
        this.systemPrompt = StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8);
    }

    public String systemPrompt() {
        return systemPrompt;
    }

    public String titleSystemPrompt() {
        return """
                你是小红书全品类内容资深运营，擅长写高点击、高收藏标题，适配美妆、穿搭、美食、旅行、知识干货等赛道。
                输出必须是合法 JSON，不要 markdown 代码块或额外说明。
                """;
    }

    public String bodySystemPrompt() {
        return """
                你是小红书全品类资深内容运营，擅长把用户提供的标题、正文素材和分析报告转成可发布正文初稿。
                原始标题和原始正文是最高优先级素材，分析报告只能用于优化表达、结构和 CTA，不能替换原主题。
                必须遵守合规要求：不得承诺保证 offer、100% 上岸、包进大厂、官方背书等不可验证结果。
                输出必须是合法 JSON，不要 markdown 代码块或额外说明。
                """;
    }

    public String buildTitleGenerateUserPrompt(TitleGenerateRequest request, int count) {
        String goal = request.getGoal() == null ? "high_ctr" : request.getGoal();
        StringBuilder sb = new StringBuilder();
        sb.append("【生成目标】").append(goalLabel(goal)).append('\n');
        sb.append("【运营人设】").append(personaLabel(request.getPersona())).append('\n');
        sb.append("【生成数量】").append(count).append(" 条（5-10 条）\n");
        if (request.getTitle() != null && !request.getTitle().isBlank()) {
            sb.append("【参考标题】\n").append(request.getTitle().trim()).append('\n');
        }
        if (request.getBody() != null && !request.getBody().isBlank()) {
            sb.append("【参考正文】\n").append(request.getBody().trim()).append('\n');
        }
        sb.append("""
                请生成小红书笔记标题，要求：
                - 每条标题不超过 20 字，标题中的中文、英文、数字、标点和符号都计入长度
                - highlights 列出该标题使用的运营手法（如「数字」「身份标签」「信息差」）
                - estimatedCtr 取 low / medium / high
                
                仅输出 JSON：
                {
                  "titles": [
                    {
                      "text": "string，不超过20字",
                      "highlights": ["string"],
                      "estimatedCtr": "high"
                    }
                  ]
                }
                """);
        return sb.toString();
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
            sb.append("【封面】用户已上传封面图，系统将单独进行视觉分析；CTR 维度须结合标题与正文，并在 ctr.reason 中体现封面相关判断（无需输出 coverAnalysis 字段）。\n");
        } else {
            sb.append("【封面】无，CTR 仅评标题与正文，须在 ctr.reason 注明「未含封面」。\n");
        }
        appendPublishedMetrics(sb, task);
        appendCompetitorContext(sb, task);
        if ("competitor".equals(task.getScenario())) {
            sb.append("【竞品学习】须输出 borrowPoints（可借鉴点 3-5 条）与 doNotCopy（勿照搬点 2-4 条），禁止洗稿整篇。\n");
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
                  "optimizedTitle": "string，不超过20字",
                  "optimizedBody": "string",
                  "structureOutline": ["Hook", "放大", "经历", "结果", "CTA"],
                  "cta": "string",
                  "complianceWarnings": []
                }
                """);
        return sb.toString();
    }

    public String buildBodyGenerateUserPrompt(
            AnalysisTask task,
            Map<String, Object> report,
            String goal,
            String tone,
            int maxLength,
            Iterable<String> keywords
    ) {
        StringBuilder sb = new StringBuilder();
        boolean hasOriginalBody = task.getBody() != null && !task.getBody().isBlank();
        boolean hasOriginalTitle = task.getTitle() != null && !task.getTitle().isBlank();
        sb.append("基于用户提供的原始素材，生成一篇可发布的小红书正文初稿。\n");
        sb.append("【生成目标】").append(goalLabel(goal)).append('\n');
        sb.append("【运营人设】").append(personaLabel(task.getPersona())).append('\n');
        sb.append("【语气风格】").append(toneLabel(tone)).append('\n');
        sb.append("【正文目标字数】").append(maxLength).append(" 字以内\n");
        if (keywords != null) {
            StringBuilder keywordText = new StringBuilder();
            for (String keyword : keywords) {
                if (keyword != null && !keyword.isBlank()) {
                    if (!keywordText.isEmpty()) {
                        keywordText.append("、");
                    }
                    keywordText.append(keyword.trim());
                }
            }
            if (!keywordText.isEmpty()) {
                sb.append("【必须自然包含的关键词】").append(keywordText).append('\n');
            }
        }
        sb.append("【原始标题（必须围绕，不得改主题）】").append(nullToEmpty(task.getTitle())).append('\n');
        sb.append("【原始正文或素材（最高优先级，必须继承核心信息）】\n").append(nullToEmpty(task.getBody())).append("\n\n");
        sb.append("【分析报告摘要（仅作为优化建议，不得覆盖原始素材）】\n").append(report).append("\n\n");
        sb.append("""
                请生成正文，要求：
                - 必须围绕原始标题/原始正文的主题、对象、场景、观点和事实展开
                - 如果原始正文不为空，必须保留原文的核心信息、关键结论和主要叙事线，只做结构化扩写和表达优化
                - 如果只有标题没有正文，必须围绕标题主题补全内容，不得跳到无关行业或无关人群
                - 不得凭空新增用户没有提供的身份、行业、国家、案例、数据、结果或经历
                - 不要改写成留学生求职内容，除非原始标题或正文明确就是该赛道
                - 使用五段式结构：Hook -> 问题放大 -> 真实经历 -> 结果展示 -> CTA
                - CTA 要匹配人设：机构号偏资料/咨询，导师 IP 偏专业建议，学长学姐偏经验互动
                - 不要输出标题，只输出正文
                - 不要承诺就业结果，不要使用绝对化广告话术

                仅输出 JSON：
                {
                  "body": "string",
                  "structureOutline": [
                    {"section": "hook", "summary": "string"},
                    {"section": "problem_amplification", "summary": "string"},
                    {"section": "real_experience", "summary": "string"},
                    {"section": "result_showcase", "summary": "string"},
                    {"section": "cta", "summary": "string"}
                  ],
                  "cta": "string",
                  "complianceWarnings": []
                }
                """);
        if (hasOriginalBody) {
            sb.append("再次强调：这次是基于【原始正文或素材】做正文生成，不是另起一个新选题。\n");
        } else if (hasOriginalTitle) {
            sb.append("再次强调：这次必须围绕【原始标题】扩写，不要生成其他赛道内容。\n");
        }
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
            case "mentor" -> "创作者/IP";
            case "senior" -> "素人博主";
            default -> "品牌/机构号";
        };
    }

    private static String goalLabel(String goal) {
        return switch (goal) {
            case "high_collect" -> "高收藏";
            case "high_conversion" -> "高转化";
            case "anxiety" -> "焦虑共鸣";
            case "offer" -> "结果晒单";
            case "info_gap" -> "信息差";
            default -> "高点击";
        };
    }

    private static String toneLabel(String tone) {
        return switch (tone == null ? "default" : tone) {
            case "more_anxiety" -> "加强痛点和焦虑共鸣，但不制造不实恐慌";
            case "more_professional" -> "更专业可信，适合导师或机构号";
            case "more_friendly" -> "更亲和口语，适合学长学姐经验分享";
            default -> "自然小红书口吻，专业但不生硬";
        };
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private void appendPublishedMetrics(StringBuilder sb, AnalysisTask task) {
        Map<String, Object> metrics = parseJsonMap(task.getPublishedMetrics());
        if (metrics == null || metrics.isEmpty()) {
            return;
        }
        sb.append("【已发互动数据】\n");
        metrics.forEach((key, value) -> {
            if (value != null) {
                sb.append(key).append(": ").append(value).append('\n');
            }
        });
    }

    private void appendCompetitorContext(StringBuilder sb, AnalysisTask task) {
        Map<String, Object> context = parseJsonMap(task.getCompetitorContext());
        if (context == null || context.isEmpty()) {
            return;
        }
        sb.append("【竞品对标信息】\n");
        context.forEach((key, value) -> {
            if (value != null && !String.valueOf(value).isBlank()) {
                sb.append(key).append(": ").append(value).append('\n');
            }
        });
    }

    private Map<String, Object> parseJsonMap(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() {
            });
        } catch (Exception ex) {
            return null;
        }
    }
}
