package com.shortvideoscripagent.xhsagentyunying.dto.analysis;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
@Builder
public class BodyGenerateResponse {

    private String analysisId;

    private String goal;

    private String body;

    private List<StructureSection> structureOutline;

    private String cta;

    private List<Map<String, Object>> complianceWarnings;

    private String promptVersion;

    private int wordCount;

    @Data
    @AllArgsConstructor
    public static class StructureSection {
        private String section;
        private String summary;
    }
}
