package com.shortvideoscripagent.xhsagentyunying.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 工作区销毁记录（P0-9）。
 * <p>
 * 记录工作区销毁申请、二次确认、执行状态。
 * 执行时由 RetentionSweeper 扫描 pending_destructions 并执行物理删除。
 */
@Data
@TableName("workspace_destruction")
public class WorkspaceDestruction {

    public static final String STATUS_PENDING = "pending";
    public static final String STATUS_CONFIRMED = "confirmed";
    public static final String STATUS_CANCELLED = "cancelled";
    public static final String STATUS_EXECUTED = "executed";

    @TableId(type = IdType.ASSIGN_UUID)
    private UUID id;

    /** 工作区 ID（String 类型，与 Workspace.id 保持一致） */
    private String workspaceId;

    /** 申请人 userId */
    private Long requestedBy;

    /** 确认短语（必须为 "DELETE"） */
    private String confirmPhrase;

    /** 状态：pending / confirmed / cancelled / executed */
    private String status;

    /** 计划执行时间（申请后 7 天） */
    private OffsetDateTime executeAt;

    /** 二次确认时间 */
    private OffsetDateTime confirmedAt;

    /** 实际执行时间 */
    private OffsetDateTime executedAt;

    private OffsetDateTime createdAt;

    // ── 状态判断 ──────────────────────────────────────────────────────────

    public boolean isPending() {
        return STATUS_PENDING.equals(status);
    }

    public boolean isConfirmed() {
        return STATUS_CONFIRMED.equals(status);
    }

    public boolean isCancelled() {
        return STATUS_CANCELLED.equals(status);
    }

    public boolean isExecuted() {
        return STATUS_EXECUTED.equals(status);
    }

    public boolean canBeConfirmed() {
        return STATUS_PENDING.equals(status);
    }

    public boolean canBeCancelled() {
        return STATUS_PENDING.equals(status) || STATUS_CONFIRMED.equals(status);
    }

    public boolean isReadyToExecute(OffsetDateTime now) {
        return STATUS_CONFIRMED.equals(status)
                && executeAt != null
                && !now.isBefore(executeAt);
    }
}
