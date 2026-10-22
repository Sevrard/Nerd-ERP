package com.nerd.erp.api.dashboard;

import java.math.BigDecimal;

public record MonthlyRevenuePoint(
        String month,
        String label,
        BigDecimal invoicedRevenue,
        BigDecimal collectedAmount
) {
}
