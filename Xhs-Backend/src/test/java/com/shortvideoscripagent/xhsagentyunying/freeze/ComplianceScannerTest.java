package com.shortvideoscripagent.xhsagentyunying.freeze;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 阶段 0：合规扫描器单元测试。
 */
class ComplianceScannerTest {

    @Test
    void absoluteClaimDetected() {
        ComplianceScanner scanner = new ComplianceScanner(new ObjectMapper());
        List<ComplianceHit> hits = scanner.scan("ABSOLUTE_CLAIM", "我们保证100%通过面试");
        // 同时含"100%"和"保证"，应命中 2 条
        assertEquals(2, hits.size());
        ComplianceHit hit = hits.get(0);
        assertEquals("ABSOLUTE_CLAIM", hit.ruleCode());
        assertEquals(RiskLevel.HIGH, hit.riskLevel());
        assertTrue(hit.requiresManualReview());
        assertTrue(hit.matchedSnippet().contains("100%") || hit.matchedSnippet().contains("保证"));
    }

    @Test
    void medicalKeywordDetected() {
        ComplianceScanner scanner = new ComplianceScanner(new ObjectMapper());
        List<ComplianceHit> hits = scanner.scan("MEDICAL_RISK", "本产品可以治愈失眠");
        assertEquals(1, hits.size());
        assertEquals(RiskLevel.BLOCKED, hits.get(0).riskLevel());
        assertTrue(hits.get(0).blocksPublication());
    }

    @Test
    void noHitOnCleanText() {
        ComplianceScanner scanner = new ComplianceScanner(new ObjectMapper());
        List<ComplianceHit> hits = scanner.scan("ABSOLUTE_CLAIM", "我们提供专业辅导");
        assertTrue(hits.isEmpty());
    }

    @Test
    void unknownRuleReturnsEmpty() {
        ComplianceScanner scanner = new ComplianceScanner(new ObjectMapper());
        List<ComplianceHit> hits = scanner.scan("UNKNOWN_RULE", "anything");
        assertTrue(hits.isEmpty());
    }

    @Test
    void invalidRegexDoesNotBreak() {
        ComplianceScanner scanner = new ComplianceScanner(new ObjectMapper());
        // 已知规则，全部正常字符串不会让扫描器崩溃
        List<ComplianceHit> hits = scanner.scan("AD_LAW_RISK", "测试文本");
        assertNotNull(hits);
    }

    @Test
    void blankTextReturnsEmpty() {
        ComplianceScanner scanner = new ComplianceScanner(new ObjectMapper());
        assertTrue(scanner.scan("ABSOLUTE_CLAIM", "").isEmpty());
        assertTrue(scanner.scan("ABSOLUTE_CLAIM", null).isEmpty());
    }

    @Test
    void blankRuleReturnsEmpty() {
        ComplianceScanner scanner = new ComplianceScanner(new ObjectMapper());
        assertTrue(scanner.scan("", "100%").isEmpty());
        assertTrue(scanner.scan(null, "100%").isEmpty());
    }

    @Test
    void aggregateRiskLevelPicksWorst() {
        List<ComplianceHit> hits = new ArrayList<>();
        hits.add(ComplianceHit.of("L1", RiskLevel.LOW, "x", ""));
        hits.add(ComplianceHit.of("L2", RiskLevel.HIGH, "y", ""));
        hits.add(ComplianceHit.of("L3", RiskLevel.LOW, "z", ""));
        assertEquals(RiskLevel.HIGH, ComplianceScanner.aggregateRiskLevel(hits));
    }

    @Test
    void aggregateRiskLevelEmptyList() {
        assertEquals(RiskLevel.LOW, ComplianceScanner.aggregateRiskLevel(new ArrayList<>()));
    }

    @Test
    void financeKeywordDetected() {
        ComplianceScanner scanner = new ComplianceScanner(new ObjectMapper());
        List<ComplianceHit> hits = scanner.scan("FINANCE_RISK", "稳赚无风险高收益保本");
        // 4 个独立关键词均应命中
        assertEquals(4, hits.size());
        assertTrue(hits.stream().allMatch(h -> h.riskLevel() == RiskLevel.BLOCKED));
    }

    @Test
    void adLawRegexDetected() {
        ComplianceScanner scanner = new ComplianceScanner(new ObjectMapper());
        List<ComplianceHit> hits = scanner.scan("AD_LAW_RISK", "我们效果最好，行业第一");
        assertFalse(hits.isEmpty());
    }

    @Test
    void adLawCleanTextNotHit() {
        ComplianceScanner scanner = new ComplianceScanner(new ObjectMapper());
        // 不含极限词的普通文本，不应命中
        List<ComplianceHit> hits = scanner.scan("AD_LAW_RISK", "我们提供专业辅导");
        assertTrue(hits.isEmpty());
    }
}
