package com.nerd.erp.api.etsysync;

import java.math.BigDecimal;
import java.util.UUID;

public record EtsySyncProductDraft(
        long listingId, String referenceCode, String name, String description,
        BigDecimal unitPriceHt, BigDecimal vatRate, boolean alreadyInCatalog, UUID existingProductId) {
}
