package com.shortvideoscripagent.xhsagentyunying.quota.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.OffsetDateTime;

/**
 * Workspace 级每日配额实体。
 * <p>
 * 使用单条 UPDATE 原子扣减 used，reset_at 过期后重置。
 * </p>
 */
@Data
@TableName("workspace_quota")
public class WorkspaceQuota {

    /**
     * Workspace ID，主键。
     */
    @TableId(type = IdType.INPUT)
    private String workspaceId;

    /**
     * 每日配额上限，默认 100。
     */
    private Integer dailyQuota;

    /**
     * 当日已使用次数。
     */
    private Integer used;

    /**
     * 重置时间点（UTC），默认次日零点。
     */
    private OffsetDateTime resetAt;

    /**
     * 创建时间。
     */
    private OffsetDateTime createdAt;

    /**
     * 最后更新时间。
     */
    private OffsetDateTime updatedAt;
}
