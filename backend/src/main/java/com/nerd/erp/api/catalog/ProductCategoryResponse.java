package com.nerd.erp.api.catalog;

import java.util.UUID;

public record ProductCategoryResponse(
        UUID id,
        UUID workspaceId,
        String code,
        String label,
        int sortOrder
) {
}
