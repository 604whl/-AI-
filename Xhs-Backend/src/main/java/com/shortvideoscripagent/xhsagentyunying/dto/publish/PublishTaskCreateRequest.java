package com.shortvideoscripagent.xhsagentyunying.dto.publish;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.OffsetDateTime;

/**
 * 创建发布任务请求。
 * <p>
 * <b>必须</b>配合 {@code Idempotency-Key} 请求头使用；相同幂等键重复提交只产生一个任务。
 * <p>
 * 注意：请求体不接受 {@code level} / {@code status} 等服务端判定字段。
 */
@Data
public class PublishTaskCreateRequest {

    /** 内容资产 ID */
    @NotNull(message = "asset_id_required")
    private String assetId;

    /** 平台代码（大写枚举名） */
    @NotBlank(message = "platform_code_required")
    @Pattern(regexp = "^[A-Z][A-Z0-9_]{1,31}$", message = "invalid_platform_code")
    private String platformCode;

    /** 平台账号 ID */
    @Size(max = 64, message = "account_id_too_long")
    private String accountId;

    /** 计划发布时间（可空：为空表示立即执行） */
    private OffsetDateTime scheduledAt;

    /** 关联的排期 ID（可空） */
    @Size(max = 64, message = "schedule_id_too_long")
    private String scheduleId;
}
