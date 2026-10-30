package com.shortvideoscripagent.xhsagentyunying.billing.order;

import java.util.Locale;

/**
 * S4-2 订单状态枚举。
 * <p>
 * 状态机：{@code pending → paid → (fulfilled | refunded | closed)}；
 * {@code pending} 超时 → {@code closed}。
 * </p>
 */
public enum OrderStatus {

    /** 待支付 */
    PENDING("pending"),
    /** 已支付（未履约 / 履约中） */
    PAID("paid"),
    /** 已履约（订阅已开通、额度已写入） */
    FULFILLED("fulfilled"),
    /** 已退款 */
    REFUNDED("refunded"),
    /** 已关闭（超时未支付） */
    CLOSED("closed");

    private final String value;

    OrderStatus(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static OrderStatus parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return OrderStatus.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
