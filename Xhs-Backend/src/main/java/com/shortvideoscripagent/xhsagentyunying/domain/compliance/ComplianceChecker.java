package com.shortvideoscripagent.xhsagentyunying.domain.compliance;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class ComplianceChecker {

    private record Rule(String id, Pattern pattern, String suggestion) {
    }

    private static final List<Rule> RULES = List.of(
            new Rule("absolute_promise",
                    Pattern.compile("100%\\s*有效| garantee|国家级(?!产品)|(?:最好|第一)(?!手)|百分百|绝对有效|必上岸|保证录取"),
                    "避免绝对化承诺与广告法禁用表述，改为可验证的客观描述"),
            new Rule("fake_authority",
                    Pattern.compile("官方认证|小红书官方|教育部认证(?!号)"),
                    "勿冒充官方；若合作须标注广告/合作"),
            new Rule("discrimination",
                    Pattern.compile("某省人都不行|某国人都不行|学历低的人都不行"),
                    "避免地域、国籍、群体歧视性表述"),
            new Rule("excessive_fear",
                    Pattern.compile("再不.*就完了|马上关停|政策已崩|立刻下架"),
                    "避免制造不实恐慌，引用政策须注明来源")
    );

    public List<Map<String, Object>> scan(String title, String body) {
        String text = ((title == null ? "" : title) + "\n" + (body == null ? "" : body)).trim();
        if (text.isBlank()) {
            return List.of();
        }

        List<Map<String, Object>> warnings = new ArrayList<>();
        for (Rule rule : RULES) {
            Matcher matcher = rule.pattern.matcher(text);
            if (matcher.find()) {
                Map<String, Object> warning = new LinkedHashMap<>();
                warning.put("rule", rule.id);
                warning.put("matchedText", matcher.group().trim());
                warning.put("suggestion", rule.suggestion);
                warnings.add(warning);
            }
        }
        return warnings;
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> mergeIntoReport(Map<String, Object> report, String title, String body) {
        List<Map<String, Object>> merged = new ArrayList<>(scan(title, body));
        Object existing = report.get("complianceWarnings");
        if (existing instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof Map<?, ?> map) {
                    merged.add((Map<String, Object>) map);
                }
            }
        }
        report.put("complianceWarnings", dedupe(merged));
        return report;
    }

    private List<Map<String, Object>> dedupe(List<Map<String, Object>> warnings) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> warning : warnings) {
            boolean duplicate = result.stream().anyMatch(w ->
                    warning.get("rule").equals(w.get("rule"))
                            && warning.get("matchedText").equals(w.get("matchedText")));
            if (!duplicate) {
                result.add(warning);
            }
        }
        return result;
    }
}
