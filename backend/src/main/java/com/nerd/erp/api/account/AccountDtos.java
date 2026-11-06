package com.nerd.erp.api.account;

import com.nerd.erp.domain.account.AccountTokenKind;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

/** DTOs des parcours de compte : mot de passe oublié, activation d'invitation, profil. */
public final class AccountDtos {

    private AccountDtos() {
    }

    public record ForgotPasswordRequest(@NotBlank @Email @Size(max = 320) String email) {
    }

    public record ResetPasswordRequest(
            @NotBlank String token,
            @NotBlank @Size(min = 8, max = 128) String password
    ) {
    }

    public record ActivateRequest(
            @NotBlank String token,
            @Size(max = 255) String displayName,
            @NotBlank @Size(min = 8, max = 128) String password
    ) {
    }

    /** Ce que la page publique affiche avant que l'utilisateur ne saisisse quoi que ce soit. */
    public record TokenInfoResponse(
            AccountTokenKind kind,
            String email,
            String displayName,
            String tenantName,
            String tenantSlug
    ) {
    }

    public record ProfileResponse(UUID userId, String email, String displayName, Instant lastLoginAt) {
    }

    public record ProfileUpdateRequest(@NotBlank @Size(max = 255) String displayName) {
    }

    public record ChangePasswordRequest(
            @NotBlank String currentPassword,
            @NotBlank @Size(min = 8, max = 128) String newPassword
    ) {
    }

    /** État d'acceptation des CGU/CGV : {@code accepted} est faux si la version courante n'a pas été acceptée. */
    public record TermsStatusResponse(String currentVersion, String acceptedVersion, Instant acceptedAt,
                                      boolean accepted) {
    }

    /** Identité visuelle publique d'un compte client pour l'écran de connexion. */
    public record LoginBrandingResponse(String tenantSlug, String tenantName, String brandColor, String logoUrl,
                                        String tagline) {
    }
}
