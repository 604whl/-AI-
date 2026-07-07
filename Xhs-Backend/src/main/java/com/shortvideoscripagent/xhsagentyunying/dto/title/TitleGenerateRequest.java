package com.shortvideoscripagent.xhsagentyunying.dto.title;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class TitleGenerateRequest {

    private String analysisId;

    @NotBlank
    @Pattern(regexp = "high_ctr|high_collect|high_conversion|anxiety|offer|info_gap", message = "invalid_goal")
    private String goal;

    @Min(1)
    @Max(10)
    private Integer count = 8;

    @Size(max = 100)
    private String title;

    @Size(max = 10000)
    private String body;

    @Pattern(regexp = "agency|mentor|senior", message = "invalid_persona")
    private String persona;
}
