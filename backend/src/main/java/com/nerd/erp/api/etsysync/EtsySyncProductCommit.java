package com.nerd.erp.api.etsysync;

import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record EtsySyncProductCommit(
        long listingId, boolean importProduct, String referenceCode, String name, String description,
        @NotNull BigDecimal unitPriceHt, @NotNull BigDecimal vatRate, boolean active) {
}
