package com.nerd.erp.api.admin;

import com.nerd.erp.domain.module.ModuleCode;
import com.nerd.erp.domain.tenancy.TenantRole;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** DTOs de l'espace d'administration plateforme (gestion des tenants clients par l'éditeur). */
public final class AdminDtos {

    private AdminDtos() {
    }

    public record ModuleDefinition(ModuleCode code, String label, String description) {
        public static ModuleDefinition of(ModuleCode code) {
            return new ModuleDefinition(code, code.label(), code.description());
        }
    }

    public record TenantModuleState(
            ModuleCode code,
            String label,
            boolean enabled,
            boolean currentlyActive,
            Instant activatedAt,
            LocalDate expiresAt
    ) {
    }

    public record TenantSummary(
            UUID id,
            String name,
            String slug,
            boolean active,
            Instant createdAt,
            long userCount,
            List<ModuleCode> activeModules
    ) {
    }

    public record TenantUser(
            UUID userId,
            String email,
            String displayName,
            TenantRole role,
            boolean enabled,
            boolean platformAdmin,
            /** Invitation en attente (mot de passe pas encore choisi). */
            boolean pending,
            Instant lastLoginAt
    ) {
    }

    /**
     * Invitation émise lors d'une création. {@code activationUrl} n'est présent que si l'e-mail n'a pas
     * pu partir (boîte plateforme non configurée) : l'admin transmet alors le lien lui-même.
     */
    public record Invitation(String email, boolean emailSent, String activationUrl) {
    }

    public record TenantDetail(
            UUID id,
            String name,
            String slug,
            boolean active,
            Instant createdAt,
            Instant updatedAt,
            long workspaceCount,
            List<TenantUser> users,
            List<TenantModuleState> modules,
            String brandColor,
            String logoUrl,
            String loginTagline,
            /** Renseigné uniquement en réponse à une création de tenant / ajout d'utilisateur par invitation. */
            Invitation invitation
    ) {
        public TenantDetail withInvitation(Invitation invitation) {
            return new TenantDetail(id, name, slug, active, createdAt, updatedAt, workspaceCount, users, modules,
                    brandColor, logoUrl, loginTagline, invitation);
        }
    }

    public record TenantCreateRequest(
            @NotBlank @Size(max = 255) String name,
            @Size(max = 100) String slug,
            @Size(max = 255) String legalName,
            @NotBlank @Email @Size(max = 255) String ownerEmail,
            @Size(max = 255) String ownerDisplayName,
            /** Optionnel : sans mot de passe, un nouvel utilisateur reçoit une invitation par e-mail. */
            @Size(min = 8, max = 128) String ownerPassword,
            @Size(max = 255) String workspaceName,
            List<ModuleCode> modules
    ) {
    }

    public record TenantUpdateRequest(
            @Size(max = 255) String name,
            Boolean active,
            // --- Identité visuelle de l'écran de connexion (optionnels ; chaîne vide = effacer) ---
            @Pattern(regexp = "^$|^#[0-9a-fA-F]{6}$", message = "Couleur attendue au format #RRGGBB") String brandColor,
            @Size(max = 2048) @Pattern(regexp = "^$|^https://.*", message = "URL de logo en https:// attendue") String logoUrl,
            @Size(max = 255) String loginTagline
    ) {
    }

    public record ModuleUpdate(
            @NotNull ModuleCode code,
            boolean enabled,
            LocalDate expiresAt
    ) {
    }

    public record ModulesUpdateRequest(@NotNull @Valid List<ModuleUpdate> modules) {
    }

    public record TenantUserCreateRequest(
            @NotBlank @Email @Size(max = 255) String email,
            @Size(max = 255) String displayName,
            /** Optionnel : sans mot de passe, un nouvel utilisateur reçoit une invitation par e-mail. */
            @Size(min = 8, max = 128) String password,
            @NotNull TenantRole role
    ) {
    }
}
