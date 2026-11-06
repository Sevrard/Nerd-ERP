package com.nerd.erp.application.money;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MoneyCalculatorTest {

    @Test
    void computesLineTotals() {
        MoneyCalculator.LineTotals totals = MoneyCalculator.computeLine(
                new BigDecimal("2"),
                new BigDecimal("100.00"),
                new BigDecimal("20.00"));
        assertEquals(new BigDecimal("200.0000"), totals.totalHt());
        assertEquals(new BigDecimal("40.0000"), totals.totalVat());
        assertEquals(new BigDecimal("240.0000"), totals.totalTtc());
    }

    @Test
    void appliesGlobalDiscountProportionally() {
        MoneyCalculator.DocumentTotals gross = new MoneyCalculator.DocumentTotals(
                new BigDecimal("1000.0000"), new BigDecimal("200.0000"), new BigDecimal("1200.0000"));
        MoneyCalculator.DiscountedTotals d = MoneyCalculator.applyDiscount(gross, new BigDecimal("12.5"));
        assertEquals(new BigDecimal("125.0000"), d.discountAmountHt());
        assertEquals(new BigDecimal("875.0000"), d.net().totalHt());
        assertEquals(new BigDecimal("175.0000"), d.net().totalVat());
        assertEquals(new BigDecimal("1050.0000"), d.net().totalTtc());
    }

    @Test
    void zeroOrNullDiscountLeavesTotalsUntouched() {
        MoneyCalculator.DocumentTotals gross = new MoneyCalculator.DocumentTotals(
                new BigDecimal("99.9900"), new BigDecimal("19.9980"), new BigDecimal("119.9880"));
        assertEquals(gross, MoneyCalculator.applyDiscount(gross, null).net());
        assertEquals(gross, MoneyCalculator.applyDiscount(gross, BigDecimal.ZERO).net());
        assertEquals(0, BigDecimal.ZERO.compareTo(MoneyCalculator.applyDiscount(gross, null).discountAmountHt()));
    }

    @Test
    void rejectsDiscountAbove100() {
        MoneyCalculator.DocumentTotals gross = new MoneyCalculator.DocumentTotals(
                BigDecimal.TEN, BigDecimal.ONE, BigDecimal.TEN);
        assertThrows(IllegalArgumentException.class,
                () -> MoneyCalculator.applyDiscount(gross, new BigDecimal("100.01")));
    }

    @Test
    void percentOfRoundsToMoneyScale() {
        assertEquals(new BigDecimal("36.0000"), MoneyCalculator.percentOf(new BigDecimal("120"), new BigDecimal("30")));
        assertEquals(new BigDecimal("0.0000"), MoneyCalculator.percentOf(null, new BigDecimal("30")));
    }
}
