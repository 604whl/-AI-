package com.shortvideoscripagent.xhsagentyunying.dto.retention;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

/**
 * 保留策略请求 DTO（P0-9）。
 */
@Data
public class RetentionPolicyRequest {

    @Min(value = 30, message = "asset_retention_days must be at least 30")
    @Max(value = 3650, message = "asset_retention_days must be at most 3650")
    private Integer assetRetentionDays;

    @Min(value = 7, message = "log_retention_days must be at least 7")
    @Max(value = 3650, message = "log_retention_days must be at most 3650")
    private Integer logRetentionDays;

    @Min(value = 7, message = "media_retention_days must be at least 7")
    @Max(value = 3650, message = "media_retention_days must be at most 3650")
    private Integer mediaRetentionDays;

    @Min(value = 7, message = "chat_session_retention_days must be at least 7")
    @Max(value = 3650, message = "chat_session_retention_days must be at most 3650")
    private Integer chatSessionRetentionDays;
}
