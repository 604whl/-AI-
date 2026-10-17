package com.shortvideoscripagent.xhsagentyunying.dto.retention;

import lombok.Builder;
import lombok.Data;

import java.time.OffsetDateTime;

/**
 * 工作区销毁状态响应 DTO（P0-9）。
 */
@Data
@Builder
public class WorkspaceDestructionResponse {

    private String id;

    private String workspaceId;

    /** 当前状态：pending / confirmed / cancelled / executed */
    private String status;

    /** 计划执行时间 */
    private OffsetDateTime executeAt;

    /** 剩余天数 */
    private Long daysRemaining;

    /** 确认时间 */
    private OffsetDateTime confirmedAt;

    /** 执行时间 */
    private OffsetDateTime executedAt;

    /** 销毁申请时间 */
    private OffsetDateTime createdAt;
}
