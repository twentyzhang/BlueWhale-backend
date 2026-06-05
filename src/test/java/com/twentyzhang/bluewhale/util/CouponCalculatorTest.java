package com.twentyzhang.bluewhale.util;

import com.twentyzhang.bluewhale.util.CouponCalculator.CouponSpec;
import com.twentyzhang.bluewhale.util.CouponCalculator.Result;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("CouponCalculator")
class CouponCalculatorTest {

    private static CouponSpec discount(long id, String rate) {
        return new CouponSpec(id, CouponCalculator.TYPE_DISCOUNT, new BigDecimal(rate));
    }

    private static CouponSpec fullReduction(long id, String off) {
        return new CouponSpec(id, CouponCalculator.TYPE_FULL_REDUCTION, new BigDecimal(off));
    }

    private static CouponSpec directOff(long id, String off) {
        return new CouponSpec(id, CouponCalculator.TYPE_DIRECT_OFF, new BigDecimal(off));
    }

    @Test
    @DisplayName("无券：折扣 0，实付 = 总额")
    void noCoupons() {
        Result r = CouponCalculator.calculate(new BigDecimal("40.00"), List.of());
        assertEquals(new BigDecimal("0.00"), r.discountAmount());
        assertEquals(new BigDecimal("40.00"), r.payableAmount());
        assertTrue(r.appliedCouponIds().isEmpty());
    }

    @Test
    @DisplayName("单张折扣券（8折）：discount=8, payable=32")
    void singleDiscount() {
        Result r = CouponCalculator.calculate(new BigDecimal("40.00"), List.of(discount(1, "0.80")));
        assertEquals(new BigDecimal("8.00"), r.discountAmount());
        assertEquals(new BigDecimal("32.00"), r.payableAmount());
    }

    @Test
    @DisplayName("单张满减券（减5）：discount=5, payable=35")
    void singleFullReduction() {
        Result r = CouponCalculator.calculate(new BigDecimal("40.00"), List.of(fullReduction(1, "5.00")));
        assertEquals(new BigDecimal("5.00"), r.discountAmount());
        assertEquals(new BigDecimal("35.00"), r.payableAmount());
    }

    @Test
    @DisplayName("折扣 + 满减叠加：取『先折后减』最优 discount=20（优于先减后折的 18）")
    void stacked_picksOptimalOrder() {
        // total 50；先8折→40 再减10→30（discount 20）vs 先减10→40 再8折→32（discount 18）
        Result r = CouponCalculator.calculate(new BigDecimal("50.00"),
                List.of(fullReduction(2, "10.00"), discount(1, "0.80")));
        assertEquals(new BigDecimal("20.00"), r.discountAmount());
        assertEquals(new BigDecimal("30.00"), r.payableAmount());
        // 最优顺序：折扣(1) 在满减(2) 之前
        assertEquals(List.of(1L, 2L), r.appliedCouponIds());
    }

    @Test
    @DisplayName("三种类型叠加（折扣+满减+直减），先折后减最优")
    void threeTypesStacked() {
        // total 100；先5折→50，再减20→30，再直减5→25 → discount 75
        Result r = CouponCalculator.calculate(new BigDecimal("100.00"),
                List.of(directOff(3, "5.00"), fullReduction(2, "20.00"), discount(1, "0.50")));
        assertEquals(new BigDecimal("75.00"), r.discountAmount());
        assertEquals(new BigDecimal("25.00"), r.payableAmount());
        assertEquals(1L, r.appliedCouponIds().get(0)); // 折扣排在最前
    }

    @Test
    @DisplayName("券额超过订单金额：实付落到下限 0.01，不为负")
    void overDiscount_flooredAtMinPayable() {
        Result r = CouponCalculator.calculate(new BigDecimal("10.00"), List.of(fullReduction(1, "30.00")));
        assertEquals(new BigDecimal("0.01"), r.payableAmount());
        assertEquals(new BigDecimal("9.99"), r.discountAmount());
    }
}
