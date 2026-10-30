package com.shortvideoscripagent.xhsagentyunying.billing.order;

/**
 * S4-2 对账差异类型枚举。
 */
public enum ReconciliationDiffType {

    /** 本地已付、渠道未付 */
    LOCAL_PAID_CHANNEL_UNPAID("local_paid_channel_unpaid"),
    /** 本地未付、渠道已付 */
    LOCAL_UNPAID_CHANNEL_PAID("local_unpaid_channel_paid"),
    /** 金额不一致 */
    AMOUNT_MISMATCH("amount_mismatch");

    private final String value;

    ReconciliationDiffType(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }
}
