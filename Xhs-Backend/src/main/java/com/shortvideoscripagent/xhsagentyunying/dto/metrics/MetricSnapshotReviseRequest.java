package com.shortvideoscripagent.xhsagentyunying.dto.metrics;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.Map;

/**
 * S3-5 人工修订指标快照请求。
 * <p>
 * 修订<b>不覆盖</b>原快照，而是生成一条新快照，{@code revised_from_id} 指向原快照。
 */
@Data
public class MetricSnapshotReviseRequest {

    /** 修订后的指标值（核心 10 键缺失补 0） */
    @NotNull(message = "metrics_required")
    private Map<String, Object> metrics;

    /** 平台扩展指标（可选） */
    private Map<String, Object> metricExtra;

    /** 可信度：HIGH / MEDIUM / LOW / UNVERIFIED */
    @Size(max = 16)
    private String confidence;

    /** 修订原因（可追溯） */
    @Size(max = 512)
    private String reason;
}
