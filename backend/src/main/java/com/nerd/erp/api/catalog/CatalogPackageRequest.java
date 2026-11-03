package com.nerd.erp.api.catalog;

import com.nerd.erp.domain.catalog.PackageDiscountType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record CatalogPackageRequest(
        @NotNull UUID workspaceId,
        @NotBlank String referenceCode,
        @NotBlank String title,
        String description,
        @NotNull PackageDiscountType discountType,
        @NotNull @DecimalMin("0.0") BigDecimal discountValue,
        String badgeTheme,
        Boolean active,
        @NotEmpty @Valid List<CatalogPackageItemRequest> items
) {
}
