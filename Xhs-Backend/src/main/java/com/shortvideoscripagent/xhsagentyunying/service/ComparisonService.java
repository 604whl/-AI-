package com.shortvideoscripagent.xhsagentyunying.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shortvideoscripagent.xhsagentyunying.common.exception.BusinessException;
import com.shortvideoscripagent.xhsagentyunying.connector.MetricKeys;
import com.shortvideoscripagent.xhsagentyunying.domain.entity.MetricComparison;
import com.shortvideoscripagent.xhsagentyunying.domain.entity.MetricForecast;
import com.shortvideoscripagent.xhsagentyunying.domain.entity.MetricSnapshot;
import com.shortvideoscripagent.xhsagentyunying.domain.mapper.MetricComparisonMapper;
import com.shortvideoscripagent.xhsagentyunying.domain.mapper.MetricForecastMapper;
import com.shortvideoscripagent.xhsagentyunying.domain.mapper.MetricSnapshotMapper;
import com.shortvideoscripagent.xhsagentyunying.dto.analysis.PaginatedResponse;
import com.shortvideoscripagent.xhsagentyunying.dto.forecast.ComparisonCreateRequest;
import com.shortvideoscripagent.xhsagentyunying.dto.forecast.ComparisonResponse;
import com.shortvideoscripagent.xhsagentyunying.infra.audit.AuditWriter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * S3-6 预测 vs 实际偏差计算服务。
 * <p>
 * <b>核心约束</b>：
 * <ul>
 *   <li>偏差计算<b>必须</b>用统一口径 {@link MetricKeys#CORE_KEYS}，与 S3-5 快照口径一致</li>
 *   <li>每个维度一行（{@code app.metric_comparison}），预测值来自
 *       {@code metric_forecast.predicted_metrics}，实际值来自 {@code metric_snapshot.metrics}</li>
 *   <li>命中判定：实际 / 预测 ≥ 0.8 → {@code HIT}；≤ 0.5 → {@code MISS}；中间 → {@code PARTIAL}</li>
 *   <li>实际为 0（或预测为 0）时 {@code deviation_pct} 置 {@code null}，
 *       不写 Infinity、不写 0 假装一致</li>
 *   <li>预测缺失时<b>不生成</b>对比行（由复盘层显式说明"无预测值"，不构成预测失败）</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ComparisonService {

    // ── 错误码 ──────────────────────────────────────────────────────────
    /** 404 预测或快照不存在 / 越权 */
    public static final int CODE_FORECAST_OR_SNAPSHOT_NOT_FOUND = 40452;
    /** 400 对比参数非法 */
    public static final int CODE_COMPARISON_INVALID = 40072;

    /** 命中阈值：实际 / 预测 ≥ 0.8 */
    private static final BigDecimal HIT_RATIO = new BigDecimal("0.8");
    /** 命中阈值：实际 / 预测 ≤ 0.5 */
    private static final BigDecimal MISS_RATIO = new BigDecimal("0.5");

    private final MetricComparisonMapper comparisonMapper;
    private final MetricForecastMapper forecastMapper;
    private final MetricSnapshotMapper snapshotMapper;
    private final AuditWriter auditWriter;
    private final ObjectMapper objectMapper;

    // ══════════════════════════════════════════════════════════════════
    // 计算偏差
    // ══════════════════════════════════════════════════════════════════

    /**
     * 计算一次"预测 vs 实际"的偏差（每个核心维度一行，幂等）。
     *
     * @return 各维度对比行
     */
    @Transactional
    public List<ComparisonResponse> create(String workspaceId, Long userId,
                                            ComparisonCreateRequest request, String idempotencyKey) {
        requireWorkspace(workspaceId);

        UUID forecastId = parseUuid(request.getForecastId());
        UUID snapshotId = parseUuid(request.getSnapshotId());
        if (forecastId == null || snapshotId == null) {
            throw new BusinessException(CODE_COMPARISON_INVALID, "COMPARISON_INVALID: bad_uuid");
        }

        String key = normalizeKey(idempotencyKey);
        if (key != null) {
            // 行级幂等键带维度后缀（同一请求产生 10 行，不能共用一个唯一键）
            MetricComparison existing = comparisonMapper.selectByIdempotencyKey(
                    workspaceId, rowKey(key, MetricKeys.CORE_KEYS.get(0)));
            if (existing != null) {
                log.info("Comparison create skipped (idempotent): ws={} key={}", workspaceId, key);
                return comparisonMapper.selectByForecastAndSnapshot(workspaceId, forecastId, snapshotId)
                        .stream().map(this::toResponse).toList();
            }
        }

        // 归属校验：预测与快照都必须属于当前 workspace，否则按"未找到"处理
        MetricForecast forecast = forecastMapper.selectByIdAndWorkspace(forecastId, workspaceId);
        if (forecast == null) {
            throw new BusinessException(CODE_FORECAST_OR_SNAPSHOT_NOT_FOUND, "FORECAST_OR_SNAPSHOT_NOT_FOUND");
        }
        MetricSnapshot snapshot = snapshotMapper.selectByIdAndWorkspace(snapshotId, workspaceId);
        if (snapshot == null) {
            throw new BusinessException(CODE_FORECAST_OR_SNAPSHOT_NOT_FOUND, "FORECAST_OR_SNAPSHOT_NOT_FOUND");
        }

        Map<String, Long> predicted = readCoreMetrics(forecast.getPredictedMetrics());
        Map<String, Long> actual = readCoreMetrics(snapshot.getMetrics());

        // 预测缺失（预测 JSON 为空）→ 不生成对比行
        if (predicted.isEmpty()) {
            throw new BusinessException(CODE_COMPARISON_INVALID,
                    "COMPARISON_INVALID: forecast_has_no_predicted_metrics");
        }

        UUID assetId = forecast.getAssetId() != null ? forecast.getAssetId() : snapshot.getAssetId();
        OffsetDateTime now = OffsetDateTime.now();

        List<MetricComparison> rows = new ArrayList<>();
        for (String dimension : MetricKeys.CORE_KEYS) {
            BigDecimal predictedValue = BigDecimal.valueOf(predicted.getOrDefault(dimension, 0L));
            BigDecimal actualValue = BigDecimal.valueOf(actual.getOrDefault(dimension, 0L));

            BigDecimal deviationAbs = actualValue.subtract(predictedValue).abs();
            BigDecimal deviationPct = null;
            if (predictedValue.signum() != 0) {
                deviationPct = actualValue.subtract(predictedValue)
                        .divide(predictedValue, 6, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100))
                        .setScale(4, RoundingMode.HALF_UP);
            }

            MetricComparison row = new MetricComparison();
            row.setId(UUID.randomUUID());
            row.setWorkspaceId(workspaceId);
            row.setForecastId(forecastId);
            row.setSnapshotId(snapshotId);
            row.setAssetId(assetId);
            row.setDimension(dimension);
            row.setPredictedValue(predictedValue);
            row.setActualValue(actualValue);
            row.setDeviationAbs(deviationAbs);
            row.setDeviationPct(deviationPct);
            row.setHit(judgeHit(predictedValue, actualValue, deviationPct));
            row.setDimensionCategory(categorize(dimension));
            row.setReason(buildReason(dimension, predictedValue, actualValue, deviationPct,
                    row.getHit()));
            row.setComputedAt(now);
            row.setIdempotencyKey(key == null ? null : rowKey(key, dimension));
            row.setCreatedAt(now);
            rows.add(row);
        }

        int inserted = 0;
        for (MetricComparison row : rows) {
            try {
                comparisonMapper.insert(row);
                inserted++;
            } catch (DuplicateKeyException ex) {
                log.info("Comparison row duplicate skipped (idempotent): forecast={} snapshot={} dim={}",
                        forecastId, snapshotId, row.getDimension());
            }
        }

        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("forecastId", forecastId.toString());
        meta.put("snapshotId", snapshotId.toString());
        meta.put("rows", inserted);
        auditWriter.record("comparison.create", "METRIC_COMPARISON", forecastId.toString(), meta);

        log.info("Comparison computed: forecast={} snapshot={} rows={}", forecastId, snapshotId, inserted);
        return comparisonMapper.selectByForecastAndSnapshot(workspaceId, forecastId, snapshotId)
                .stream().map(this::toResponse).toList();
    }

    // ══════════════════════════════════════════════════════════════════
    // 列表
    // ══════════════════════════════════════════════════════════════════

    /**
     * 分页列出当前工作区的对比行。
     */
    public PaginatedResponse<ComparisonResponse> list(String workspaceId, String assetId,
                                                       String dimension, String hit,
                                                       int page, int size) {
        requireWorkspace(workspaceId);
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(size, 1), 200);
        long offset = (long) (safePage - 1) * safeSize;

        UUID parsedAsset = parseUuid(assetId);
        String parsedDimension = normalizeDimension(dimension);
        String parsedHit = upper(hit);

        List<ComparisonResponse> items = comparisonMapper.listByWorkspace(workspaceId, parsedAsset,
                        parsedDimension, parsedHit, offset, safeSize)
                .stream().map(this::toResponse).toList();
        long total = comparisonMapper.countByWorkspace(workspaceId, parsedAsset, parsedDimension, parsedHit);
        return new PaginatedResponse<>(items, total, safePage, safeSize);
    }

    // ══════════════════════════════════════════════════════════════════
    // 内部工具
    // ══════════════════════════════════════════════════════════════════

    /**
     * 命中判定：实际 / 预测 ≥ 0.8 → HIT；≤ 0.5 → MISS；中间 → PARTIAL。
     * <p>
     * 预测为 0 时无法计算比率：实际也为 0 记 HIT（无偏差），实际 &gt; 0 记 MISS。
     * </p>
     */
    private String judgeHit(BigDecimal predicted, BigDecimal actual, BigDecimal deviationPct) {
        if (predicted.signum() == 0) {
            return actual.signum() == 0 ? MetricComparison.HIT_HIT : MetricComparison.HIT_MISS;
        }
        BigDecimal ratio = actual.divide(predicted, 6, RoundingMode.HALF_UP);
        if (ratio.compareTo(HIT_RATIO) >= 0) {
            return MetricComparison.HIT_HIT;
        }
        if (ratio.compareTo(MISS_RATIO) <= 0) {
            return MetricComparison.HIT_MISS;
        }
        return MetricComparison.HIT_PARTIAL;
    }

    /**
     * 维度归类：把统一口径核心键映射到内容维度，便于复盘给出可执行建议。
     */
    private String categorize(String dimension) {
        return switch (dimension) {
            case MetricKeys.IMPRESSIONS, MetricKeys.PLAYS, MetricKeys.CLICKS ->
                    MetricComparison.CATEGORY_COVER;
            case MetricKeys.LIKES -> MetricComparison.CATEGORY_TITLE;
            case MetricKeys.COLLECTS -> MetricComparison.CATEGORY_CONTENT_BODY;
            case MetricKeys.COMMENTS, MetricKeys.SHARES -> MetricComparison.CATEGORY_CTA;
            case MetricKeys.FOLLOWS, MetricKeys.DM_INQUIRIES, MetricKeys.CONVERSIONS ->
                    MetricComparison.CATEGORY_DISTRIBUTION;
            default -> MetricComparison.CATEGORY_TIMING;
        };
    }

    /**
     * 偏差原因说明（可追溯的确定性描述，不做无依据的归因）。
     */
    private String buildReason(String dimension, BigDecimal predicted, BigDecimal actual,
                               BigDecimal deviationPct, String hit) {
        if (predicted.signum() == 0) {
            return actual.signum() == 0
                    ? "预测值为 0，实际值也为 0，无偏差"
                    : "预测值为 0，实际值 " + actual.stripTrailingZeros().toPlainString()
                    + "，缺少预测基准";
        }
        if (actual.signum() == 0) {
            return "预测 " + predicted.stripTrailingZeros().toPlainString()
                    + "，实际为 0，偏差率不可计算";
        }
        String pct = deviationPct == null
                ? "不可计算"
                : deviationPct.stripTrailingZeros().toPlainString() + "%";
        return switch (hit) {
            case MetricComparison.HIT_HIT -> "实际值达到预测值的 80% 以上（偏差率 " + pct + "）";
            case MetricComparison.HIT_MISS -> "实际值不足预测值的 50%（偏差率 " + pct + "）";
            default -> "实际值介于预测值的 50%~80% 之间（偏差率 " + pct + "）";
        };
    }

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
            log.warn("Failed to parse metrics JSON for comparison: {}", ex.getMessage());
        }
        return core;
    }

    private ComparisonResponse toResponse(MetricComparison c) {
        return ComparisonResponse.builder()
                .id(c.getId())
                .forecastId(c.getForecastId())
                .snapshotId(c.getSnapshotId())
                .assetId(c.getAssetId())
                .dimension(c.getDimension())
                .predictedValue(c.getPredictedValue())
                .actualValue(c.getActualValue())
                .deviationAbs(c.getDeviationAbs())
                .deviationPct(c.getDeviationPct())
                .hit(c.getHit())
                .dimensionCategory(c.getDimensionCategory())
                .reason(c.getReason())
                .computedAt(c.getComputedAt())
                .build();
    }

    private void requireWorkspace(String workspaceId) {
        if (workspaceId == null || workspaceId.isBlank()) {
            throw new BusinessException(CODE_COMPARISON_INVALID, "COMPARISON_INVALID: workspace_id_required");
        }
    }

    private String normalizeKey(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String trimmed = raw.trim();
        return trimmed.length() > 100 ? trimmed.substring(0, 100) : trimmed;
    }

    /**
     * 组合行级幂等键（幂等键 + 维度），保证同一请求的多行不互相冲突。
     */
    private String rowKey(String key, String dimension) {
        String combined = key + ":" + dimension;
        return combined.length() > 128 ? combined.substring(0, 128) : combined;
    }

    private String normalizeDimension(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String value = raw.trim();
        return MetricKeys.CORE_KEYS.contains(value) ? value : null;
    }

    private String upper(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return raw.trim().toUpperCase(Locale.ROOT);
    }

    private UUID parseUuid(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(raw.trim());
        } catch (IllegalArgumentException ex) {
            return null;
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
}
