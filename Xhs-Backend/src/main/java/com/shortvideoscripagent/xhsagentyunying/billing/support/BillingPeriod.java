package com.shortvideoscripagent.xhsagentyunying.billing.support;

/**
 * S4-1 计费周期枚举。
 */
public enum BillingPeriod {
    MONTH(1),
    QUARTER(3),
    YEAR(12);

    private final int months;

    BillingPeriod(int months) {
        this.months = months;
    }

    public int getMonths() {
        return months;
    }

    public static BillingPeriod parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return MONTH;
        }
        try {
            return BillingPeriod.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return MONTH;
        }
    }
}
