package com.nerd.erp.api.catalog;

import com.nerd.erp.domain.catalog.BillingType;

import java.math.BigDecimal;
import java.util.UUID;

public record ProductResponse(
        UUID id,
        UUID workspaceId,
        String referenceCode,
        String name,
        String description,
        BigDecimal unitPriceHt,
        BigDecimal vatRate,
        boolean active,
        UUID categoryId,
        String categoryLabel,
        BillingType billingType,
        /** Libellé d'unité effectif (jamais null). */
        String unitLabel
) {
}
