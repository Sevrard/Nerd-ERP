package com.nerd.erp.infrastructure.security.core;

import com.nerd.erp.api.error.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.UUID;

public final class SecurityContextHelper {

    private SecurityContextHelper() {
    }

    public static AuthenticatedUser requireUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof AuthenticatedUser user)) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        return user;
    }

    public static UUID requireTenantId() {
        return requireUser().tenantId();
    }

    public static void requireWriteAccess() {
        if (!requireUser().role().canWrite()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Read-only access");
        }
    }

    public static void requireAdminAccess() {
        if (!requireUser().role().canAdminister()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Administrator access required");
        }
    }
}
