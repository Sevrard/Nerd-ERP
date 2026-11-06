package com.nerd.erp.infrastructure.security.core;

import com.nerd.erp.domain.module.ModuleCode;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Endpoint réservé aux tenants ayant le module activé (403 {@code MODULE_DISABLED} sinon).
 * Vérifié par {@link ModuleAccessInterceptor} sur la méthode puis sur la classe du contrôleur.
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RequireModule {
    ModuleCode value();
}
