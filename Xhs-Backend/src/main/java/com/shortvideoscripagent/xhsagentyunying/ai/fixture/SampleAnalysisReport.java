package com.shortvideoscripagent.xhsagentyunying.ai.fixture;

import com.shortvideoscripagent.xhsagentyunying.domain.entity.AnalysisTask;
import com.shortvideoscripagent.xhsagentyunying.dto.title.TitleGenerateResponse;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class SampleAnalysisReport {

    private SampleAnalysisReport() {
    }

    public static Map<String, Object> build(AnalysisTask task) {
        return build(
                task.getTitle(),
                task.getBody(),
                task.getPersona(),
                task.getScenario(),
                task.getCoverImageUrl() != null && !task.getCoverImageUrl().isBlank()
        );
    }

    public static Map<String, Object> build(String title, String body, String persona) {
        return build(title, body, persona, "draft", false);
    }

    public static Map<String, Object> build(
            String title,
            String body,
            String persona,
            String scenario,
            boolean hasCover
    ) {
        String noteTitle = title == null || title.isBlank() ? "未命名笔记" : title;
        String hook = extractFirstLine(body);

        Map<String, Object> report = new LinkedHashMap<>();
        report.put("contentType", "TIMELINE");
        report.put("secondaryTags", List.of("INFO_GAP", "ANXIETY"));
        report.put("structure", Map.of(
                "hook", hook.isBlank() ? "开篇以常见误区或反常识结论切入，制造信息差。" : hook,
                "emotionArc", List.of("痛点共鸣", "信息揭示", "方法落地", "行动号召"),
                "savePoints", List.of("步骤清单", "常见误区汇总", "实操建议"),
                "conversionPath", "痛点提问 → 分阶段拆解 → 评论区领取资料 → 私信咨询",
                "cta", Map.of(
                        "text", ctaForPersona(persona),
                        "rating", 4,
                        "comment", "CTA 与 " + persona + " 人设匹配度较好，建议保留评论区互动引导。"
                )
        ));
        String ctrReason = hasCover
                ? "标题含数字与身份标签；封面关键词清晰，点击预期中等偏上。"
                : "标题含数字与身份标签，点击预期中等偏上。";
        report.put("scores", Map.of(
                "ctr", score(78, ctrReason, "high"),
                "emotion", score(82, "痛点与共鸣点覆盖完整，情绪递进清晰。", "high"),
                "collect", score(85, "时间线结构适合收藏回看。", "high"),
                "conversion", score(71, "CTA 位置合理，但可再强化稀缺性。", "medium"),
                "viral", score(68, "话题明确，传播半径适中，利于垂类人群扩散。", "medium")
        ));
        report.put("issues", List.of(
                issue("medium", "ctr", "标题可再压缩至 20 字内，提升移动端展示完整度。", "尝试「数字+身份标签｜核心利益点」格式。"),
                issue("low", "emotion", "中段可增加对比或反差场景，强化紧迫感。", "补充「身边人已经…」类共鸣表述。"),
                issue("low", "conversion", "CTA 略靠后，部分用户可能在中间段落流失。", "在第 3 段末尾增加一次轻量引导。")
        ));
        report.put("optimizations", Map.of(
                "title", List.of(Map.of(
                        "original", noteTitle,
                        "optimized", "一张图讲清｜" + noteTitle + "完整攻略"
                )),
                "structure", List.of("首段增加反常识结论", "中段用 emoji 序号强化扫读性"),
                "emotion", List.of("补充对比场景增强紧迫感"),
                "cta", List.of("评论区扣关键词领取资料", "结尾增加限时感表述")
        ));
        report.put("complianceWarnings", List.of());

        if ("competitor".equals(scenario)) {
            report.put("borrowPoints", List.of(
                    "Hook 用「误区+反常识」开场，适合各类笔记冷启动",
                    "中段清单/表格结构利于收藏，可复用到自家内容",
                    "评论区领资料 CTA 降低转化门槛，可借鉴互动设计"
            ));
            report.put("doNotCopy", List.of(
                    "勿照搬对方具体品牌/数据与个人经历细节",
                    "勿复制整段个人经历，仅借鉴框架与节奏"
            ));
        }
        return report;
    }

    public static Map<String, Object> buildCoverAnalysis(String title, String body) {
        Map<String, Object> cover = new LinkedHashMap<>();
        cover.put("available", true);
        cover.put("keywords", List.of("数字标签", "高对比底色", "人物表情", "品类关键词"));
        cover.put("contrastComment", "主标题与背景对比度较好，移动端缩略图可读。");
        cover.put("emotionMatch", "封面情绪与笔记主题一致，利于目标受众停留。");
        cover.put("ctrImpact", "预计可提升 5–15% 点击率，建议保留大号数字元素。");
        if (title != null && title.contains("Offer")) {
            cover.put("keywords", List.of("Offer 喜报", "结果导向", "高饱和色"));
            cover.put("emotionMatch", "喜悦与成就感明确，适合 Offer 型笔记。");
        }
        return cover;
    }

    private static Map<String, Object> score(int value, String reason, String level) {
        return Map.of("score", value, "reason", reason, "level", level);
    }

    private static Map<String, Object> issue(String severity, String category, String description, String suggestion) {
        return Map.of(
                "severity", severity,
                "category", category,
                "description", description,
                "suggestion", suggestion
        );
    }

    private static String extractFirstLine(String body) {
        if (body == null || body.isBlank()) {
            return "";
        }
        int idx = body.indexOf('\n');
        return idx < 0 ? body.trim() : body.substring(0, idx).trim();
    }

    private static String ctaForPersona(String persona) {
        return switch (persona == null ? "agency" : persona) {
            case "mentor" -> "想要一对一指导的粉丝，私信「资料」领取清单。";
            case "senior" -> "整理了完整清单，评论区扣「清单」发你。";
            default -> "评论「清单」领取完整资料，或私信获取定制建议。";
        };
    }

    public static List<TitleGenerateResponse.TitleItem> buildTitles(String goal, String title, String body, int count) {
        String topic = title == null || title.isBlank() ? "这篇笔记" : title.trim();
        String[] templates = switch (goal == null ? "high_ctr" : goal) {
            case "high_collect" -> new String[]{
                    "建议收藏｜" + topic + "完整攻略",
                    "必存！" + topic + "一张表搞定",
                    topic + "｜收藏这篇就够了",
                    "新手请收藏：" + topic,
                    topic + "避坑清单（建议收藏）",
                    "入门必看｜" + topic + "全攻略",
                    "收藏向｜" + topic + "步骤清单",
                    topic + "｜干货收藏版"
            };
            case "high_conversion" -> new String[]{
                    "私信领资料｜" + topic + "完整版",
                    topic + "｜评论「资料」免费领取",
                    topic + "｜私信咨询一对一",
                    topic + "：评论扣 1 领清单",
                    "想少走弯路？" + topic + "先看这篇",
                    topic + "｜免费领实操清单",
                    "别踩坑｜" + topic + "完整指南",
                    topic + "｜评论扣 1 领资料"
            };
            case "anxiety" -> new String[]{
                    "还没开始？" + topic + "别人已经开始了",
                    "别慌｜" + topic + "还来得及吗",
                    topic + "｜太真实了",
                    "身边人都在做，你的" + topic + "准备好了吗",
                    topic + "｜再拖就真来不及了",
                    "崩溃瞬间：" + topic,
                    "别等最后｜" + topic + "真相",
                    topic + "｜90% 的人都搞错了"
            };
            case "offer" -> new String[]{
                    "成功了！我的" + topic + "复盘",
                    "从 0 到 1｜" + topic + "经验分享",
                    topic + "｜全记录公开",
                    topic + "｜我就是这样做到的",
                    topic + "喜报｜结果前置",
                    topic + "成功复盘，经验全公开",
                    "拿到结果后总结的" + topic,
                    topic + "｜背后的完整步骤"
            };
            case "info_gap" -> new String[]{
                    "很少有人告诉你的" + topic + "真相",
                    topic + "｜信息差太大了",
                    "必看｜" + topic + "隐藏细节",
                    "新手不知道的" + topic + "细节",
                    topic + "｜内行人才知道的事",
                    "信息差警告：" + topic,
                    topic + "｜内行人才知道的技巧",
                    topic + "｜信息差合集"
            };
            default -> new String[]{
                    topic + "｜一张图讲清楚",
                    topic + "到底怎么做？",
                    "新手必看｜" + topic,
                    topic + "｜90% 的人都搞错了",
                    topic + "全攻略（建议收藏）",
                    topic + "步骤清单（建议收藏）",
                    "新手｜" + topic + "避坑指南",
                    topic + "｜评论区领完整资料"
            };
        };

        String[] highlights = new String[]{"数字", "身份标签", "悬念"};
        String[] ctrLevels = new String[]{"medium", "high", "high", "medium", "high", "medium", "high", "medium"};
        int size = Math.min(Math.max(count, 5), 10);
        List<TitleGenerateResponse.TitleItem> items = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            items.add(new TitleGenerateResponse.TitleItem(
                    templates[i % templates.length],
                    List.of(highlights[i % highlights.length], goal == null ? "高点击" : goal),
                    ctrLevels[i % ctrLevels.length]
            ));
        }
        return items;
    }
}
