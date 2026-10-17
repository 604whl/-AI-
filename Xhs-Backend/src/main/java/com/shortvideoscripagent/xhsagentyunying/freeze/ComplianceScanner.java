package com.shortvideoscripagent.xhsagentyunying.freeze;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 阶段 0：合规扫描器实现。
 * <p>
 * 扫描来源：
 * <ul>
 *   <li>关键词（KEYWORD 类型规则）</li>
 *   <li>正则（REGEX 类型规则）</li>
 *   <li>启发式（HEURISTIC 类型规则，目前仅作为占位）</li>
 * </ul>
 * 任何命中必须给出命中片段和规则代码，禁止只返回 true/false。
 * <p>
 * 错误处理：扫描器自身异常不能影响业务；捕获后记日志返回空结果。
 */
@Component
public class ComplianceScanner {

    private static final Logger LOG = LoggerFactory.getLogger(ComplianceScanner.class);

    private final ObjectMapper objectMapper;

    public ComplianceScanner(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 扫描给定文本，返回所有命中的风险项。
     * <p>
     * 永不抛异常；扫描异常一律返回空集合并打日志。
     */
    public List<ComplianceHit> scan(String ruleCode, String text) {
        if (text == null || text.isBlank() || ruleCode == null || ruleCode.isBlank()) {
            return Collections.emptyList();
        }
        try {
            Rule rule = RuleCatalog.lookup(ruleCode);
            if (rule == null) {
                return Collections.emptyList();
            }
            return switch (rule.detectorType) {
                case KEYWORD -> scanKeywords(rule, text);
                case REGEX -> scanRegex(rule, text);
                case HEURISTIC, ML -> Collections.emptyList(); // 阶段 0 占位
            };
        } catch (Exception ex) {
            LOG.warn("compliance scan failed for rule={} message={}", ruleCode, ex.toString());
            return Collections.emptyList();
        }
    }

    private List<ComplianceHit> scanKeywords(Rule rule, String text) {
        List<ComplianceHit> hits = new ArrayList<>();
        JsonNode words = extractArray(rule.detectorConfig, "words");
        if (words == null) {
            return hits;
        }
        for (JsonNode node : words) {
            if (node.isTextual()) {
                String keyword = node.asText();
                int idx = text.indexOf(keyword);
                if (idx >= 0) {
                    String snippet = excerpt(text, idx, keyword.length());
                    hits.add(ComplianceHit.of(rule.code, rule.riskLevel, snippet, rule.suggestion));
                }
            }
        }
        return hits;
    }

    private List<ComplianceHit> scanRegex(Rule rule, String text) {
        List<ComplianceHit> hits = new ArrayList<>();
        JsonNode patterns = extractArray(rule.detectorConfig, "patterns");
        if (patterns == null) {
            return hits;
        }
        for (JsonNode node : patterns) {
            if (node.isTextual()) {
                String patternStr = node.asText();
                try {
                    Pattern pattern = Pattern.compile(patternStr);
                    var matcher = pattern.matcher(text);
                    if (matcher.find()) {
                        String snippet = excerpt(text, matcher.start(), matcher.end() - matcher.start());
                        hits.add(ComplianceHit.of(rule.code, rule.riskLevel, snippet, rule.suggestion));
                    }
                } catch (Exception ex) {
                    LOG.warn("invalid regex pattern in rule={} pattern={} error={}",
                            rule.code, patternStr, ex.toString());
                }
            }
        }
        return hits;
    }

    /**
     * 提取 array 字段。兼容两种存储：
     * <ol>
     *   <li>直接 array（向后兼容 / 硬编码路径）</li>
     *   <li>object：{ "words": [...] } 或 { "patterns": [...] }（DB 路径）</li>
     * </ol>
     */
    private JsonNode extractArray(JsonNode config, String key) {
        if (config == null) {
            return null;
        }
        if (config.isArray()) {
            return config;
        }
        if (config.isObject()) {
            JsonNode v = config.get(key);
            if (v != null && v.isArray()) {
                return v;
            }
        }
        return null;
    }

    private static String excerpt(String text, int index, int length) {
        int start = Math.max(0, index - 10);
        int end = Math.min(text.length(), index + length + 10);
        String prefix = start > 0 ? "…" : "";
        String suffix = end < text.length() ? "…" : "";
        return prefix + text.substring(start, end) + suffix;
    }

    /**
     * 规则的内存表示。阶段 0 优先硬编码启动期必用规则，未来由 DB 加载。
     */
    public static final class Rule {
        public final String code;
        public final DetectorType detectorType;
        public final RiskLevel riskLevel;
        public final String suggestion;
        public final JsonNode detectorConfig;

        public Rule(String code,
                    DetectorType detectorType,
                    RiskLevel riskLevel,
                    String suggestion,
                    JsonNode detectorConfig) {
            this.code = code;
            this.detectorType = detectorType;
            this.riskLevel = riskLevel;
            this.suggestion = suggestion;
            this.detectorConfig = detectorConfig;
        }
    }

    public enum DetectorType { KEYWORD, REGEX, ML, HEURISTIC }

    /**
     * 阶段 0 内存级规则目录：与 {@code app.compliance_rule} 数据库冻结保持一致。
     */
    public static final class RuleCatalog {
        private static final ObjectMapper MAPPER = new ObjectMapper();

        public static Rule lookup(String code) {
            return switch (code) {
                case "ABSOLUTE_CLAIM" -> new Rule("ABSOLUTE_CLAIM", DetectorType.REGEX, RiskLevel.HIGH,
                        "去掉绝对化词汇，改为具体可验证的承诺",
                        parseArray("100%", "绝对", "保证", "稳过", "包过"));
                case "MEDICAL_RISK" -> new Rule("MEDICAL_RISK", DetectorType.KEYWORD, RiskLevel.BLOCKED,
                        "医疗内容必须由专业人士审核，禁止自动发布",
                        parseArray("治愈", "特效", "根治"));
                case "AD_LAW_RISK" -> new Rule("AD_LAW_RISK", DetectorType.REGEX, RiskLevel.HIGH,
                        "广告法禁止极限词，请改写",
                        parseArray("最好", "最大", "最强", "第一", "唯一", "顶级"));
                case "FINANCE_RISK" -> new Rule("FINANCE_RISK", DetectorType.KEYWORD, RiskLevel.BLOCKED,
                        "金融内容禁止收益承诺，必须人工审核",
                        parseArray("稳赚", "无风险", "高收益", "保本"));
                case "IMMIGRATION_RISK" -> new Rule("IMMIGRATION_RISK", DetectorType.KEYWORD, RiskLevel.MEDIUM,
                        "请添加风险提示，避免绝对化描述",
                        parseArray("包过", "百分百", "100%通过"));
                default -> null;
            };
        }

        private static JsonNode parseArray(String... values) {
            try {
                return MAPPER.readTree("[" +
                        String.join(",", java.util.Arrays.stream(values)
                                .map(s -> "\"" + s + "\"")
                                .toArray(String[]::new))
                        + "]");
            } catch (Exception ex) {
                return MAPPER.createArrayNode();
            }
        }
    }

    /**
     * 计算扫描结果综合严重性。
     */
    public static RiskLevel aggregateRiskLevel(List<ComplianceHit> hits) {
        RiskLevel worst = RiskLevel.LOW;
        for (ComplianceHit hit : hits) {
            if (hit.riskLevel().ordinal() > worst.ordinal()) {
                worst = hit.riskLevel();
            }
        }
        return worst;
    }
}
