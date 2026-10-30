package com.shortvideoscripagent.xhsagentyunying.billing.support;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * S4-3 账单数学单元测试。
 * <p>
 * 覆盖：
 * <ul>
 *   <li>金额 scale 归一（HALF_UP，4 位）；</li>
 *   <li>套餐包含额度求和；</li>
 *   <li>升级补差价按剩余周期比例计算；</li>
 *   <li>升级额度增量按剩余周期折算。</li>
 * </ul>
 */
class BillingMathTest {

    @Test
    void scale_shouldRoundHalfUpToFourDecimals() {
        assertEquals(new BigDecimal("1.2346"), BillingMath.scale(new BigDecimal("1.23455")));
        assertEquals(new BigDecimal("1.2345"), BillingMath.scale(new BigDecimal("1.23454999")));
        assertEquals(new BigDecimal("0.0000"), BillingMath.scale(BigDecimal.ZERO));
    }

    @Test
    void scale_shouldHandleNullAsZero() {
        assertEquals(new BigDecimal("0.0000"), BillingMath.scale(null));
    }

    @Test
    void sumIncludedUnits_shouldSumAllValues() {
        Map<String, Object> units = new LinkedHashMap<>();
        units.put("analysis", 100);
        units.put("chat", 500);
        units.put("browser", 50);
        assertEquals(650, BillingMath.sumIncludedUnits(units));
    }

    @Test
    void sumIncludedUnits_shouldReturnZeroForNullOrEmpty() {
        assertEquals(0, BillingMath.sumIncludedUnits(null));
        assertEquals(0, BillingMath.sumIncludedUnits(Map.of()));
    }

    @Test
    void sumIncludedUnits_shouldIgnoreNonNumericValues() {
        Map<String, Object> units = new LinkedHashMap<>();
        units.put("analysis", 100);
        units.put("note", "unlimited");
        assertEquals(100, BillingMath.sumIncludedUnits(units));
    }

    @Test
    void proratedUpgradeAmount_shouldScaleByRemainingPeriod() {
        OffsetDateTime start = OffsetDateTime.now().minusDays(15);
        OffsetDateTime end = OffsetDateTime.now().plusDays(15);
        // 199 -> 599，差价 400，剩余一半周期 -> 约 200
        BigDecimal prorated = BillingMath.proratedUpgradeAmount(
                new BigDecimal("199"), new BigDecimal("599"), start, end);
        assertTrue(prorated.compareTo(new BigDecimal("150")) > 0,
                "补差价应落在剩余周期对应的区间，实际=" + prorated);
        assertTrue(prorated.compareTo(new BigDecimal("250")) < 0,
                "补差价应落在剩余周期对应的区间，实际=" + prorated);
    }

    @Test
    void proratedUpgradeAmount_shouldBeZeroWhenPriceUnchanged() {
        BigDecimal prorated = BillingMath.proratedUpgradeAmount(
                new BigDecimal("199"), new BigDecimal("199"),
                OffsetDateTime.now().minusDays(10), OffsetDateTime.now().plusDays(20));
        assertEquals(0, prorated.compareTo(BigDecimal.ZERO));
    }

    @Test
    void proratedQuotaDelta_shouldScaleByRemainingPeriod() {
        Map<String, Object> current = Map.of("analysis", 100);
        Map<String, Object> target = Map.of("analysis", 900);
        OffsetDateTime start = OffsetDateTime.now().minusDays(15);
        OffsetDateTime end = OffsetDateTime.now().plusDays(15);
        BigDecimal delta = BillingMath.proratedQuotaDelta(current, target, start, end);
        // 差额 800，剩余一半 -> 约 400
        assertTrue(delta.compareTo(new BigDecimal("300")) > 0, "实际=" + delta);
        assertTrue(delta.compareTo(new BigDecimal("500")) < 0, "实际=" + delta);
    }

    @Test
    void proratedQuotaDelta_shouldBeZeroWhenSameQuota() {
        Map<String, Object> units = Map.of("analysis", 100);
        BigDecimal delta = BillingMath.proratedQuotaDelta(units, units,
                OffsetDateTime.now().minusDays(10), OffsetDateTime.now().plusDays(10));
        assertEquals(0, delta.compareTo(BigDecimal.ZERO));
    }
}
