package com.shortvideoscripagent.xhsagentyunying.billing.support;

/**
 * S4-1 套餐状态枚举。
 */
public enum PlanStatus {
    DRAFT("draft"),
    ACTIVE("active"),
    ARCHIVED("archived");

    private final String value;

    PlanStatus(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static PlanStatus parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return DRAFT;
        }
        try {
            return PlanStatus.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return DRAFT;
        }
    }
}
