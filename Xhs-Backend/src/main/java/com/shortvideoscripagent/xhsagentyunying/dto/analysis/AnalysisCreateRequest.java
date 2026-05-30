package com.shortvideoscripagent.xhsagentyunying.dto.analysis;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AnalysisCreateRequest {

    @NotBlank
    private String scenario;

    private String persona = "agency";

    @Size(max = 100)
    private String title;

    @Size(max = 10000)
    private String body;

    private String coverImageUrl;

    @Valid
    private PublishedMetricsDto publishedMetrics;

    @Valid
    private CompetitorContextDto competitorContext;
}
