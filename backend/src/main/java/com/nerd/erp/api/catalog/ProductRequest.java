package com.nerd.erp.api.catalog;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

public record ProductRequest(
        @NotNull UUID workspaceId,
        @NotBlank String referenceCode,
        @NotBlank String name,
        String description,
        @NotNull @DecimalMin("0.0") BigDecimal unitPriceHt,
        @NotNull BigDecimal vatRate,
        Boolean active
) {
}
