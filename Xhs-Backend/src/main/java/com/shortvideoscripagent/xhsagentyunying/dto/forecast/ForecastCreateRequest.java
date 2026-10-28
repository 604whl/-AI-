package com.shortvideoscripagent.xhsagentyunying.dto.forecast;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.Map;

/**
 * S3-6 创建预测请求。
 * <p>
 * {@code predictedMetrics} 使用统一口径 10 键（{@code connector.MetricKeys#CORE_KEYS}），
 * 缺失键自动补 0；核心键之外的字段会被剔除（不污染预测口径）。
 * </p>
 */
@Data
public class ForecastCreateRequest {

    /** 关联内容资产（可空） */
    @Size(max = 64)
    private String assetId;

    /** 平台代码（可空；填了则校验与资产平台一致） */
    @Size(max = 32)
    private String platformCode;

    /** 预测类型：TITLE_CTR / IMPRESSION / ENGAGEMENT / CONVERSION */
    @NotBlank(message = "forecast_type_required")
    @Size(max = 32)
    private String forecastType;

    /** 统一口径预测值（10 核心键） */
    private Map<String, Object> predictedMetrics;

    /** 预期发布时间 */
    private java.time.OffsetDateTime predictedPublishedAt;

    /** 预测所用模型（来源标识） */
    @Size(max = 64)
    private String modelName;

    /** 预测所用提示词版本（来源标识） */
    @Size(max = 32)
    private String promptVersion;

    /** 预测所用知识库版本（来源标识） */
    @Size(max = 32)
    private String kbVersion;

    /** 可信度：HIGH / MEDIUM / LOW / UNVERIFIED，默认 LOW */
    @Size(max = 16)
    private String confidence;

    /** 来源生成任务 ID */
    @Size(max = 64)
    private String sourceTaskId;
}
