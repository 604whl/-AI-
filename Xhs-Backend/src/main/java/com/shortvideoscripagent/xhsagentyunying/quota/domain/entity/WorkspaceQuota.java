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
 * <p>
 * <b>阶段 4 S4-1 变更（V62）</b>：只<b>加列不改语义</b>。
 * {@code dailyQuota / used / resetAt} 行为完全不变；新增的
 * {@code planId / periodQuota / periodStart / periodEnd / overagePolicy}
 * 是订阅绑定的<b>周期额度</b>，与日额度并存，由订阅服务写入。
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
     * S4-1：订阅绑定的套餐 ID（app.subscription_plan.id）。
     */
    private String planId;

    /**
     * S4-1：周期额度上限（订阅套餐包含额度合计）。
     */
    private Integer periodQuota;

    /**
     * S4-1：周期额度起始时间。
     */
    private OffsetDateTime periodStart;

    /**
     * S4-1：周期额度结束时间。
     */
    private OffsetDateTime periodEnd;

    /**
     * S4-1：超额策略 reject / charge_oversage / downgrade，默认 reject。
     */
    private String overagePolicy;

    /**
     * 创建时间。
     */
    private OffsetDateTime createdAt;

    /**
     * 最后更新时间。
     */
    private OffsetDateTime updatedAt;
}
