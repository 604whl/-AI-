package com.shortvideoscripagent.xhsagentyunying.dto.forecast;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * S3-6 偏差对比响应。
 * <p>
 * 预测值与实际值<b>分字段</b>返回：{@code predictedValue} / {@code actualValue}，
 * 不提供混合的 {@code metrics} 字段。
 * </p>
 */
@Data
@Builder
public class ComparisonResponse {

    /** 对比行 ID */
    private UUID id;

    /** 预测 ID */
    private UUID forecastId;

    /** 实际快照 ID */
    private UUID snapshotId;

    /** 关联内容资产 */
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

    /** 维度归类 */
    private String dimensionCategory;

    /** 偏差原因说明 */
    private String reason;

    /** 计算时间 */
    private OffsetDateTime computedAt;
}
