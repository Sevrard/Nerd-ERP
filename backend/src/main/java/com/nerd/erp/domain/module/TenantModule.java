package com.nerd.erp.domain.module;

import com.nerd.erp.domain.shared.TenantScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;

/** Activation d'un module optionnel pour un tenant (une ligne par couple tenant / module). */
@Entity
@Table(name = "tenant_module")
@Getter
@Setter
public class TenantModule extends TenantScopedEntity {

    @Enumerated(EnumType.STRING)
    @Column(name = "module_code", nullable = false, length = 64, updatable = false)
    private ModuleCode moduleCode;

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(name = "activated_at")
    private Instant activatedAt;

    /** Fin de validité incluse ; null = sans limite. */
    @Column(name = "expires_at")
    private LocalDate expiresAt;

    /** Actif aujourd'hui : activé et non expiré. */
    public boolean isCurrentlyActive(LocalDate today) {
        return enabled && (expiresAt == null || !expiresAt.isBefore(today));
    }
}
