package com.shortvideoscripagent.xhsagentyunying.dto.billing.refund;

import lombok.Data;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * S4-4 退款 DTO。
 */
public final class RefundDtos {

    private RefundDtos() {
    }

    /**
     * 申请退款请求。
     */
    @Data
    public static class CreateRefundRequest {
        /** 订单号（app.subscription_order.order_no） */
        private String orderNo;
        /** 退款金额；为空表示全额退款 */
        private BigDecimal amount;
        /** 退款原因（必填，进审计） */
        private String reason;
    }

    /**
     * 审核请求（通过 / 驳回）。
     */
    @Data
    public static class ReviewRefundRequest {
        /** 审核备注；驳回时必填 */
        private String note;
    }

    /**
     * 退款单视图。
     */
    @Data
    public static class RefundView {
        private String id;
        private String refundNo;
        private String orderId;
        private String orderNo;
        private String workspaceId;
        private BigDecimal amount;
        private String currency;
        private String reason;
        /** pending / approved / rejected / refunded / failed */
        private String status;
        private Long requestedBy;
        private Long reviewedBy;
        private OffsetDateTime reviewedAt;
        private String reviewNote;
        private String provider;
        private String providerRefundId;
        private OffsetDateTime refundedAt;
        private String failureReason;
        private OffsetDateTime createdAt;
    }

    /**
     * 退款结果（审核后同步返回）。
     */
    @Data
    public static class RefundResult {
        private String refundNo;
        private String orderNo;
        private String status;
        private BigDecimal amount;
        private String providerRefundId;
        /** 是否已受理（渠道已接受退款请求） */
        private boolean accepted;
        /** 是否已完成权益冲正 */
        private boolean entitlementReversed;
        /** 关联发票是否已红冲 */
        private boolean invoiceRedFlushed;
    }

    /**
     * 额度 / 余额冲正结果。
     */
    @Data
    public static class ReversalResult {
        private UUID refundId;
        private String refundNo;
        private String orderNo;
        private String workspaceId;
        private BigDecimal amount;
        /** balance（余额回补）/ quota（额度冲正）/ subscription（订阅取消+降级） */
        private String reversalType;
        private boolean applied;
        private String note;
    }
}
