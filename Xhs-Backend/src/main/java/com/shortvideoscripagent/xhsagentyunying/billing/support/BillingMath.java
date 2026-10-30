package com.shortvideoscripagent.xhsagentyunying.billing.support;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Map;

/**
 * S4-1 金额与周期计算工具。
 * <p>
 * 金额一律 {@link BigDecimal}，比例计算统一 {@link RoundingMode#HALF_UP} 到 4 位小数，
 * 与数据库 {@code NUMERIC(18,4)} 对齐。
 * </p>
 */
public final class BillingMath {

    /** 与 DB NUMERIC(18,4) 对齐的标度 */
    public static final int SCALE = 4;

    private BillingMath() {
    }

    public static BigDecimal scale(BigDecimal value) {
        if (value == null) {
            return BigDecimal.ZERO.setScale(SCALE, RoundingMode.HALF_UP);
        }
        return value.setScale(SCALE, RoundingMode.HALF_UP);
    }

    /**
     * 升级补差价：按本期剩余天数比例计算两个套餐的差额。
     *
     * @param currentPrice 当前套餐周期价格
     * @param targetPrice  目标套餐周期价格
     * @param periodStart  本期开始
     * @param periodEnd    本期结束
     * @return 补差价（&gt;= 0；目标更便宜时返回 0，由降级路径处理）
     */
    public static BigDecimal proratedUpgradeAmount(BigDecimal currentPrice,
                                                   BigDecimal targetPrice,
                                                   OffsetDateTime periodStart,
                                                   OffsetDateTime periodEnd) {
        BigDecimal current = scale(currentPrice);
        BigDecimal target = scale(targetPrice);
        if (target.compareTo(current) <= 0) {
            return BigDecimal.ZERO.setScale(SCALE, RoundingMode.HALF_UP);
        }
        BigDecimal diff = target.subtract(current);
        if (periodStart == null || periodEnd == null || !periodEnd.isAfter(periodStart)) {
            return scale(diff);
        }
        long totalDays = ChronoUnit.DAYS.between(periodStart, periodEnd);
        long remainingDays = ChronoUnit.DAYS.between(OffsetDateTime.now(), periodEnd);
        if (totalDays <= 0) {
            return scale(diff);
        }
        if (remainingDays < 0) {
            remainingDays = 0;
        }
        if (remainingDays > totalDays) {
            remainingDays = totalDays;
        }
        BigDecimal ratio = BigDecimal.valueOf(remainingDays)
                .divide(BigDecimal.valueOf(totalDays), 6, RoundingMode.HALF_UP);
        return scale(diff.multiply(ratio));
    }

    /**
     * 计算周期内包含额度合计（所有计量单位求和），作为 {@code period_quota}。
     */
    public static int sumIncludedUnits(Map<String, Object> includedUnits) {
        if (includedUnits == null || includedUnits.isEmpty()) {
            return 0;
        }
        int sum = 0;
        for (Object v : includedUnits.values()) {
            if (v instanceof Number n) {
                sum += n.intValue();
            } else if (v != null) {
                try {
                    sum += Integer.parseInt(String.valueOf(v));
                } catch (NumberFormatException ignored) {
                    // 非数字项跳过
                }
            }
        }
        return sum;
    }

    /**
     * 计算两个套餐的包含额度差额（升级为正，降级为负），按剩余天数比例折算。
     */
    public static BigDecimal proratedQuotaDelta(Map<String, Object> currentUnits,
                                                Map<String, Object> targetUnits,
                                                OffsetDateTime periodStart,
                                                OffsetDateTime periodEnd) {
        int current = sumIncludedUnits(currentUnits);
        int target = sumIncludedUnits(targetUnits);
        int delta = target - current;
        if (delta == 0) {
            return BigDecimal.ZERO.setScale(SCALE, RoundingMode.HALF_UP);
        }
        if (periodStart == null || periodEnd == null || !periodEnd.isAfter(periodStart)) {
            return scale(BigDecimal.valueOf(delta));
        }
        long totalDays = ChronoUnit.DAYS.between(periodStart, periodEnd);
        long remainingDays = ChronoUnit.DAYS.between(OffsetDateTime.now(), periodEnd);
        if (totalDays <= 0) {
            return scale(BigDecimal.valueOf(delta));
        }
        if (remainingDays < 0) {
            remainingDays = 0;
        }
        if (remainingDays > totalDays) {
            remainingDays = totalDays;
        }
        BigDecimal ratio = BigDecimal.valueOf(remainingDays)
                .divide(BigDecimal.valueOf(totalDays), 6, RoundingMode.HALF_UP);
        return scale(BigDecimal.valueOf(delta).multiply(ratio));
    }
}
