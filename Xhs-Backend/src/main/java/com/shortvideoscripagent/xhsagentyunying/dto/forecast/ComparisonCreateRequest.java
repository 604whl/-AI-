package com.shortvideoscripagent.xhsagentyunying.dto.forecast;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * S3-6 创建预测 vs 实际对比请求。
 * <p>
 * 只需给出 {@code forecastId} + {@code snapshotId}，偏差由服务层按统一口径自动计算，
 * 每个核心维度生成一行对比记录。
 * </p>
 */
@Data
public class ComparisonCreateRequest {

    /** 预测 ID */
    @NotBlank(message = "forecast_id_required")
    @Size(max = 64)
    private String forecastId;

    /** 实际指标快照 ID */
    @NotBlank(message = "snapshot_id_required")
    @Size(max = 64)
    private String snapshotId;
}
