package com.nerd.erp.infrastructure.security.core;

import com.nerd.erp.domain.tenancy.TenantRole;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public record AuthenticatedUser(
        UUID userId,
        UUID tenantId,
        TenantRole role,
        String email,
        boolean platformAdmin
) {
    public static final String PLATFORM_ADMIN_ROLE = "ROLE_PLATFORM_ADMIN";

    public List<SimpleGrantedAuthority> authorities() {
        List<SimpleGrantedAuthority> list = new ArrayList<>(2);
        list.add(new SimpleGrantedAuthority("ROLE_" + role.name()));
        if (platformAdmin) {
            list.add(new SimpleGrantedAuthority(PLATFORM_ADMIN_ROLE));
        }
        return list;
    }
}
