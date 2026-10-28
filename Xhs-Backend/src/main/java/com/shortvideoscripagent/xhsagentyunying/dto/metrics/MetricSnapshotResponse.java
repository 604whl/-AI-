package com.shortvideoscripagent.xhsagentyunying.dto.metrics;

import lombok.Builder;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * S3-5 指标快照响应。
 */
@Data
@Builder
public class MetricSnapshotResponse {

    private UUID id;
    private UUID assetId;
    private String platformCode;
    private String accountId;
    private String externalContentId;
    private String contentType;
    private OffsetDateTime publishedAt;

    /** 统一口径指标（固定 10 键） */
    private Map<String, Long> metrics;

    /** 平台扩展指标 */
    private Map<String, Object> metricExtra;

    private String metricSource;
    private String confidence;
    private OffsetDateTime collectedAt;
    private Boolean isManual;
    private UUID revisedFromId;
    private Long revisedBy;
    private OffsetDateTime createdAt;

    /** 修订链（仅详情接口返回，按 created_at 升序） */
    private java.util.List<MetricSnapshotResponse> revisions;

    /** 修订原因（仅详情接口返回，说明本条快照为何被修订） */
    private String reviseReason;
}
