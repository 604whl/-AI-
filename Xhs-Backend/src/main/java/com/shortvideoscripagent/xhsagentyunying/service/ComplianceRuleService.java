package com.shortvideoscripagent.xhsagentyunying.service;

import com.shortvideoscripagent.xhsagentyunying.domain.entity.ComplianceBrandWord;
import com.shortvideoscripagent.xhsagentyunying.domain.entity.ComplianceRule;
import com.shortvideoscripagent.xhsagentyunying.domain.mapper.ComplianceBrandWordMapper;
import com.shortvideoscripagent.xhsagentyunying.domain.mapper.ComplianceRuleMapper;
import com.shortvideoscripagent.xhsagentyunying.freeze.ComplianceHit;
import com.shortvideoscripagent.xhsagentyunying.freeze.ComplianceScanner;
import com.shortvideoscripagent.xhsagentyunying.freeze.RiskLevel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 合规规则服务（S2-3: DB-backed 规则查询 + 品牌级禁用词扫描）。
 * <p>
 * 核心改进：规则不再从静态内存加载，而是实时查询 DB，支持热更新。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ComplianceRuleService {

    private final ComplianceRuleMapper ruleMapper;
    private final ComplianceBrandWordMapper brandWordMapper;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    /**
     * DB-backed 规则查找（替换静态 RuleCatalog.lookup）。
     */
    public ComplianceScanner.Rule lookup(String code) {
        var rules = ruleMapper.selectList(
            new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ComplianceRule>()
                .eq(ComplianceRule::getCode, code)
                .eq(ComplianceRule::getEnabled, true)
        );
        if (rules.isEmpty()) return null;
        ComplianceRule r = rules.get(0);
        return new ComplianceScanner.Rule(
            r.getCode(),
            ComplianceScanner.DetectorType.valueOf(r.getDetectorType()),
            RiskLevel.valueOf(r.getRiskLevel()),
            r.getDescription(),
            r.getDetectorConfig()
        );
    }

    /**
     * 扫描所有启用的全局规则。
     */
    public List<ComplianceHit> scanGlobalRules(String text) {
        List<ComplianceHit> hits = new ArrayList<>();
        if (text == null || text.isBlank()) return hits;
        for (ComplianceRule rule : ruleMapper.selectAllEnabled()) {
            ComplianceScanner sc = new ComplianceScanner(objectMapper);
            List<ComplianceHit> h = sc.scan(rule.getCode(), text);
            hits.addAll(h);
        }
        return hits;
    }

    /**
     * 扫描品牌级禁用词（按 workspace）。
     */
    public List<ComplianceHit> scanBrandWords(String workspaceId, String text) {
        List<ComplianceHit> hits = new ArrayList<>();
        if (text == null || text.isBlank() || workspaceId == null) return hits;
        for (ComplianceBrandWord w : brandWordMapper.selectByWorkspace(workspaceId)) {
            int idx = text.indexOf(w.getKeyword());
            if (idx >= 0) {
                RiskLevel level = RiskLevel.valueOf(w.getRiskLevel());
                hits.add(ComplianceHit.of(
                    "BRAND_WORD:" + w.getRuleCode(),
                    level,
                    excerpt(text, idx, w.getKeyword().length()),
                    "品牌禁用词，请替换或删除"
                ));
            }
        }
        return hits;
    }

    /**
     * 返回所有规则（含未启用的，用于管理列表）。
     */
    public List<ComplianceRule> getAllRules() {
        return ruleMapper.selectList(null);
    }

    private String excerpt(String text, int idx, int len) {
        int s = Math.max(0, idx - 10);
        int e = Math.min(text.length(), idx + len + 10);
        return (s > 0 ? "..." : "") + text.substring(s, e) + (e < text.length() ? "..." : "");
    }
}