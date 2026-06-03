package com.shortvideoscripagent.xhsagentyunying.service.analysis;

import lombok.Builder;

@Builder
public record AnalysisProgressEvent(
        String taskId,
        String status,
        String phase,
        String message,
        Integer processingMs,
        Integer failureCode,
        String failureReason
) {
}
