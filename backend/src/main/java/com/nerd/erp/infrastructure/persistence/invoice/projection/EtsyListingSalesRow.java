package com.nerd.erp.infrastructure.persistence.invoice.projection;

import java.math.BigDecimal;
import java.util.UUID;

public interface EtsyListingSalesRow {
    Long getEtsyListingId();
    UUID getProductId();
    String getProductName();
    String getProductReferenceCode();
    BigDecimal getQuantitySold();
    BigDecimal getRevenueHt();
    BigDecimal getRevenueTtc();
    Long getOrderCount();
    Long getLineCount();
}
