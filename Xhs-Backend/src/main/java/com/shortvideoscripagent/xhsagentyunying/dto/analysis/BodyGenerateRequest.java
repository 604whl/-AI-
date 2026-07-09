package com.shortvideoscripagent.xhsagentyunying.dto.analysis;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class BodyGenerateRequest {

    @NotBlank
    @Pattern(regexp = "high_ctr|high_collect|high_conversion|anxiety|offer|info_gap", message = "invalid_goal")
    private String goal = "high_conversion";

    @Pattern(regexp = "default|more_anxiety|more_professional|more_friendly", message = "invalid_tone")
    private String tone = "default";

    @Min(500)
    @Max(1500)
    private Integer maxLength = 900;

    @Size(max = 5)
    private List<@Size(max = 20) String> keywords;
}
