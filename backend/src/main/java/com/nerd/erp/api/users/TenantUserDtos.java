package com.nerd.erp.api.users;

import com.nerd.erp.domain.tenancy.TenantRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

/** DTOs de la gestion des utilisateurs d'un tenant par ses administrateurs. */
public final class TenantUserDtos {

    private TenantUserDtos() {
    }

    public record TenantUserResponse(
            UUID userId,
            String email,
            String displayName,
            TenantRole role,
            boolean enabled,
            /** Invitation en attente : l'utilisateur n'a pas encore choisi son mot de passe. */
            boolean pending,
            Instant lastLoginAt,
            Instant memberSince,
            /** L'utilisateur connecté lui-même (certaines actions sont interdites sur soi). */
            boolean self
    ) {
    }

    public record InviteUserRequest(
            @NotBlank @Email @Size(max = 320) String email,
            @Size(max = 255) String displayName,
            @NotNull TenantRole role
    ) {
    }

    /**
     * Résultat d'une invitation. {@code activationUrl} n'est renseigné que si l'e-mail n'a pas pu être
     * envoyé (boîte plateforme absente) : l'administrateur transmet alors le lien lui-même.
     */
    public record InvitationResult(TenantUserResponse user, boolean emailSent, String activationUrl) {
    }

    public record TenantUserUpdateRequest(TenantRole role, Boolean enabled) {
    }
}
