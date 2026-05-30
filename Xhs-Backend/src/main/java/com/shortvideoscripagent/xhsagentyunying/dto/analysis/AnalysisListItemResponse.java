package com.shortvideoscripagent.xhsagentyunying.dto.analysis;

import lombok.Builder;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.Map;

@Data
@Builder
public class AnalysisListItemResponse {

    private String id;
    private String status;
    private String scenario;
    private String persona;
    private String title;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    private Map<String, Object> report;
}
