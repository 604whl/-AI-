package com.shortvideoscripagent.xhsagentyunying.billing.subscription.domain.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.shortvideoscripagent.xhsagentyunying.billing.subscription.domain.entity.Subscription;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * S4-1 订阅 Mapper。
 */
@Mapper
public interface SubscriptionMapper extends BaseMapper<Subscription> {

    /**
     * 取工作区当前生效中的订阅（trialing / active / past_due）。
     * 部分唯一索引保证最多一条。
     */
    @Select("""
            SELECT * FROM app.subscription
            WHERE workspace_id = #{workspaceId}
              AND status IN ('trialing', 'active', 'past_due')
            LIMIT 1
            """)
    Subscription selectActiveByWorkspace(@Param("workspaceId") String workspaceId);

    /**
     * 取工作区最新一条订阅（无论状态，用于读历史与降级后回读）。
     */
    @Select("""
            SELECT * FROM app.subscription
            WHERE workspace_id = #{workspaceId}
            ORDER BY created_at DESC
            LIMIT 1
            """)
    Subscription selectLatestByWorkspace(@Param("workspaceId") String workspaceId);

    /**
     * 按（workspace_id, idempotency_key）取订阅，命中即视为幂等重放。
     */
    @Select("""
            SELECT * FROM app.subscription
            WHERE workspace_id = #{workspaceId}
              AND idempotency_key = CAST(#{idempotencyKey} AS VARCHAR)
            LIMIT 1
            """)
    Subscription selectByIdempotencyKey(@Param("workspaceId") String workspaceId,
                                        @Param("idempotencyKey") String idempotencyKey);

    /**
     * 查询工作区是否已经用过试用（trial_end 非空即视为已用过）。
     * <p>
     * 同一 workspace 只能试用一次。
     * </p>
     */
    @Select("""
            SELECT * FROM app.subscription
            WHERE workspace_id = #{workspaceId}
              AND trial_end IS NOT NULL
            ORDER BY trial_end DESC
            LIMIT 1
            """)
    Subscription selectTrialUsedByWorkspace(@Param("workspaceId") String workspaceId);

    /**
     * 扫描到期订阅（trialing / active 且 current_period_end 已过）。
     */
    @Select("""
            SELECT * FROM app.subscription
            WHERE status IN ('trialing', 'active')
              AND current_period_end IS NOT NULL
              AND current_period_end < #{now}
            ORDER BY current_period_end ASC
            LIMIT #{limit}
            """)
    List<Subscription> selectExpiredCandidates(@Param("now") OffsetDateTime now,
                                                @Param("limit") int limit);

    /**
     * 扫描宽限期已满的 past_due 订阅。
     * <p>
     * 宽限期截止 = {@code current_period_end + grace_period_days}，用 SQL 表达式内联计算，
     * 避免应用侧逐条判断导致的漏扫。
     * </p>
     */
    @Select("""
            SELECT * FROM app.subscription
            WHERE status = 'past_due'
              AND current_period_end IS NOT NULL
              AND current_period_end + (grace_period_days || ' days')::interval < #{now}
            ORDER BY current_period_end ASC
            LIMIT #{limit}
            """)
    List<Subscription> selectGraceExpiredCandidates(@Param("now") OffsetDateTime now,
                                                     @Param("limit") int limit);

    /**
     * 原子置为 expired（宽限期满）。仅当当前为 trialing / active / past_due。
     * <p>
     * 三种来源状态都要覆盖：正常路径是 active → past_due → expired；
     * 但如果调度器漏扫（服务停机 / Redis 不可用），订阅可能停留在 active 状态直接跨过宽限期，
     * 此时必须仍能被置为 expired，否则会永久卡在 active。
     * </p>
     */
    @Update("""
            UPDATE app.subscription
            SET status = 'expired',
                auto_renew = FALSE,
                canceled_at = NOW(),
                updated_at = NOW()
            WHERE id = #{id}
              AND status IN ('trialing', 'active', 'past_due')
            """)
    int compareAndSetExpired(@Param("id") UUID id);

    /**
     * 原子置为 canceled（周期末取消生效）。仅当当前为 trialing / active / past_due。
     * <p>
     * 与 {@link #compareAndSetExpired} 一样覆盖三种来源状态，理由相同：
     * 调度器漏扫时订阅可能跨过宽限期仍停留在 active / past_due。
     * </p>
     */
    @Update("""
            UPDATE app.subscription
            SET status = 'canceled',
                auto_renew = FALSE,
                cancel_at_period_end = FALSE,
                canceled_at = NOW(),
                updated_at = NOW()
            WHERE id = #{id}
              AND status IN ('trialing', 'active', 'past_due')
            """)
    int compareAndSetCanceled(@Param("id") UUID id);

    /**
     * 原子状态流转：仅当当前状态符合预期时才更新。
     * <p>
     * 返回受影响行数：1 = 流转成功（本次由本消费者处理）；0 = 已被其他消费者处理（幂等跳过）。
     * </p>
     */
    @Update("""
            UPDATE app.subscription
            SET status = #{toStatus},
                updated_at = NOW()
            WHERE id = #{id}
              AND status = #{fromStatus}
            """)
    int compareAndSetStatus(@Param("id") UUID id,
                            @Param("fromStatus") String fromStatus,
                            @Param("toStatus") String toStatus);

    /**
     * 原子置为 past_due（进入宽限期）。仅当当前为 trialing / active。
     */
    @Update("""
            UPDATE app.subscription
            SET status = 'past_due',
                updated_at = NOW()
            WHERE id = #{id}
              AND status IN ('trialing', 'active')
            """)
    int compareAndSetPastDue(@Param("id") UUID id);

    /**
     * 绑定写操作幂等键（cancel / change-plan 这类「修改已有订阅」的操作）。
     * <p>
     * {@code app.subscription.idempotency_key} 上有唯一索引
     * {@code (workspace_id, idempotency_key)}，重复绑定同一键会抛
     * {@code DuplicateKeyException}，由 Service 捕获后判定为「幂等重放」。
     * </p>
     * <p>
     * 仅在当前行还没绑定任何幂等键时才写入（{@code idempotency_key IS NULL}），
     * 避免覆盖已有键导致后续重放判定错乱。
     * </p>
     */
    @Update("""
            UPDATE app.subscription
            SET idempotency_key = CAST(#{idempotencyKey} AS VARCHAR),
                updated_at = NOW()
            WHERE id = #{id}
              AND idempotency_key IS NULL
            """)
    int bindIdempotencyKey(@Param("id") UUID id,
                           @Param("idempotencyKey") String idempotencyKey);
}
