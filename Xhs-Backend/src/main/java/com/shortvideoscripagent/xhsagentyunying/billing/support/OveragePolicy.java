package com.shortvideoscripagent.xhsagentyunying.billing.support;

/**
 * S4-1 超额策略枚举。
 */
public enum OveragePolicy {
    /** 拒绝（默认）：额度用尽直接报错 */
    REJECT("reject"),
    /** 转按量扣款 */
    CHARGE_OVERSAGE("charge_oversage"),
    /** 降级功能 */
    DOWNGRADE("downgrade");

    private final String value;

    OveragePolicy(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static String parseOrDefault(String raw) {
        if (raw == null || raw.isBlank()) {
            return REJECT.value;
        }
        try {
            return OveragePolicy.valueOf(raw.trim().toUpperCase()).value;
        } catch (IllegalArgumentException ex) {
            return REJECT.value;
        }
    }
}
