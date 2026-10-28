package com.shortvideoscripagent.xhsagentyunying.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * S3-6 预测 vs 实际偏差实体（{@code app.metric_comparison}，V57）。
 * <p>
 * <b>一个维度一行</b>：{@code dimension} 为统一口径核心键，
 * {@code predictedValue} 来自 {@code metric_forecast.predicted_metrics}，
 * {@code actualValue} 来自 {@code metric_snapshot.metrics}。
 * </p>
 * <p>
 * 命中判定：实际 / 预测 ≥ 0.8 记 {@code HIT}；≤ 0.5 记 {@code MISS}；中间记 {@code PARTIAL}。
 * 实际为 0（或预测为 0）时 {@code deviationPct} 置 {@code null}，不写 Infinity、不写 0 假装一致。
 * </p>
 */
@Data
@TableName(value = "metric_comparison", autoResultMap = true)
public class MetricComparison {

    @TableId(type = IdType.ASSIGN_UUID)
    private UUID id;

    /** 所属工作区 */
    private String workspaceId;

    /** 预测 ID */
    private UUID forecastId;

    /** 实际快照 ID */
    private UUID snapshotId;

    /** 关联内容资产（可空） */
    private UUID assetId;

    /** 维度（统一口径核心键） */
    private String dimension;

    /** 预测值 */
    private BigDecimal predictedValue;

    /** 实际值 */
    private BigDecimal actualValue;

    /** 偏差绝对值 */
    private BigDecimal deviationAbs;

    /** 偏差率（%）；实际为 0 或预测为 0 时为 null */
    private BigDecimal deviationPct;

    /** 命中判定：HIT / MISS / PARTIAL */
    private String hit;

    /** 维度归类：TITLE / COVER / TIMING / CTA / CONTENT_BODY / DISTRIBUTION */
    private String dimensionCategory;

    /** 偏差原因说明 */
    private String reason;

    /** 偏差计算时间 */
    private OffsetDateTime computedAt;

    /** 幂等键（工作区内唯一） */
    private String idempotencyKey;

    private OffsetDateTime createdAt;

    // ── 常量 ────────────────────────────────────────────────────────────

    public static final String HIT_HIT = "HIT";
    public static final String HIT_MISS = "MISS";
    public static final String HIT_PARTIAL = "PARTIAL";

    public static final String CATEGORY_TITLE = "TITLE";
    public static final String CATEGORY_COVER = "COVER";
    public static final String CATEGORY_TIMING = "TIMING";
    public static final String CATEGORY_CTA = "CTA";
    public static final String CATEGORY_CONTENT_BODY = "CONTENT_BODY";
    public static final String CATEGORY_DISTRIBUTION = "DISTRIBUTION";
}
