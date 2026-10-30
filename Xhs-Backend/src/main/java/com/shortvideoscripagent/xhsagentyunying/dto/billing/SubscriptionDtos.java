package com.shortvideoscripagent.xhsagentyunying.dto.billing;

import lombok.Data;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Map;

/**
 * S4-1 订阅相关 DTO。
 */
public final class SubscriptionDtos {

    private SubscriptionDtos() {
    }

    /**
     * 工作区订阅视图（读全量：OWNER / ADMIN）。
     */
    @Data
    public static class SubscriptionView {
        private String id;
        private String workspaceId;
        private String planId;
        private String planCode;
        private String planName;
        private Integer planVersion;
        private String billingPeriod;
        private BigDecimal price;
        private String currency;
        private Map<String, Object> includedUnits;
        private Map<String, Object> featureFlags;
        private String status;
        private OffsetDateTime currentPeriodStart;
        private OffsetDateTime currentPeriodEnd;
        private OffsetDateTime trialEnd;
        private Boolean cancelAtPeriodEnd;
        private String pendingPlanCode;
        private Boolean autoRenew;
        private Integer gracePeriodDays;
        private OffsetDateTime subscribedAt;
        private OffsetDateTime canceledAt;
        /** 当前周期额度合计 */
        private Integer periodQuota;
        private String overagePolicy;
    }

    /**
     * 订阅创建请求。
     */
    @Data
    public static class SubscribeRequest {
        /** 套餐编码：FREE / PRO / TEAM / USAGE */
        private String planCode;
        /** 是否以试用方式开通（默认 false；试用接口单独提供） */
        private Boolean trial;
        /** 试用天数（默认 14，上限 30） */
        private Integer trialDays;
        /** 是否自动续费，默认 true */
        private Boolean autoRenew;
    }

    /**
     * 改套餐请求。
     */
    @Data
    public static class ChangePlanRequest {
        /** 目标套餐编码 */
        private String planCode;
    }

    /**
     * 试用请求。
     */
    @Data
    public static class TrialRequest {
        /** 试用套餐编码，默认 PRO */
        private String planCode;
        /** 试用天数，默认 14，上限 30 */
        private Integer trialDays;
    }

    /**
     * 取消订阅请求。
     */
    @Data
    public static class CancelRequest {
        /** 是否在周期末取消（默认 true；false 表示立即取消） */
        private Boolean atPeriodEnd;
        /** 取消原因 */
        private String reason;
    }

    /**
     * 改套餐结果（供前端展示补差价）。
     */
    @Data
    public static class ChangePlanResult {
        /** immediate（升级立即生效） / period_end（降级周期末生效） */
        private String effective;
        private String fromPlanCode;
        private String toPlanCode;
        /** 按剩余天数比例计算的补差价（升级时 > 0） */
        private BigDecimal proratedAmount;
        private String currency;
        /** 按比例折算的额度增量（升级为正，降级为负） */
        private BigDecimal proratedQuotaDelta;
        private SubscriptionView subscription;
    }

    /**
     * 管理端补偿/延期请求。
     */
    @Data
    public static class AdminAdjustRequest {
        /** 延期天数（延长 current_period_end） */
        private Integer extendDays;
        /** 目标状态（可选）：active / past_due / canceled / suspended */
        private String status;
        /** 宽限期天数覆盖 */
        private Integer gracePeriodDays;
        /** 备注（必填，进审计） */
        private String reason;
    }
}
