package com.shortvideoscripagent.xhsagentyunying.dto.analysis;

import lombok.Builder;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.Map;

@Data
@Builder
public class AnalysisDetailResponse {

    private String id;
    private String status;
    private String scenario;
    private String persona;
    private String title;
    private String body;
    private String coverImageUrl;
    private Map<String, Object> publishedMetrics;
    private Map<String, Object> competitorContext;
    private String promptVersion;
    private String model;
    private Integer processingMs;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    private Map<String, Object> failure;
    private Map<String, Object> report;
    private Map<String, Object> coverAnalysis;
}
