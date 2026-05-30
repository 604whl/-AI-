package com.shortvideoscripagent.xhsagentyunying.dto.analysis;

import lombok.Data;

@Data
public class OptimizeDraftRequest {

    private boolean includeTitle = true;

    private String tone = "default";

    private int maxLength = 1200;
}
