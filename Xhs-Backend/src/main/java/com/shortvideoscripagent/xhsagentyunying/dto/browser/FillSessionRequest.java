package com.shortvideoscripagent.xhsagentyunying.dto.browser;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class FillSessionRequest {

    @NotBlank
    private String sessionId;

    @NotBlank
    private String assetId;
}