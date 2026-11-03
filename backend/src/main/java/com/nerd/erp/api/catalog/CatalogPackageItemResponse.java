package com.nerd.erp.api.catalog;

import java.math.BigDecimal;
import java.util.UUID;

public record CatalogPackageItemResponse(
        UUID productId,
        String productReferenceCode,
        String productName,
        BigDecimal unitPriceHt,
        String unitLabel,
        BigDecimal quantity,
        BigDecimal lineTotalHt
) {
}
