package com.nerd.erp.application.money;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
}
