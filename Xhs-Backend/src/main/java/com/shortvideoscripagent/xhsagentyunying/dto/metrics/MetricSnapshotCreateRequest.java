package com.shortvideoscripagent.xhsagentyunying.dto.metrics;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.Map;

/**
 * S3-5 手动录入指标快照请求。
 */
@Data
public class MetricSnapshotCreateRequest {

    /** 关联内容资产（可空） */
    @Size(max = 64)
    private String assetId;

    /** 平台代码（必填） */
    @NotBlank(message = "platform_code_required")
    @Size(max = 32)
    private String platformCode;

    /** 平台账号 ID（可空） */
    @Size(max = 64)
    private String accountId;

    /** 平台侧内容 ID（可空） */
    @Size(max = 128)
    private String externalContentId;

    /** 内容类型（可空） */
    @Size(max = 32)
    private String contentType;

    /** 内容发布时间（可空） */
    private OffsetDateTime publishedAt;

    /**
     * 指标值。核心 10 键缺失自动补 0；核心键之外的字段被剔除到 metric_extra。
     */
    private Map<String, Object> metrics;

    /** 平台扩展指标（可选） */
    private Map<String, Object> metricExtra;

    /** 数据来源：MANUAL_IMPORT / CSV_IMPORT / BROWSER_ASSIST / OFFICIAL_API / AUTHORIZED_CONNECTOR */
    @NotBlank(message = "metric_source_required")
    @Size(max = 32)
    private String metricSource;

    /** 可信度：HIGH / MEDIUM / LOW / UNVERIFIED，默认 LOW */
    @Size(max = 16)
    private String confidence;

    /** 采集时间（平台实际统计时间），默认 now() */
    private OffsetDateTime collectedAt;
}
