package com.nerd.erp.api.etsysync;

import java.math.BigDecimal;
import java.util.UUID;

public record EtsyListingSalesStat(
        long etsyListingId, UUID productId, String productName, String productReferenceCode,
        BigDecimal quantitySold, BigDecimal revenueHt, BigDecimal revenueTtc,
        long orderCount, long lineCount) {
}
