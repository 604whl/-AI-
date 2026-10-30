package com.shortvideoscripagent.xhsagentyunying.dto.billing;

import lombok.Data;

import java.math.BigDecimal;
import java.util.Map;

/**
 * S4-1 套餐相关 DTO。
 */
public final class PlanDtos {

    private PlanDtos() {
    }

    /**
     * 对外只读的套餐视图（公开列表 / 详情）。
     */
    @Data
    public static class PlanView {
        private String id;
        private String code;
        private Integer version;
        private String name;
        private String billingPeriod;
        private BigDecimal price;
        private String currency;
        private Map<String, Object> includedUnits;
        private BigDecimal overagePrice;
        private Map<String, Object> featureFlags;
        private String status;
        private String description;

        public static PlanView of(String id, String code, Integer version, String name,
                                  String billingPeriod, BigDecimal price, String currency,
                                  Map<String, Object> includedUnits, BigDecimal overagePrice,
                                  Map<String, Object> featureFlags, String status, String description) {
            PlanView v = new PlanView();
            v.id = id;
            v.code = code;
            v.version = version;
            v.name = name;
            v.billingPeriod = billingPeriod;
            v.price = price;
            v.currency = currency;
            v.includedUnits = includedUnits;
            v.overagePrice = overagePrice;
            v.featureFlags = featureFlags;
            v.status = status;
            v.description = description;
            return v;
        }
    }

    /**
     * 管理端创建套餐请求。
     */
    @Data
    public static class CreatePlanRequest {
        private String code;
        private String name;
        private String billingPeriod;
        private BigDecimal price;
        private String currency;
        private Map<String, Object> includedUnits;
        private BigDecimal overagePrice;
        private Map<String, Object> featureFlags;
        private String description;
        private String status;
    }

    /**
     * 管理端修改套餐请求（改价/改额度生成新版本）。
     */
    @Data
    public static class UpdatePlanRequest {
        private String name;
        private String billingPeriod;
        private BigDecimal price;
        private String currency;
        private Map<String, Object> includedUnits;
        private BigDecimal overagePrice;
        private Map<String, Object> featureFlags;
        private String description;
        private String status;
    }

    /**
     * 管理端订阅列表项。
     */
    @Data
    public static class AdminSubscriptionItem {
        private String id;
        private String workspaceId;
        private String planCode;
        private String planName;
        private String status;
        private java.time.OffsetDateTime currentPeriodStart;
        private java.time.OffsetDateTime currentPeriodEnd;
        private java.time.OffsetDateTime trialEnd;
        private Boolean cancelAtPeriodEnd;
        private Boolean autoRenew;
        private Integer gracePeriodDays;
        private BigDecimal price;
        private String currency;
    }
}
