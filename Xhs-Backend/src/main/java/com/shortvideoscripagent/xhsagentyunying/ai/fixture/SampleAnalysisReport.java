package com.shortvideoscripagent.xhsagentyunying.ai.fixture;

import com.shortvideoscripagent.xhsagentyunying.dto.title.TitleGenerateResponse;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class SampleAnalysisReport {

    private SampleAnalysisReport() {
    }

    public static Map<String, Object> build(String title, String body, String persona) {
        String noteTitle = title == null || title.isBlank() ? "未命名笔记" : title;
        String hook = extractFirstLine(body);

        Map<String, Object> report = new LinkedHashMap<>();
        report.put("contentType", "TIMELINE");
        report.put("secondaryTags", List.of("INFO_GAP", "ANXIETY"));
        report.put("structure", Map.of(
                "hook", hook.isBlank() ? "开篇以留学生常见误区切入，制造信息差。" : hook,
                "emotionArc", List.of("焦虑共鸣", "信息揭示", "方法落地", "行动号召"),
                "savePoints", List.of("秋招时间线表格", "常见误区清单", "投递节奏建议"),
                "conversionPath", "痛点提问 → 分阶段拆解 → 评论区领取资料 → 私信咨询",
                "cta", Map.of(
                        "text", ctaForPersona(persona),
                        "rating", 4,
                        "comment", "CTA 与 " + persona + " 人设匹配度较好，建议保留评论区互动引导。"
                )
        ));
        report.put("scores", Map.of(
                "ctr", score(78, "标题含数字与身份标签，点击预期中等偏上。", "high"),
                "emotion", score(82, "留学生秋招焦虑点覆盖完整，情绪递进清晰。", "high"),
                "collect", score(85, "时间线结构适合收藏回看。", "high"),
                "conversion", score(71, "CTA 位置合理，但可再强化稀缺性。", "medium"),
                "viral", score(68, "话题垂直，传播半径有限但精准。", "medium")
        ));
        report.put("issues", List.of(
                issue("medium", "ctr", "标题可再压缩至 20 字内，提升移动端展示完整度。", "尝试「26届｜英国秋招时间线」格式。"),
                issue("low", "conversion", "CTA 略靠后，部分用户可能在中间段落流失。", "在第 3 段末尾增加一次轻量引导。")
        ));
        report.put("optimizations", Map.of(
                "title", List.of(Map.of(
                        "original", noteTitle,
                        "optimized", "26届英国留学生｜秋招时间线一张图讲清"
                )),
                "structure", List.of("首段增加反常识结论", "中段用 emoji 序号强化扫读性"),
                "emotion", List.of("补充「同届竞争」场景增强紧迫感"),
                "cta", List.of("评论区扣关键词领取表格", "结尾增加限时感表述")
        ));
        report.put("complianceWarnings", List.of());
        return report;
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
            case "mentor" -> "想要一对一求职规划的同学，私信「规划」领取诊断表。";
            case "senior" -> "学长整理了完整表格，评论区扣「时间线」发你。";
            default -> "评论「时间线」领取完整表格，或私信获取定制建议。";
        };
    }

    public static Map<String, Object> scoresOnly(Map<String, Object> report) {
        Object scores = report.get("scores");
        if (scores == null) {
            return Map.of();
        }
        return Map.of("scores", scores);
    }

    public static List<TitleGenerateResponse.TitleItem> buildTitles(String goal, String title, String body, int count) {
        String topic = title == null || title.isBlank() ? "英国留学生秋招" : title.trim();
        String[] templates = switch (goal == null ? "high_ctr" : goal) {
            case "high_collect" -> new String[]{
                    "建议收藏｜" + topic + "完整时间线",
                    "留子必存！" + topic + "一张表搞定",
                    "26届" + topic + "｜收藏这篇就够了",
                    "回国求职党请收藏：" + topic,
                    topic + "避坑清单（建议收藏）",
                    "秋招前必看｜" + topic + "全攻略",
                    "收藏向｜" + topic + "投递节奏表",
                    "留学生求职｜" + topic + "收藏版"
            };
            case "high_conversion" -> new String[]{
                    "私信领表｜" + topic + "完整规划",
                    topic + "｜评论「规划」领诊断",
                    "26届留子求职｜" + topic + "1v1 咨询",
                    topic + "：私信「时间线」领表格",
                    "想进大厂？" + topic + "先看这篇",
                    topic + "｜免费领求职节奏表",
                    "留子求职别踩坑｜" + topic,
                    topic + "｜评论扣 1 领资料"
            };
            case "anxiety" -> new String[]{
                    "还没开始投？" + topic + "已经开始了",
                    "26届留子别慌｜" + topic + "还来得及吗",
                    "英国回国求职｜" + topic + "太真实了",
                    "同届都在投，你的" + topic + "准备好了吗",
                    topic + "｜再拖就真来不及了",
                    "留子崩溃瞬间：" + topic,
                    "别等毕业再投｜" + topic + "真相",
                    topic + "｜90% 留子都搞错了"
            };
            case "offer" -> new String[]{
                    "Offer 到手！我的" + topic + "复盘",
                    "从 0 到 Offer｜" + topic + "经验分享",
                    "26届留子上岸｜" + topic + "全记录",
                    topic + "｜我就是这样拿到 Offer 的",
                    "英国留子 Offer 喜报｜" + topic,
                    topic + "成功上岸，经验全公开",
                    "拿到 Dream Offer 后总结的" + topic,
                    topic + "｜Offer 背后的时间线"
            };
            case "info_gap" -> new String[]{
                    "很少有人告诉你的" + topic + "真相",
                    topic + "｜信息差太大了",
                    "26届必看｜" + topic + "隐藏规则",
                    "英国留子不知道的" + topic + "细节",
                    topic + "｜大厂 HR 不会告诉你的事",
                    "信息差警告：" + topic,
                    topic + "｜内行人才知道的节奏",
                    "留子求职信息差｜" + topic
            };
            default -> new String[]{
                    "26届" + topic + "｜一张图讲清楚",
                    topic + "到底从几月开始？",
                    "英国留子必看｜" + topic,
                    topic + "｜90% 的人都搞错了",
                    "留子求职｜" + topic + "全攻略",
                    topic + "时间线（建议收藏）",
                    "26届留子｜" + topic + "避坑指南",
                    topic + "｜评论区领完整表格"
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
