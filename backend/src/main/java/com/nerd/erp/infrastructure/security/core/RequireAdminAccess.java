package com.nerd.erp.infrastructure.security.core;

import org.springframework.security.access.prepost.PreAuthorize;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Autorisation déclarative : réservé aux rôles OWNER / ADMIN (403 sinon).
 * Complète les contrôles {@link SecurityContextHelper#requireAdminAccess()} des services.
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@PreAuthorize("hasAnyRole('OWNER', 'ADMIN')")
public @interface RequireAdminAccess {
}
