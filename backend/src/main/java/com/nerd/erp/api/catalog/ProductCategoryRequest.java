package com.nerd.erp.api.catalog;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record ProductCategoryRequest(
        @NotNull UUID workspaceId,
        @NotBlank String code,
        @NotBlank String label,
        Integer sortOrder
) {
}
