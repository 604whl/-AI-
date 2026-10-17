package com.shortvideoscripagent.xhsagentyunying.service;

import com.shortvideoscripagent.xhsagentyunying.domain.compliance.ComplianceChecker;
import com.shortvideoscripagent.xhsagentyunying.domain.entity.ContentAsset;
import com.shortvideoscripagent.xhsagentyunying.domain.mapper.ContentAssetMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 风险评估服务（P0-6）。
 * <p>
 * 集成 ComplianceChecker，计算 risk_score（0-100）。
 * risk_score > 70 → 高风险。
 * <p>
 * 注意：本服务仅负责评估，不触发审核任务创建（避免与 ReviewService 循环依赖）。
 * 由 ContentAssetService 在 submitReview 时统一编排。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RiskAssessmentService {

    /** 风险分阈值：超过此分数视为高风险 */
    public static final int HIGH_RISK_THRESHOLD = 70;

    private static final int MAX_RISK_SCORE = 100;

    private final ComplianceChecker complianceChecker;
    private final ContentAssetMapper assetMapper;

    // ── 风险评估结果 ────────────────────────────────────────────────────

    /**
     * 风险评估结果。
     */
    public record RiskResult(
            int riskScore,
            List<String> riskTags,
            boolean isHighRisk
    ) {}

    // ── 核心评估方法 ────────────────────────────────────────────────────

    /**
     * 对资产进行合规风险评估。
     *
     * @param assetId 资产 ID
     * @return 风险评估结果
     */
    public RiskResult assessAsset(UUID assetId) {
        ContentAsset asset = assetMapper.selectById(assetId);
        if (asset == null) {
            log.warn("assessAsset: asset {} not found", assetId);
            return new RiskResult(0, List.of(), false);
        }
        return assessContent(asset.getTitle(), asset.getBody());
    }

    /**
     * 对标题 + 正文进行风险评估。
     *
     * @param title 标题（可为 null）
     * @param body  正文（可为 null）
     * @return 风险评估结果
     */
    public RiskResult assessContent(String title, String body) {
        List<Map<String, Object>> warnings = complianceChecker.scan(
                title != null ? title : "",
                body != null ? body : "");

        int score = Math.min(warnings.size() * 20, MAX_RISK_SCORE);
        List<String> tags = warnings.stream()
                .map(w -> String.valueOf(w.getOrDefault("rule", "unknown")))
                .toList();

        boolean highRisk = score > HIGH_RISK_THRESHOLD;

        if (highRisk) {
            log.info("High risk detected: score={}, tags={}", score, tags);
        }

        return new RiskResult(score, tags, highRisk);
    }

    /**
     * 仅计算风险分，不触发审核（用于预览/诊断）。
     */
    public int calculateRiskScore(String title, String body) {
        return assessContent(title, body).riskScore();
    }
}
