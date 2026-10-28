package com.shortvideoscripagent.xhsagentyunying.dto.metrics;

import lombok.Builder;
import lombok.Data;

import java.time.OffsetDateTime;

/**
 * S3-5 指标趋势桶。
 */
@Data
@Builder
public class MetricTrendBucket {

    /** 时间桶（day / week 粒度下的起止日期，格式 yyyy-MM-dd） */
    private String bucket;

    private Long impressions;
    private Long likes;
    private Long collects;
    private Long comments;
    private Long dmInquiries;
}
