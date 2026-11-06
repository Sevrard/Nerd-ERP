package com.nerd.erp.api.auth;

import com.nerd.erp.domain.tenancy.TenantRole;

import java.util.UUID;

public record LoginResponse(
        String accessToken,
        UUID userId,
        UUID tenantId,
        String tenantSlug,
        TenantRole role,
        String email,
        String displayName,
        boolean platformAdmin
) {
}
