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

/** Trace de chaque e-mail émis par l'application (qui, à qui, quoi, résultat). Le corps n'est pas conservé. */
@Entity
@Table(name = "email_log")
@Getter
@Setter
public class EmailLog extends TenantScopedEntity {

    /** Null pour les e-mails de compte envoyés hors contexte workspace. */
    @Column(name = "workspace_id")
    private UUID workspaceId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private EmailKind kind;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private EmailStatus status = EmailStatus.QUEUED;

    @Column(name = "from_email", nullable = false)
    private String fromEmail;

    @Column(name = "to_email", nullable = false)
    private String toEmail;

    @Column(nullable = false, length = 512)
    private String subject;

    /** Facture, devis… à l'origine de l'envoi (traçabilité). */
    @Column(name = "related_id")
    private UUID relatedId;

    /** Expéditeur utilisé : WORKSPACE (SMTP du client) ou PLATFORM (boîte éditeur). */
    @Column(name = "sender_source", nullable = false, length = 16)
    private String senderSource;

    @Column(nullable = false)
    private int attempts = 0;

    @Column(name = "sent_at")
    private Instant sentAt;

    @Column(name = "last_error", length = 1024)
    private String lastError;
}
