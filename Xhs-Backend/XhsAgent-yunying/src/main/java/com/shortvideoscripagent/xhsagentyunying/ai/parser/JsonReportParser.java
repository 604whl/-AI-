package com.shortvideoscripagent.xhsagentyunying.ai.parser;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shortvideoscripagent.xhsagentyunying.common.exception.BusinessException;
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

    private static final Pattern JSON_BLOCK = Pattern.compile("```(?:json)?\\s*([\\s\\S]*?)```", Pattern.CASE_INSENSITIVE);

    private final ObjectMapper objectMapper;

    public Map<String, Object> parseAnalysisReport(String raw) {
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
        return report;
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

    private void validateAnalysisReport(Map<String, Object> report) {
        requireNonBlank(report, "contentType");
        String contentType = String.valueOf(report.get("contentType"));
        if (!CONTENT_TYPES.contains(contentType)) {
            throw new BusinessException(CODE_AI_RESPONSE_INVALID, "ai_response_invalid");
        }
        requireObject(report, "structure");
        requireObject(report, "scores");
        requireList(report, "issues");
        requireObject(report, "optimizations");
        if (!report.containsKey("complianceWarnings")) {
            report.put("complianceWarnings", new ArrayList<>());
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
