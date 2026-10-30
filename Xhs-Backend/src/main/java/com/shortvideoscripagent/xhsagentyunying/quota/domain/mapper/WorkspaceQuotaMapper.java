package com.shortvideoscripagent.xhsagentyunying.quota.domain.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.shortvideoscripagent.xhsagentyunying.quota.domain.entity.WorkspaceQuota;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.time.OffsetDateTime;

/**
 * WorkspaceQuota Mapper。
 */
@Mapper
public interface WorkspaceQuotaMapper extends BaseMapper<WorkspaceQuota> {

    /**
     * 原子扣减：仅当未超配额时 used + 1。
     * 返回受影响的行数：1 = 成功，0 = 超额或 workspace 不存在。
     */
    @Update("""
            UPDATE app.workspace_quota
            SET used = used + 1,
                updated_at = NOW()
            WHERE workspace_id = #{workspaceId}
              AND (used < daily_quota OR reset_at < NOW())
            """)
    int atomicConsume(@Param("workspaceId") String workspaceId);

    /**
     * 重置已用次数（reset_at 过期时调用）。
     */
    @Update("""
            UPDATE app.workspace_quota
            SET used = 0,
                reset_at = NOW() + INTERVAL '1 day',
                updated_at = NOW()
            WHERE workspace_id = #{workspaceId}
              AND reset_at < NOW()
            """)
    int resetIfExpired(@Param("workspaceId") String workspaceId);

    /**
     * 冲正：used - 1。
     */
    @Update("""
            UPDATE app.workspace_quota
            SET used = GREATEST(used - 1, 0),
                updated_at = NOW()
            WHERE workspace_id = #{workspaceId}
            """)
    int refund(@Param("workspaceId") String workspaceId);

    /**
     * S4-1：写入订阅绑定的周期额度。
     * <p>
     * <b>不改</b> daily_quota / used / reset_at 语义，只更新订阅相关列。
     * </p>
     */
    @Update("""
            UPDATE app.workspace_quota
            SET plan_id        = CAST(#{planId} AS VARCHAR),
                period_quota   = #{periodQuota},
                period_start   = #{periodStart},
                period_end     = #{periodEnd},
                overage_policy = #{overagePolicy},
                updated_at     = NOW()
            WHERE workspace_id = #{workspaceId}
            """)
    int applySubscriptionQuota(@Param("workspaceId") String workspaceId,
                               @Param("planId") String planId,
                               @Param("periodQuota") Integer periodQuota,
                               @Param("periodStart") OffsetDateTime periodStart,
                               @Param("periodEnd") OffsetDateTime periodEnd,
                               @Param("overagePolicy") String overagePolicy);

    /**
     * S4-1：降级到 FREE 套餐时重置周期额度。
     * <p>
     * 用 plan_id 作为乐观锁条件：只有当前仍绑定同一套餐才写，
     * 保证重复降级不会重复写额度。
     * </p>
     */
    @Update("""
            UPDATE app.workspace_quota
            SET plan_id        = CAST(#{newPlanId} AS VARCHAR),
                period_quota   = #{periodQuota},
                period_start   = #{periodStart},
                period_end     = #{periodEnd},
                overage_policy = #{overagePolicy},
                updated_at     = NOW()
            WHERE workspace_id = #{workspaceId}
              AND plan_id IS NOT DISTINCT FROM CAST(#{expectedPlanId} AS VARCHAR)
            """)
    int applyDowngradeQuota(@Param("workspaceId") String workspaceId,
                            @Param("expectedPlanId") String expectedPlanId,
                            @Param("newPlanId") String newPlanId,
                            @Param("periodQuota") Integer periodQuota,
                            @Param("periodStart") OffsetDateTime periodStart,
                            @Param("periodEnd") OffsetDateTime periodEnd,
                            @Param("overagePolicy") String overagePolicy);
}
