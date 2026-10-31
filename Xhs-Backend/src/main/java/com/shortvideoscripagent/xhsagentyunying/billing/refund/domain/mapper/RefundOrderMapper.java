package com.shortvideoscripagent.xhsagentyunying.billing.refund.domain.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.shortvideoscripagent.xhsagentyunying.billing.refund.domain.entity.RefundOrder;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.UUID;

/**
 * S4-4 退款单 Mapper。
 */
@Mapper
public interface RefundOrderMapper extends BaseMapper<RefundOrder> {

    /**
     * 按工作区 + 幂等键查退款单。
     */
    @Select("""
            SELECT * FROM app.refund_order
            WHERE workspace_id = #{workspaceId}
              AND idempotency_key = #{idempotencyKey}
            LIMIT 1
            """)
    RefundOrder selectByIdempotencyKey(@Param("workspaceId") String workspaceId,
                                        @Param("idempotencyKey") String idempotencyKey);

    /**
     * 按渠道退款单号查退款单（回调幂等锚点）。
     */
    @Select("""
            SELECT * FROM app.refund_order
            WHERE provider_refund_id = #{providerRefundId}
            LIMIT 1
            """)
    RefundOrder selectByProviderRefundId(@Param("providerRefundId") String providerRefundId);

    /**
     * 审核通过：{@code pending → approved}（条件更新，保证并发只成功一次）。
     */
    @Update("""
            UPDATE app.refund_order
            SET status = 'approved',
                reviewed_by = #{reviewedBy},
                reviewed_at = #{reviewedAt},
                review_note = #{reviewNote},
                updated_at = NOW()
            WHERE id = #{id} AND status = 'pending'
            """)
    int compareAndSetApproved(@Param("id") UUID id,
                              @Param("reviewedBy") Long reviewedBy,
                              @Param("reviewedAt") java.time.OffsetDateTime reviewedAt,
                              @Param("reviewNote") String reviewNote);

    /**
     * 审核驳回：{@code pending → rejected}。
     */
    @Update("""
            UPDATE app.refund_order
            SET status = 'rejected',
                reviewed_by = #{reviewedBy},
                reviewed_at = #{reviewedAt},
                review_note = #{reviewNote},
                updated_at = NOW()
            WHERE id = #{id} AND status = 'pending'
            """)
    int compareAndSetRejected(@Param("id") UUID id,
                              @Param("reviewedBy") Long reviewedBy,
                              @Param("reviewedAt") java.time.OffsetDateTime reviewedAt,
                              @Param("reviewNote") String reviewNote);

    /**
     * 渠道退款成功：{@code approved → refunded}，并回写渠道退款单号。
     * <p>
     * 条件更新保证「同一退款单只成功冲正一次」。
     * </p>
     */
    @Update("""
            UPDATE app.refund_order
            SET status = 'refunded',
                provider_refund_id = #{providerRefundId},
                refunded_at = #{refundedAt},
                failure_reason = NULL,
                updated_at = NOW()
            WHERE id = #{id} AND status = 'approved'
            """)
    int compareAndSetRefunded(@Param("id") UUID id,
                              @Param("providerRefundId") String providerRefundId,
                              @Param("refundedAt") java.time.OffsetDateTime refundedAt);

    /**
     * 渠道退款失败：{@code approved → failed}。
     */
    @Update("""
            UPDATE app.refund_order
            SET status = 'failed',
                failure_reason = #{reason},
                updated_at = NOW()
            WHERE id = #{id} AND status = 'approved'
            """)
    int compareAndSetFailed(@Param("id") UUID id, @Param("reason") String reason);

    /**
     * 统计订单已退款金额（不含 failed / rejected），用于「累计退款额不超过订单实付金额」校验。
     */
    @Select("""
            SELECT COALESCE(SUM(amount), 0) FROM app.refund_order
            WHERE order_id = #{orderId} AND status = 'refunded'
            """)
    java.math.BigDecimal sumRefundedAmount(@Param("orderId") UUID orderId);

    /**
     * 统计订单「已通过审核但未完成」的退款金额（含 approved）。
     * <p>
     * 用于「在途退款额」校验：审核通过但渠道回调未到的窗口内，
     * 不允许再对同一订单发起超额退款。
     * </p>
     */
    @Select("""
            SELECT COALESCE(SUM(amount), 0) FROM app.refund_order
            WHERE order_id = #{orderId} AND status IN ('approved', 'refunded')
            """)
    java.math.BigDecimal sumCommittedAmount(@Param("orderId") UUID orderId);
}
