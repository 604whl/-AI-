package com.shortvideoscripagent.xhsagentyunying.dto.metrics;

import lombok.Builder;
import lombok.Data;

import java.time.OffsetDateTime;

/**
 * S3-5 同步状态响应（同步延迟 / 失败率 / 最后成功时间可见）。
 */
@Data
@Builder
public class MetricSyncStatusResponse {

    private String platformCode;
    private String accountId;

    /** HEALTHY / DEGRADED / UNKNOWN */
    private String status;

    /** 最后一次采集时间 */
    private OffsetDateTime lastCollectedAt;

    /** 最后一次成功时间 */
    private OffsetDateTime lastSuccessAt;

    /** 连续失败次数（>= 3 标 DEGRADED） */
    private Integer consecutiveFailures;

    /** 最近一次失败原因 */
    private String lastError;

    /** 同步延迟秒数 = now - lastCollectedAt；从未采集过为 null */
    private Long delaySeconds;
}
