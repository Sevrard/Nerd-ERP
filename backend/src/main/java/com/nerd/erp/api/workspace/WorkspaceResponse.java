package com.nerd.erp.api.workspace;

import java.util.UUID;

public record WorkspaceResponse(
        UUID id,
        String name,
        String slug,
        UUID organizationId,
        String currencyCode,
        String brandColor,
        String logoUrl,
        boolean active
) {
}
