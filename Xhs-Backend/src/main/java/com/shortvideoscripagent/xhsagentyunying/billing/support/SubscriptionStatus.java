package com.shortvideoscripagent.xhsagentyunying.billing.support;

/**
 * S4-1 订阅状态枚举（状态机）。
 * <p>
 * 正常路径：{@code trialing → active → past_due → (active | canceled)}；
 * 到期未续费：{@code past_due → expired}。
 * </p>
 */
public enum SubscriptionStatus {
    /** 试用中 */
    TRIALING("trialing"),
    /** 生效中 */
    ACTIVE("active"),
    /** 宽限期（未支付，功能不降级） */
    PAST_DUE("past_due"),
    /** 已取消 */
    CANCELED("canceled"),
    /** 已到期（已降级到 FREE） */
    EXPIRED("expired"),
    /** 平台侧封禁 */
    SUSPENDED("suspended");

    private final String value;

    SubscriptionStatus(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    /** 生效中的订阅（占用部分唯一索引）。 */
    public static boolean isLive(String status) {
        return TRIALING.value.equals(status)
                || ACTIVE.value.equals(status)
                || PAST_DUE.value.equals(status);
    }

    public static SubscriptionStatus parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return SubscriptionStatus.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
