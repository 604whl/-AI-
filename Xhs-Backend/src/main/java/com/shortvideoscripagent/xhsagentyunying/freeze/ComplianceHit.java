package com.shortvideoscripagent.xhsagentyunying.freeze;

import java.util.Objects;

/**
 * 阶段 0：合规扫描单条命中结果。
 * <p>
 * 由 {@link ComplianceScanner} 输出的最小单元，所有匹配的提示/阻断必须显式命中片段和规则代码。
 */
public final class ComplianceHit {
    private final String ruleCode;
    private final RiskLevel riskLevel;
    private final ScanSeverity severity;
    private final String matchedSnippet;
    private final String suggestion;

    public ComplianceHit(String ruleCode,
                         RiskLevel riskLevel,
                         ScanSeverity severity,
                         String matchedSnippet,
                         String suggestion) {
        this.ruleCode = Objects.requireNonNull(ruleCode, "ruleCode");
        this.riskLevel = Objects.requireNonNull(riskLevel, "riskLevel");
        this.severity = Objects.requireNonNull(severity, "severity");
        this.matchedSnippet = matchedSnippet == null ? "" : matchedSnippet;
        this.suggestion = suggestion == null ? "" : suggestion;
    }

    public String ruleCode() { return ruleCode; }
    public RiskLevel riskLevel() { return riskLevel; }
    public ScanSeverity severity() { return severity; }
    public String matchedSnippet() { return matchedSnippet; }
    public String suggestion() { return suggestion; }

    public boolean blocksPublication() {
        return riskLevel.blocksPublication();
    }

    public boolean requiresManualReview() {
        return riskLevel.requiresManualReview();
    }

    /**
     * 便捷工厂：从风险等级推断严重性。
     */
    public static ComplianceHit of(String ruleCode,
                                   RiskLevel riskLevel,
                                   String matchedSnippet,
                                   String suggestion) {
        ScanSeverity severity = switch (riskLevel) {
            case LOW -> ScanSeverity.INFO;
            case MEDIUM -> ScanSeverity.WARN;
            case HIGH -> ScanSeverity.ERROR;
            case BLOCKED -> ScanSeverity.CRITICAL;
        };
        return new ComplianceHit(ruleCode, riskLevel, severity, matchedSnippet, suggestion);
    }
}
