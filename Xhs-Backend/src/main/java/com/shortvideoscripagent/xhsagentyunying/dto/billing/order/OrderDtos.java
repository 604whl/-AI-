package com.shortvideoscripagent.xhsagentyunying.dto.billing.order;

import lombok.Data;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Map;

/**
 * S4-2 订单相关 DTO。
 */
public final class OrderDtos {

    private OrderDtos() {
    }

    /**
     * 创建订单请求。
     */
    @Data
    public static class CreateOrderRequest {
        /** 套餐编码：PRO / TEAM / USAGE */
        private String planCode;
        /** 数量（订阅默认 1；按量充值表示充值份数） */
        private Integer quantity;
        /** 支付渠道：mock / alipay / wechat */
        private String provider;
        /** 按量充值金额（D6：order_type = usage 时必填） */
        private BigDecimal amount;
        /** subscription（默认）/ usage；留空时按 planCode 是否为空推断 */
        private String orderType;
    }

    /**
     * 订单视图。
     */
    @Data
    public static class OrderView {
        private String id;
        private String orderNo;
        private String workspaceId;
        private String planId;
        private String planCode;
        private Integer planVersion;
        /** subscription / usage */
        private String orderType;
        private Integer quantity;
        private BigDecimal amount;
        private String currency;
        private String provider;
        private String providerOrderId;
        /** pending / paid / fulfilled / refunded / closed */
        private String status;
        private OffsetDateTime paidAt;
        private OffsetDateTime fulfilledAt;
        private OffsetDateTime expiresAt;
        private OffsetDateTime createdAt;
        /** 渠道支付参数（前端唤起支付用） */
        private Map<String, Object> paymentPayload;
        /** 支付跳转地址 */
        private String payUrl;
    }

    /**
     * 退款请求（管理端）。
     */
    @Data
    public static class RefundRequest {
        /** 退款原因（必填，进审计） */
        private String reason;
        /** 退款金额（可空，默认全额） */
        private BigDecimal amount;
    }

    /**
     * 退款结果。
     */
    @Data
    public static class RefundResult {
        private String orderNo;
        private String status;
        private String providerRefundId;
        private boolean accepted;
    }

    /**
     * 回调处理结果（返回给渠道）。
     */
    @Data
    public static class CallbackResult {
        /** 渠道名 */
        private String provider;
        /** 事件 ID */
        private String eventId;
        /** 订单号 */
        private String orderNo;
        /** processed / duplicate / rejected */
        private String result;
        /** 是否已入账 */
        private boolean credited;
        /** 履约是否已入队 */
        private boolean fulfillmentEnqueued;
    }

    /**
     * 对账差异视图。
     */
    @Data
    public static class ReconciliationView {
        private String id;
        private String orderNo;
        private String provider;
        private String localStatus;
        private String providerStatus;
        private String diffType;
        private BigDecimal amountDiff;
        private String status;
        private String resolvedBy;
        private OffsetDateTime resolvedAt;
        private String resolutionNote;
        private OffsetDateTime createdAt;
    }

    /**
     * 标记对账差异处理状态请求。
     */
    @Data
    public static class ResolveReconciliationRequest {
        /** resolved / ignored */
        private String status;
        private String note;
    }
}
