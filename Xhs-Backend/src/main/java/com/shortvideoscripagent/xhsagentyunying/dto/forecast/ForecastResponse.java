package com.shortvideoscripagent.xhsagentyunying.dto.forecast;

import lombok.Builder;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * S3-6 预测响应。
 * <p>
 * <b>预测与实际分字段</b>：这里只返回预测侧（{@code predictedMetrics} + {@code source}），
 * 实际侧在 {@code MetricSnapshotResponse.metrics}，两者不混在同一字段。
 * </p>
 */
@Data
@Builder
public class ForecastResponse {

    /** 预测 ID */
    private UUID id;

    /** 关联内容资产 */
    private UUID assetId;

    /** 平台代码 */
    private String platformCode;

    /** 预测类型 */
    private String forecastType;

    /** 统一口径预测值（10 核心键） */
    private Map<String, Long> predictedMetrics;

    /** 预期发布时间 */
    private OffsetDateTime predictedPublishedAt;

    /** 可信度 */
    private String confidence;

    /** 预测来源与时间（与实际 metric_source / collected_at 同屏展示） */
    private ForecastSource source;

    /** 创建时间 */
    private OffsetDateTime createdAt;

    /**
     * 预测来源标识。
     */
    @Data
    @Builder
    public static class ForecastSource {
        /** 模型 */
        private String modelName;
        /** 提示词版本 */
        private String promptVersion;
        /** 知识库版本 */
        private String kbVersion;
        /** 预测生成时间 */
        private OffsetDateTime generatedAt;
    }
}
