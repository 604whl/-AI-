package com.shortvideoscripagent.xhsagentyunying.dto.analysis;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CompetitorContextDto {

    @Size(max = 64)
    private String accountName;

    @Size(max = 500)
    private String noteUrl;

    @Size(max = 200)
    private String learningFocus;
}
