package com.nerd.erp.api.merchant;

import java.util.UUID;

public record MerchantResponse(
        UUID id,
        UUID workspaceId,
        String name,
        String slug,
        String logoUrl
) {
}
