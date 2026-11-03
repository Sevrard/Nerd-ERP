package com.nerd.erp.api.catalog;

import com.nerd.erp.domain.catalog.PackageDiscountType;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record CatalogPackageResponse(
        UUID id,
        UUID workspaceId,
        String referenceCode,
        String title,
        String description,
        PackageDiscountType discountType,
        BigDecimal discountValue,
        String badgeTheme,
        boolean active,
        List<CatalogPackageItemResponse> items,
        BigDecimal grossTotalHt,
        BigDecimal discountAmountHt,
        BigDecimal netTotalHt
) {
}
