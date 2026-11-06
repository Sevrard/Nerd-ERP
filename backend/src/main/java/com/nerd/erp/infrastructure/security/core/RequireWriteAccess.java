package com.nerd.erp.infrastructure.security.core;

import org.springframework.security.access.prepost.PreAuthorize;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Autorisation déclarative : refuse (403) les rôles sans droit d'écriture (READONLY)
 * avant même d'entrer dans le controller. Complète — sans remplacer — les contrôles
 * {@link SecurityContextHelper#requireWriteAccess()} des services (défense en profondeur).
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@PreAuthorize("hasAnyRole('OWNER', 'ADMIN', 'USER')")
public @interface RequireWriteAccess {
}
