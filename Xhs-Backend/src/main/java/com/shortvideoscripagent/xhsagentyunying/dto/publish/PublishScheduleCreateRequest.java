package com.shortvideoscripagent.xhsagentyunying.dto.publish;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.OffsetDateTime;

/**
 * 创建排期请求。
 * <p>
 * 注意：请求体不接受 {@code status} / {@code level} 等服务端判定字段。
 */
@Data
public class PublishScheduleCreateRequest {

    /** 内容资产 ID（必须是已审核通过的资产） */
    @NotNull(message = "asset_id_required")
    private String assetId;

    /** 平台代码（大写枚举名，如 XIAOHONGSHU / DOUYIN） */
    @NotBlank(message = "platform_code_required")
    @Pattern(regexp = "^[A-Z][A-Z0-9_]{1,31}$", message = "invalid_platform_code")
    private String platformCode;

    /** 平台账号 ID */
    @Size(max = 64, message = "account_id_too_long")
    private String accountId;

    /** 排期时刻（ISO-8601 带偏移量，服务端统一转 UTC 落库） */
    @NotNull(message = "scheduled_at_required")
    private OffsetDateTime scheduledAt;

    /** IANA 时区串（如 Asia/Shanghai），默认 UTC */
    @Size(max = 64, message = "timezone_too_long")
    @Pattern(regexp = "^[A-Za-z0-9_+\\-/]{1,64}$", message = "invalid_timezone")
    private String timezone;
}
