package com.shortvideoscripagent.xhsagentyunying.dto.analysis;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AnalysisCreateRequest {

    @NotBlank
    @Pattern(regexp = "draft|published|competitor", message = "invalid_scenario")
    private String scenario;

    @Pattern(regexp = "agency|mentor|senior", message = "invalid_persona")
    private String persona = "agency";

    @Size(max = 20)
    private String title;

    @Size(max = 10000)
    private String body;

    @Size(max = 512)
    private String coverImageUrl;

    @Valid
    private PublishedMetricsDto publishedMetrics;

    @Valid
    private CompetitorContextDto competitorContext;
}
