package com.nerd.erp.infrastructure.security.core;

import org.springframework.security.access.prepost.PreAuthorize;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Réservé aux administrateurs de la plateforme (éditeur), indépendamment du rôle dans le tenant.
 * Les endpoints concernés travaillent hors contexte tenant (gestion des clients de l'éditeur).
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@PreAuthorize("hasRole('PLATFORM_ADMIN')")
public @interface RequirePlatformAdmin {
}
