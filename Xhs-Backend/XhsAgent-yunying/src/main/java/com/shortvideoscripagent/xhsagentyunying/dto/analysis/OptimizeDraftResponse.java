package com.shortvideoscripagent.xhsagentyunying.dto.analysis;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
@Builder
public class OptimizeDraftResponse {

    private String analysisId;

    private String optimizedTitle;

    private String optimizedBody;

    private List<String> structureOutline;

    private String cta;

    private List<Map<String, Object>> complianceWarnings;

    private String promptVersion;

    private int wordCount;
}
