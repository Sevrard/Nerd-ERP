package com.nerd.erp.api.catalog;

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
        boolean active
) {
}
