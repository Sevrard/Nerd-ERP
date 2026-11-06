package com.nerd.erp.api.catalog;

import com.nerd.erp.domain.catalog.BillingType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.UUID;

public record ProductRequest(
        @NotNull UUID workspaceId,
        @NotBlank @Size(max = 64) String referenceCode,
        @NotBlank String name,
        String description,
        @NotNull @DecimalMin("0.0") BigDecimal unitPriceHt,
        @NotNull BigDecimal vatRate,
        Boolean active,
        UUID categoryId,
        /** Défaut {@link BillingType#UNIT} si absent. */
        BillingType billingType,
        /** Libellé d'unité ; vide = libellé par défaut du mode. */
        @Size(max = 32) String unitLabel
) {
}
