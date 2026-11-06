package com.nerd.erp.api.mail;

import com.nerd.erp.domain.mail.EmailKind;
import com.nerd.erp.domain.mail.EmailStatus;
import com.nerd.erp.domain.mail.SmtpSecurity;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

public final class MailDtos {

    private MailDtos() {
    }

    /** Paramètres SMTP d'un workspace ; le mot de passe n'est jamais renvoyé (seulement {@code passwordSet}). */
    public record WorkspaceMailSettingsResponse(
            UUID workspaceId,
            boolean configured,
            boolean enabled,
            String host,
            Integer port,
            SmtpSecurity security,
            String username,
            boolean passwordSet,
            String fromEmail,
            String fromName,
            String replyTo,
            Instant lastTestAt,
            Boolean lastTestOk,
            String lastError,
            /** Boîte plateforme disponible en secours si le workspace n'a pas de SMTP. */
            boolean platformFallbackAvailable
    ) {
    }

    public record WorkspaceMailSettingsRequest(
            boolean enabled,
            @NotBlank @Size(max = 255) String host,
            @NotNull @Min(1) @Max(65535) Integer port,
            @NotNull SmtpSecurity security,
            @NotBlank @Size(max = 255) String username,
            /** Vide ou null = conserver le mot de passe enregistré. */
            @Size(max = 512) String password,
            @NotBlank @Email @Size(max = 255) String fromEmail,
            @Size(max = 255) String fromName,
            @Email @Size(max = 255) String replyTo
    ) {
    }

    /** Test d'envoi : paramètres du formulaire (non encore enregistrés) ; mot de passe vide = celui en base. */
    public record MailTestRequest(
            @NotBlank @Size(max = 255) String host,
            @NotNull @Min(1) @Max(65535) Integer port,
            @NotNull SmtpSecurity security,
            @NotBlank @Size(max = 255) String username,
            @Size(max = 512) String password,
            @NotBlank @Email @Size(max = 255) String fromEmail,
            @Size(max = 255) String fromName,
            @Email @Size(max = 255) String replyTo,
            /** Destinataire ; par défaut l'utilisateur connecté. */
            @Email @Size(max = 255) String to
    ) {
    }

    public record MailTestResponse(boolean ok, String sentTo, String message) {
    }

    public record EmailLogResponse(
            UUID id,
            EmailKind kind,
            EmailStatus status,
            String fromEmail,
            String toEmail,
            String subject,
            UUID relatedId,
            String senderSource,
            int attempts,
            Instant createdAt,
            Instant sentAt,
            String lastError
    ) {
    }
}
