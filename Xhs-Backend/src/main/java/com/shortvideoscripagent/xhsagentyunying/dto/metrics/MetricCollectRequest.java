package com.shortvideoscripagent.xhsagentyunying.dto.metrics;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * S3-5 采集触发请求。
 */
@Data
public class MetricCollectRequest {

    /** 平台代码（必填） */
    @NotBlank(message = "platform_code_required")
    @Size(max = 32)
    private String platformCode;

    /** 平台账号 ID（可空，空表示平台级采集） */
    @Size(max = 64)
    private String accountId;
}
