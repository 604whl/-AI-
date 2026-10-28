package com.shortvideoscripagent.xhsagentyunying.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shortvideoscripagent.xhsagentyunying.common.exception.BusinessException;
import com.shortvideoscripagent.xhsagentyunying.connector.MetricKeys;
import com.shortvideoscripagent.xhsagentyunying.domain.entity.RetrospectiveFeedback;
import com.shortvideoscripagent.xhsagentyunying.dto.retrospective.TemplateEffectStatsResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * S3-8 模板 / 知识库效果统计回写服务。
 * <p>
 * <b>设计取舍（对齐验收文档「不新建模板统计表」）</b>：
 * <ul>
 *   <li>模板效果统计复用既有 {@code app.template_apply_log}，通过 S3-8 新增的
 *       {@code effect_stats} JSONB 列承载"效果"字段（采用率 → 实际表现）</li>
 *   <li>知识库回写<b>只写效果统计样本</b>（指标 + 标签）到 {@code kb.kb_document.metadata}，
 *       <b>不写 content</b>：单条复盘结果不得直接成为知识库文档正文；
 *       需要正文更新时必须走 {@code KbSourceLoader} 重新分块 + 向量化</li>
 * </ul>
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TemplateEffectStatsService {

    /** 404 模板不存在 */
    public static final int CODE_TEMPLATE_NOT_FOUND = 40470;
    /** 404 知识库文档不存在 */
    public static final int CODE_KB_DOCUMENT_NOT_FOUND = 40471;
    /** 400 回写目标非法 */
    public static final int CODE_INVALID_TARGET = 40074;

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    // ══════════════════════════════════════════════════════════════════
    // 模板效果统计
    // ══════════════════════════════════════════════════════════════════

    /**
     * 查询模板效果统计。
     * <p>
     * 口径：
     * <ul>
     *   <li>{@code applyCount}：{@code app.template_apply_log} 中该模板的引用次数</li>
     *   <li>{@code adoptionRate}：引用中资产状态为 published 的比例（%）</li>
     *   <li>{@code avgImpressions} / {@code avgCollectRate} / {@code avgDmInquiries}：
     *       基于引用资产的<b>最新一条</b>指标快照聚合</li>
     *   <li>{@code sampleSize}：参与效果聚合的样本数（有快照的引用数）</li>
     * </ul>
     * </p>
     */
    public TemplateEffectStatsResponse getEffectStats(String workspaceId, UUID templateId) {
        if (workspaceId == null || workspaceId.isBlank()) {
            throw new BusinessException(CODE_TEMPLATE_NOT_FOUND, "TEMPLATE_NOT_FOUND");
        }
        Integer templateCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM app.template WHERE id = ?", Integer.class, templateId);
        if (templateCount == null || templateCount == 0) {
            throw new BusinessException(CODE_TEMPLATE_NOT_FOUND, "TEMPLATE_NOT_FOUND");
        }

        List<String> title = jdbcTemplate.query(
                "SELECT title FROM app.template WHERE id = ?",
                (rs, i) -> rs.getString(1), templateId);
        String templateTitle = (title == null || title.isEmpty()) ? null : title.get(0);

        List<ApplyStatRow> rows = jdbcTemplate.query("""
                SELECT l.asset_id,
                       a.status AS asset_status,
                       s.metrics AS metrics
                  FROM app.template_apply_log l
                  LEFT JOIN app.content_asset a ON a.id = l.asset_id
                  LEFT JOIN LATERAL (
                        SELECT ms.metrics
                          FROM app.metric_snapshot ms
                         WHERE ms.asset_id = l.asset_id
                           AND ms.workspace_id = l.workspace_id
                         ORDER BY ms.collected_at DESC, ms.created_at DESC
                         LIMIT 1
                  ) s ON TRUE
                 WHERE l.template_id = ?
                   AND l.workspace_id = ?
                """,
                (rs, i) -> {
                    ApplyStatRow row = new ApplyStatRow();
                    row.assetStatus = rs.getString("asset_status");
                    row.metrics = rs.getString("metrics");
                    return row;
                }, templateId, workspaceId);

        long applyCount = rows.size();
        long publishedCount = rows.stream()
                .filter(r -> "published".equals(r.assetStatus)).count();
        BigDecimal adoptionRate = applyCount == 0
                ? BigDecimal.ZERO
                : BigDecimal.valueOf(publishedCount)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(applyCount), 2, RoundingMode.HALF_UP);

        BigDecimal impressionsSum = BigDecimal.ZERO;
        BigDecimal collectRateSum = BigDecimal.ZERO;
        BigDecimal dmSum = BigDecimal.ZERO;
        int impressionsSamples = 0;
        int collectRateSamples = 0;
        int dmSamples = 0;

        for (ApplyStatRow row : rows) {
            Map<String, Long> metrics = readCoreMetrics(row.metrics);
            if (metrics.isEmpty()) {
                continue;
            }
            long impressions = metrics.getOrDefault(MetricKeys.IMPRESSIONS, 0L);
            if (impressions > 0) {
                impressionsSum = impressionsSum.add(BigDecimal.valueOf(impressions));
                impressionsSamples++;
                long collects = metrics.getOrDefault(MetricKeys.COLLECTS, 0L);
                collectRateSum = collectRateSum.add(
                        BigDecimal.valueOf(collects).multiply(BigDecimal.valueOf(100))
                                .divide(BigDecimal.valueOf(impressions), 4, RoundingMode.HALF_UP));
                collectRateSamples++;
            }
            long dm = metrics.getOrDefault(MetricKeys.DM_INQUIRIES, 0L);
            if (dm > 0) {
                dmSum = dmSum.add(BigDecimal.valueOf(dm));
                dmSamples++;
            }
        }

        long sampleSize = rows.stream().filter(r -> r.metrics != null && !r.metrics.isBlank()).count();

        return TemplateEffectStatsResponse.builder()
                .templateId(templateId)
                .templateTitle(templateTitle)
                .applyCount(applyCount)
                .adoptionRate(adoptionRate)
                .avgImpressions(impressionsSamples == 0 ? BigDecimal.ZERO
                        : impressionsSum.divide(BigDecimal.valueOf(impressionsSamples), 2, RoundingMode.HALF_UP))
                .avgCollectRate(collectRateSamples == 0 ? BigDecimal.ZERO
                        : collectRateSum.divide(BigDecimal.valueOf(collectRateSamples), 2, RoundingMode.HALF_UP))
                .avgDmInquiries(dmSamples == 0 ? BigDecimal.ZERO
                        : dmSum.divide(BigDecimal.valueOf(dmSamples), 2, RoundingMode.HALF_UP))
                .sampleSize(sampleSize)
                .build();
    }

    // ══════════════════════════════════════════════════════════════════
    // 回写（审批通过后调用）
    // ══════════════════════════════════════════════════════════════════

    /**
     * 把复盘结果写回模板效果统计。
     * <p>
     * 写回方式：在 {@code app.template_apply_log} 中匹配（template_id + asset_id）的行上
     * 写入 {@code effect_stats} JSONB；没有匹配行时插入一条新的效果样本行。
     * </p>
     *
     * @return 实际写入的样本行数
     */
    @Transactional
    public int applyTemplateEffect(String workspaceId, RetrospectiveFeedback feedback, UUID assetId) {
        UUID templateId;
        try {
            templateId = UUID.fromString(feedback.getTargetId().trim());
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(CODE_INVALID_TARGET, "INVALID_TARGET: bad_template_id");
        }
        Integer exists = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM app.template WHERE id = ?", Integer.class, templateId);
        if (exists == null || exists == 0) {
            throw new BusinessException(CODE_TEMPLATE_NOT_FOUND, "TEMPLATE_NOT_FOUND");
        }

        Map<String, Object> stats = readJsonObject(feedback.getProposedStats());
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("reportId", feedback.getReportId() == null ? null : feedback.getReportId().toString());
        payload.put("feedbackId", feedback.getId() == null ? null : feedback.getId().toString());
        payload.put("targetType", RetrospectiveFeedback.TARGET_TEMPLATE);
        payload.put("targetId", feedback.getTargetId());
        payload.put("stats", stats);
        payload.put("appliedAt", OffsetDateTime.now().toString());
        String effectStats = writeJson(payload);

        Long userId = feedback.getCreatedBy() == null ? feedback.getReviewedBy() : feedback.getCreatedBy();
        int updated;
        if (assetId != null) {
            updated = jdbcTemplate.update(
                    "UPDATE app.template_apply_log SET effect_stats = ?::jsonb "
                            + "WHERE template_id = ? AND workspace_id = ? AND asset_id = ? "
                            + "AND (effect_stats IS NULL OR effect_stats = '{}'::jsonb)",
                    effectStats, templateId, workspaceId, assetId);
        } else {
            updated = 0;
        }
        if (updated == 0) {
            jdbcTemplate.update("""
                    INSERT INTO app.template_apply_log
                        (id, template_id, asset_id, workspace_id, user_id, effect_stats, created_at)
                    VALUES (?, ?, ?, ?, ?, ?::jsonb, NOW())
                    """,
                    UUID.randomUUID(), templateId, assetId, workspaceId, userId, effectStats);
            updated = 1;
        }
        log.info("Template effect stats applied: template={} feedback={} rows={}",
                templateId, feedback.getId(), updated);
        return updated;
    }

    /**
     * 把复盘结果写回知识库效果统计。
     * <p>
     * <b>污染风险控制</b>：只写 {@code kb.kb_document.metadata} 的 {@code effect_stats} 数组，
     * <b>绝不写 {@code content}</b>——单条复盘结果不得直接成为知识库文档正文。
     * 需要正文更新时必须由 {@code KbSourceLoader} 重新分块 + 向量化。
     * </p>
     */
    @Transactional
    public int applyKbDocumentEffect(String workspaceId, RetrospectiveFeedback feedback) {
        String docId = feedback.getTargetId() == null ? null : feedback.getTargetId().trim();
        if (docId == null || docId.isEmpty()) {
            throw new BusinessException(CODE_INVALID_TARGET, "INVALID_TARGET: kb_doc_id_required");
        }
        Integer exists = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM kb.kb_document WHERE doc_id = ?", Integer.class, docId);
        if (exists == null || exists == 0) {
            throw new BusinessException(CODE_KB_DOCUMENT_NOT_FOUND, "KB_DOCUMENT_NOT_FOUND");
        }

        Map<String, Object> sample = new LinkedHashMap<>();
        sample.put("reportId", feedback.getReportId() == null ? null : feedback.getReportId().toString());
        sample.put("feedbackId", feedback.getId() == null ? null : feedback.getId().toString());
        sample.put("stats", readJsonObject(feedback.getProposedStats()));
        sample.put("appliedAt", OffsetDateTime.now().toString());

        // metadata 追加 effect_stats 数组（只改 metadata，不改 content）
        int updated = jdbcTemplate.update("""
                UPDATE kb.kb_document
                   SET metadata = jsonb_set(
                           COALESCE(metadata, '{}'::jsonb),
                           '{effect_stats}',
                           COALESCE(metadata -> 'effect_stats', '[]'::jsonb) || ?::jsonb,
                           true),
                       updated_at = NOW()
                 WHERE doc_id = ?
                """, writeJson(List.of(sample)), docId);
        log.info("KB effect stats applied (metadata only, content untouched): doc={} feedback={} rows={}",
                docId, feedback.getId(), updated);
        return updated;
    }

    // ══════════════════════════════════════════════════════════════════
    // 内部工具
    // ══════════════════════════════════════════════════════════════════

    private Map<String, Long> readCoreMetrics(String json) {
        Map<String, Long> core = new LinkedHashMap<>();
        if (json == null || json.isBlank()) {
            return core;
        }
        try {
            Map<String, Object> parsed = objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {
            });
            for (String key : MetricKeys.CORE_KEYS) {
                core.put(key, toLong(parsed.get(key)));
            }
        } catch (Exception ex) {
            log.warn("Failed to parse snapshot metrics for effect stats: {}", ex.getMessage());
        }
        return core;
    }

    private Map<String, Object> readJsonObject(String json) {
        if (json == null || json.isBlank()) {
            return new LinkedHashMap<>();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {
            });
        } catch (Exception ex) {
            log.warn("Failed to parse proposed_stats JSON: {}", ex.getMessage());
            return new LinkedHashMap<>();
        }
    }

    private long toLong(Object value) {
        if (value == null) {
            return 0L;
        }
        if (value instanceof Number n) {
            return n.longValue();
        }
        try {
            return Long.parseLong(String.valueOf(value).trim());
        } catch (NumberFormatException ex) {
            return 0L;
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            log.warn("Failed to serialize effect stats JSON: {}", ex.getMessage());
            return "{}";
        }
    }

    /** 模板引用统计行 */
    private static final class ApplyStatRow {
        private String assetStatus;
        private String metrics;
    }
}
