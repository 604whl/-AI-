package com.shortvideoscripagent.xhsagentyunying.quota;

/**
 * 配额扣减结果。
 */
public record QuotaCheckResult(
        boolean success,
        java.util.UUID ledgerId,
        String message
) {
    public static QuotaCheckResult ok(java.util.UUID ledgerId) {
        return new QuotaCheckResult(true, ledgerId, "OK");
    }

    public static QuotaCheckResult exceeded(String reason) {
        return new QuotaCheckResult(false, null, reason);
    }

    public boolean isOk() {
        return success;
    }
}
