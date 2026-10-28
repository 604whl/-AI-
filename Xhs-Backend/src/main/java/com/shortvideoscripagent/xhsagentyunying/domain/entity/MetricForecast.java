package com.shortvideoscripagent.xhsagentyunying.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.shortvideoscripagent.xhsagentyunying.config.mybatis.JsonbStringTypeHandler;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * S3-6 预测实体（{@code app.metric_forecast}，V57）。
 * <p>
 * <b>核心约束</b>：预测值只存在 {@code predictedMetrics}（JSONB，固定 10 个核心键），
 * 实际值只存在 {@code app.metric_snapshot.metrics}，两者<b>绝不混在同一列</b>。
 * </p>
 * <p>
 * 预测必须带来源与时间：{@code modelName} / {@code promptVersion} / {@code kbVersion} /
 * {@code generatedAt}，与实际值的 {@code metric_source} / {@code collected_at} 同屏展示。
 * </p>
 */
@Data
@TableName(value = "metric_forecast", autoResultMap = true)
public class MetricForecast {

    @TableId(type = IdType.ASSIGN_UUID)
    private UUID id;

    /** 所属工作区（VARCHAR(32)，与 app.workspace.id 一致） */
    private String workspaceId;

    /** 关联内容资产（可空；资产被删除时置空） */
    private UUID assetId;

    /** 平台代码 */
    private String platformCode;

    /** 预测类型：TITLE_CTR / IMPRESSION / ENGAGEMENT / CONVERSION */
    private String forecastType;

    /** 统一口径预测值 JSONB（固定 10 个核心键，缺失补 0） */
    @TableField(typeHandler = JsonbStringTypeHandler.class)
    private String predictedMetrics;

    /** 预期发布时间（预测发现时间，与实际 collected_at 同屏展示） */
    private OffsetDateTime predictedPublishedAt;

    /** 预测所用模型（来源标识） */
    private String modelName;

    /** 预测所用提示词版本（来源标识） */
    private String promptVersion;

    /** 预测所用知识库版本（来源标识） */
    private String kbVersion;

    /** 可信度：HIGH / MEDIUM / LOW / UNVERIFIED */
    private String confidence;

    /** 预测生成时间 */
    private OffsetDateTime generatedAt;

    /** 来源生成任务 ID（可空） */
    private UUID sourceTaskId;

    /** 幂等键（工作区内唯一） */
    private String idempotencyKey;

    private OffsetDateTime createdAt;

    // ── 常量 ────────────────────────────────────────────────────────────

    public static final String TYPE_TITLE_CTR = "TITLE_CTR";
    public static final String TYPE_IMPRESSION = "IMPRESSION";
    public static final String TYPE_ENGAGEMENT = "ENGAGEMENT";
    public static final String TYPE_CONVERSION = "CONVERSION";

    public static final String CONFIDENCE_HIGH = "HIGH";
    public static final String CONFIDENCE_MEDIUM = "MEDIUM";
    public static final String CONFIDENCE_LOW = "LOW";
    public static final String CONFIDENCE_UNVERIFIED = "UNVERIFIED";
}
