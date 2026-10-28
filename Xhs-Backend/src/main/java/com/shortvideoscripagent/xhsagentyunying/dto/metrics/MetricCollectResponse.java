package com.shortvideoscripagent.xhsagentyunying.dto.metrics;

import lombok.Builder;
import lombok.Data;

import java.util.UUID;

/**
 * S3-5 采集触发响应。
 */
@Data
@Builder
public class MetricCollectResponse {

    /** 采集任务 ID */
    private UUID taskId;

    /** queued / running / succeeded / failed */
    private String status;

    private String platformCode;
    private String accountId;
}
