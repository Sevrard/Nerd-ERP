package com.nerd.erp.infrastructure.security.core;

import com.nerd.erp.domain.tenancy.TenantRole;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;
import java.util.UUID;

public record AuthenticatedUser(
        UUID userId,
        UUID tenantId,
        TenantRole role,
        String email
) {
    public List<SimpleGrantedAuthority> authorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }
}
