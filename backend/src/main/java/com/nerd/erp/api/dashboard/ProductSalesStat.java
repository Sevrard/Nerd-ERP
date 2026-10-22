package com.nerd.erp.api.dashboard;

import java.math.BigDecimal;
import java.util.UUID;

public record ProductSalesStat(
        UUID productId,
        String productName,
        String productReferenceCode,
        BigDecimal quantitySold,
        BigDecimal revenueHt,
        BigDecimal revenueTtc,
        long orderCount,
        long lineCount) {
}
