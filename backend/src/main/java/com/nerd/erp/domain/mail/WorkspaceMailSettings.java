package com.nerd.erp.domain.mail;

import com.nerd.erp.domain.shared.TenantScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Boîte d'envoi propre à un workspace (Gmail, OVH, …) : les devis et factures partent au nom du client
 * depuis sa propre messagerie. Le mot de passe est chiffré en base et jamais renvoyé par l'API.
 */
@Entity
@Table(name = "workspace_mail_settings")
@Getter
@Setter
public class WorkspaceMailSettings extends TenantScopedEntity {

    @Column(name = "workspace_id", nullable = false, unique = true, updatable = false)
    private UUID workspaceId;

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(nullable = false)
    private String host;

    @Column(nullable = false)
    private int port = 587;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private SmtpSecurity security = SmtpSecurity.STARTTLS;

    @Column(nullable = false)
    private String username;

    /** Mot de passe chiffré (AES-GCM, Base64). */
    @Column(name = "password_encrypted", nullable = false, length = 1024)
    private String passwordEncrypted;

    @Column(name = "from_email", nullable = false)
    private String fromEmail;

    @Column(name = "from_name")
    private String fromName;

    @Column(name = "reply_to")
    private String replyTo;

    @Column(name = "last_test_at")
    private Instant lastTestAt;

    @Column(name = "last_test_ok")
    private Boolean lastTestOk;

    @Column(name = "last_error", length = 1024)
    private String lastError;
}
