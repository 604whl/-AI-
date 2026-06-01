package com.shortvideoscripagent.xhsagentyunying.ai.parser;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shortvideoscripagent.xhsagentyunying.common.exception.BusinessException;
import com.shortvideoscripagent.xhsagentyunying.dto.title.TitleGenerateResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@RequiredArgsConstructor
public class JsonReportParser {

    private static final int CODE_AI_RESPONSE_INVALID = 50003;

    private static final Set<String> CONTENT_TYPES = Set.of(
            "ANXIETY", "OFFER", "INFO_GAP", "INTERVIEW", "TIMELINE", "COMEBACK"
    );

    private static final Set<String> ESTIMATED_CTR = Set.of("low", "medium", "high");

    private static final Pattern JSON_BLOCK = Pattern.compile("```(?:json)?\\s*([\\s\\S]*?)```", Pattern.CASE_INSENSITIVE);

    private final ObjectMapper objectMapper;

    public Map<String, Object> parseAnalysisReport(String raw) {
        return parseAnalysisReport(raw, null);
    }

    public Map<String, Object> parseAnalysisReport(String raw, String scenario) {
        String json = extractJson(raw);
        Map<String, Object> report;
        try {
            report = objectMapper.readValue(json, new TypeReference<>() {
            });
        } catch (Exception ex) {
            throw new BusinessException(CODE_AI_RESPONSE_INVALID, "ai_response_invalid");
        }
        validateAnalysisReport(report);
        enrichScores(report);
        ensureDefaults(report);
        ensureCompetitorFields(report, scenario);
        return report;
    }

    public Map<String, Object> parseCoverAnalysis(String raw) {
        String json = extractJson(raw);
        Map<String, Object> parsed;
        try {
            parsed = objectMapper.readValue(json, new TypeReference<>() {
            });
        } catch (Exception ex) {
            throw new BusinessException(CODE_AI_RESPONSE_INVALID, "ai_response_invalid");
        }
        parsed.put("available", true);
        if (!(parsed.get("keywords") instanceof List<?>)) {
            parsed.put("keywords", List.of());
        }
        parsed.putIfAbsent("contrastComment", "");
        parsed.putIfAbsent("emotionMatch", "");
        parsed.putIfAbsent("ctrImpact", "");
        return parsed;
    }

    public Map<String, Object> parseOptimizeDraft(String raw) {
        String json = extractJson(raw);
        Map<String, Object> draft;
        try {
            draft = objectMapper.readValue(json, new TypeReference<>() {
            });
        } catch (Exception ex) {
            throw new BusinessException(CODE_AI_RESPONSE_INVALID, "ai_response_invalid");
        }
        requireNonBlank(draft, "optimizedTitle");
        requireNonBlank(draft, "optimizedBody");
        if (!(draft.get("structureOutline") instanceof List<?> outline) || outline.size() < 3) {
            throw new BusinessException(CODE_AI_RESPONSE_INVALID, "ai_response_invalid");
        }
        if (!draft.containsKey("complianceWarnings")) {
            draft.put("complianceWarnings", List.of());
        }
        return draft;
    }

    @SuppressWarnings("unchecked")
    public List<TitleGenerateResponse.TitleItem> parseTitleGenerate(String raw, int minCount, int maxCount) {
        String json = extractJson(raw);
        Map<String, Object> parsed;
        try {
            parsed = objectMapper.readValue(json, new TypeReference<>() {
            });
        } catch (Exception ex) {
            throw new BusinessException(CODE_AI_RESPONSE_INVALID, "ai_response_invalid");
        }
        Object titlesObj = parsed.get("titles");
        if (!(titlesObj instanceof List<?> list) || list.isEmpty()) {
            throw new BusinessException(CODE_AI_RESPONSE_INVALID, "ai_response_invalid");
        }

        List<TitleGenerateResponse.TitleItem> items = new ArrayList<>();
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> map)) {
                continue;
            }
            Map<String, Object> titleMap = (Map<String, Object>) map;
            Object textObj = titleMap.get("text");
            if (textObj == null || String.valueOf(textObj).isBlank()) {
                continue;
            }
            String text = String.valueOf(textObj).trim();
            if (text.length() > 100) {
                text = text.substring(0, 100);
            }
            List<String> highlights = new ArrayList<>();
            Object highlightsObj = titleMap.get("highlights");
            if (highlightsObj instanceof List<?> highlightList) {
                for (Object highlight : highlightList) {
                    if (highlight != null && !String.valueOf(highlight).isBlank()) {
                        highlights.add(String.valueOf(highlight).trim());
                    }
                }
            }
            String estimatedCtr = "medium";
            Object ctrObj = titleMap.get("estimatedCtr");
            if (ctrObj != null) {
                String ctr = String.valueOf(ctrObj).trim().toLowerCase();
                if (ESTIMATED_CTR.contains(ctr)) {
                    estimatedCtr = ctr;
                }
            }
            items.add(new TitleGenerateResponse.TitleItem(text, highlights, estimatedCtr));
            if (items.size() >= maxCount) {
                break;
            }
        }

        if (items.size() < Math.min(minCount, 5)) {
            throw new BusinessException(CODE_AI_RESPONSE_INVALID, "ai_response_invalid");
        }
        return items;
    }

    private void validateAnalysisReport(Map<String, Object> report) {
        requireNonBlank(report, "contentType");
        String contentType = String.valueOf(report.get("contentType"));
        if (!CONTENT_TYPES.contains(contentType)) {
            throw new BusinessException(CODE_AI_RESPONSE_INVALID, "ai_response_invalid");
        }
        requireObject(report, "structure");
        requireObject(report, "scores");
        requireMinListSize(report, "issues", 3);
        requireObject(report, "optimizations");
        requireMinOptimizationSuggestions(report);
        if (!report.containsKey("complianceWarnings")) {
            report.put("complianceWarnings", new ArrayList<>());
        }
    }

    @SuppressWarnings("unchecked")
    private void ensureCompetitorFields(Map<String, Object> report, String scenario) {
        if (!"competitor".equals(scenario)) {
            return;
        }
        if (!(report.get("borrowPoints") instanceof List<?> borrow) || borrow.size() < 3) {
            report.put("borrowPoints", List.of(
                    "开篇 Hook 用反常识结论切入，降低划走率",
                    "中段用表格/清单承载收藏价值，适合截图保存",
                    "结尾 CTA 用评论区关键词领取资料，降低私信门槛"
            ));
        }
        if (!(report.get("doNotCopy") instanceof List<?> avoid) || avoid.isEmpty()) {
            report.put("doNotCopy", List.of(
                    "勿照搬对方具体数据与人名",
                    "勿复制整段经历叙事，仅借鉴结构"
            ));
        }
    }

    @SuppressWarnings("unchecked")
    private void requireMinOptimizationSuggestions(Map<String, Object> report) {
        Object optObj = report.get("optimizations");
        if (!(optObj instanceof Map<?, ?> optimizations)) {
            throw new BusinessException(CODE_AI_RESPONSE_INVALID, "ai_response_invalid");
        }
        int count = 0;
        Object titleObj = optimizations.get("title");
        if (titleObj instanceof List<?> titleList) {
            count += titleList.size();
        }
        for (String key : List.of("structure", "emotion", "cta")) {
            Object value = optimizations.get(key);
            if (value instanceof List<?> list) {
                count += list.size();
            }
        }
        if (count < 3) {
            throw new BusinessException(CODE_AI_RESPONSE_INVALID, "ai_response_invalid");
        }
    }

    private void requireMinListSize(Map<String, Object> map, String key, int minSize) {
        Object value = map.get(key);
        if (!(value instanceof List<?> list) || list.size() < minSize) {
            throw new BusinessException(CODE_AI_RESPONSE_INVALID, "ai_response_invalid");
        }
    }

    @SuppressWarnings("unchecked")
    private void enrichScores(Map<String, Object> report) {
        Object scoresObj = report.get("scores");
        if (!(scoresObj instanceof Map<?, ?> scores)) {
            return;
        }
        Map<String, Object> enriched = new LinkedHashMap<>();
        for (String key : List.of("ctr", "emotion", "collect", "conversion", "viral")) {
            Object dim = scores.get(key);
            if (dim instanceof Map<?, ?> dimMap) {
                Map<String, Object> copy = new LinkedHashMap<>((Map<String, Object>) dimMap);
                Object scoreVal = copy.get("score");
                if (scoreVal instanceof Number number) {
                    copy.putIfAbsent("level", levelFromScore(number.intValue()));
                }
                enriched.put(key, copy);
            }
        }
        report.put("scores", enriched);
    }

    private void ensureDefaults(Map<String, Object> report) {
        if (!(report.get("secondaryTags") instanceof List<?>)) {
            report.put("secondaryTags", List.of());
        }
    }

    private String levelFromScore(int score) {
        if (score >= 75) {
            return "high";
        }
        if (score >= 60) {
            return "medium";
        }
        return "low";
    }

    private String extractJson(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new BusinessException(CODE_AI_RESPONSE_INVALID, "ai_response_invalid");
        }
        String trimmed = raw.trim();
        Matcher matcher = JSON_BLOCK.matcher(trimmed);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        int start = trimmed.indexOf('{');
        int end = trimmed.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return trimmed.substring(start, end + 1);
        }
        return trimmed;
    }

    private void requireNonBlank(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null || String.valueOf(value).isBlank()) {
            throw new BusinessException(CODE_AI_RESPONSE_INVALID, "ai_response_invalid");
        }
    }

    private void requireObject(Map<String, Object> map, String key) {
        if (!(map.get(key) instanceof Map<?, ?>)) {
            throw new BusinessException(CODE_AI_RESPONSE_INVALID, "ai_response_invalid");
        }
    }

    private void requireList(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (!(value instanceof List<?> list) || list.isEmpty()) {
            throw new BusinessException(CODE_AI_RESPONSE_INVALID, "ai_response_invalid");
        }
    }
}
