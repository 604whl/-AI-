package com.shortvideoscripagent.xhsagentyunying.dto.title;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class TitleGenerateRequest {

    private String analysisId;

    @NotBlank
    private String goal;

    private Integer count = 8;

    @Size(max = 100)
    private String title;

    @Size(max = 10000)
    private String body;

    private String persona;
}
