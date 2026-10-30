package com.shortvideoscripagent.xhsagentyunying.billing.order;

import java.util.Locale;

/**
 * S4-2 订单类型枚举（D6 混合计费）。
 */
public enum OrderType {

    /** 订阅订单 */
    SUBSCRIPTION("subscription"),
    /** 按量充值订单 */
    USAGE("usage");

    private final String value;

    OrderType(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static OrderType parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return OrderType.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
