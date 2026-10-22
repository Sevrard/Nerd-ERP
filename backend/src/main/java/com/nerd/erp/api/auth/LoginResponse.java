package com.nerd.erp.api.auth;

import com.nerd.erp.domain.tenancy.TenantRole;

import java.util.UUID;

public record LoginResponse(
        String accessToken,
        UUID userId,
        UUID tenantId,
        TenantRole role,
        String email
) {
}
