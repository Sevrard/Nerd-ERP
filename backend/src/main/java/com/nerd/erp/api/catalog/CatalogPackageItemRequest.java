package com.nerd.erp.api.catalog;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

public record CatalogPackageItemRequest(
        @NotNull UUID productId,
        @NotNull @DecimalMin("0.0001") BigDecimal quantity
) {
}
