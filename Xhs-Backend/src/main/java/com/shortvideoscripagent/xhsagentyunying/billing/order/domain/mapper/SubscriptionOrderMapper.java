package com.shortvideoscripagent.xhsagentyunying.billing.order.domain.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.shortvideoscripagent.xhsagentyunying.billing.order.domain.entity.SubscriptionOrder;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * S4-2 订单 Mapper。
 */
@Mapper
public interface SubscriptionOrderMapper extends BaseMapper<SubscriptionOrder> {

    /**
     * 按订单号取订单。
     */
    @Select("""
            SELECT * FROM app.subscription_order
            WHERE order_no = #{orderNo}
            LIMIT 1
            """)
    SubscriptionOrder selectByOrderNo(@Param("orderNo") String orderNo);

    /**
     * 按（workspace_id, idempotency_key）取订单，命中即视为幂等重放。
     */
    @Select("""
            SELECT * FROM app.subscription_order
            WHERE workspace_id = #{workspaceId}
              AND idempotency_key = CAST(#{idempotencyKey} AS VARCHAR)
            LIMIT 1
            """)
    SubscriptionOrder selectByIdempotencyKey(@Param("workspaceId") String workspaceId,
                                             @Param("idempotencyKey") String idempotencyKey);

    /**
     * 扫描超时未支付的订单（pending → closed）。
     */
    @Select("""
            SELECT * FROM app.subscription_order
            WHERE status = 'pending'
              AND expires_at IS NOT NULL
              AND expires_at < #{now}
            ORDER BY expires_at ASC
            LIMIT #{limit}
            """)
    List<SubscriptionOrder> selectExpiredCandidates(@Param("now") OffsetDateTime now,
                                                     @Param("limit") int limit);

    /**
     * 原子置为 closed（超时）。仅当当前为 pending。
     *
     * @return 1 = 本次关闭成功；0 = 已被其他消费者关闭（幂等跳过）
     */
    @Update("""
            UPDATE app.subscription_order
            SET status = 'closed',
                updated_at = NOW()
            WHERE id = #{id}
              AND status = 'pending'
            """)
    int compareAndSetClosed(@Param("id") UUID id);

    /**
     * 原子置为 paid。仅当当前为 pending。
     * <p>
     * 返回 1 = 本次入账成功；0 = 订单已不是 pending（重复回调 / 已处理，幂等跳过）。
     * </p>
     */
    @Update("""
            UPDATE app.subscription_order
            SET status = 'paid',
                paid_at = #{paidAt},
                provider_order_id = COALESCE(#{providerOrderId}, provider_order_id),
                updated_at = NOW()
            WHERE id = #{id}
              AND status = 'pending'
            """)
    int compareAndSetPaid(@Param("id") UUID id,
                          @Param("paidAt") OffsetDateTime paidAt,
                          @Param("providerOrderId") String providerOrderId);

    /**
     * 扫描已付款但未履约的订单（{@code paid} 状态且超过补偿窗口）。
     * <p>
     * P2-7：履约任务投递失败（Redis 不可用等）时订单会永久停在 {@code paid} ——
     * 用户已付款、订阅未开通，且没有任何补偿扫描能捞回来。这里提供扫描入口。
     * </p>
     */
    @Select("""
            SELECT * FROM app.subscription_order
            WHERE status = 'paid'
              AND paid_at IS NOT NULL
              AND paid_at < #{before}
            ORDER BY paid_at ASC
            LIMIT #{limit}
            """)
    List<SubscriptionOrder> selectUnfulfilledPaidCandidates(@Param("before") OffsetDateTime before,
                                                            @Param("limit") int limit);

    /**
     * 原子置为 fulfilled（履约完成）。仅当当前为 paid。
     */
    @Update("""
            UPDATE app.subscription_order
            SET status = 'fulfilled',
                fulfilled_at = NOW(),
                updated_at = NOW()
            WHERE id = #{id}
              AND status = 'paid'
            """)
    int compareAndSetFulfilled(@Param("id") UUID id);

    /**
     * 原子置为 refunded。仅当当前为 paid / fulfilled。
     */
    @Update("""
            UPDATE app.subscription_order
            SET status = 'refunded',
                updated_at = NOW()
            WHERE id = #{id}
              AND status IN ('paid', 'fulfilled')
            """)
    int compareAndSetRefunded(@Param("id") UUID id);
}
