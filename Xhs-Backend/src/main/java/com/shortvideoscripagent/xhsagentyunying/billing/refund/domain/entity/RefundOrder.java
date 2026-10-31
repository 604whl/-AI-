package com.shortvideoscripagent.xhsagentyunying.billing.refund.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * S4-4 退款单实体（{@code app.refund_order}，V67）。
 * <p>
 * 状态机：{@code pending → approved → refunded}；
 * {@code pending → rejected}（审核驳回）；{@code approved → failed}（渠道退款失败）。
 * </p>
 * <p>
 * <b>幂等锚点</b>：{@code providerRefundId}（唯一索引）——
 * 同一渠道退款单只生效一次，回调重复投递不会重复退钱 / 重复冲正。
 * </p>
 */
@Data
@TableName(value = "refund_order", autoResultMap = true)
public class RefundOrder {

    @TableId(type = IdType.ASSIGN_UUID)
    private UUID id;

    /** 退款单号，全局唯一：RF + 时间戳 + 随机 */
    private String refundNo;

    /** 关联订单 id（app.subscription_order.id） */
    private UUID orderId;

    /** 冗余订单号，便于运营检索 */
    private String orderNo;

    private String workspaceId;

    /** 退款金额 NUMERIC(18,4) */
    private BigDecimal amount;

    private String currency;

    /** 用户申请时填的原因（进审计） */
    private String reason;

    /** pending / approved / rejected / refunded / failed */
    private String status;

    /** 申请人 user id */
    private Long requestedBy;

    /** 审核人（平台管理员 user id） */
    private Long reviewedBy;

    private OffsetDateTime reviewedAt;

    private String reviewNote;

    /** 渠道侧退款单号，唯一 */
    private String providerRefundId;

    /** 退款渠道（与订单一致） */
    private String provider;

    /** 渠道退款完成时间 */
    private OffsetDateTime refundedAt;

    /** 失败原因（不含凭据） */
    private String failureReason;

    private String idempotencyKey;

    private OffsetDateTime createdAt;

    private OffsetDateTime updatedAt;
}
