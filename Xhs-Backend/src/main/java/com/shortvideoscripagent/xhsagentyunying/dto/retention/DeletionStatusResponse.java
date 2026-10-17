package com.shortvideoscripagent.xhsagentyunying.dto.retention;

import lombok.Builder;
import lombok.Data;

import java.time.OffsetDateTime;

/**
 * 账号注销状态响应 DTO（P0-9）。
 */
@Data
@Builder
public class DeletionStatusResponse {

    /** 当前状态：active / pending_deletion / cancelled */
    private String deletionStatus;

    /** 计划删除时间（pending_deletion 时返回） */
    private OffsetDateTime scheduledDeletionAt;

    /** 剩余天数（pending_deletion 时返回） */
    private Long daysRemaining;

    /** 确认时间 */
    private OffsetDateTime deletionConfirmedAt;
}
