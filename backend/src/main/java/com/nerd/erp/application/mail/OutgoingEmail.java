package com.nerd.erp.application.mail;

import com.nerd.erp.domain.mail.EmailKind;

import java.util.List;
import java.util.UUID;

/** E-mail à émettre, indépendant du transport (SMTP du workspace ou boîte plateforme). */
public record OutgoingEmail(
        EmailKind kind,
        UUID tenantId,
        /** Null pour un e-mail de compte hors workspace (expéditeur plateforme). */
        UUID workspaceId,
        String to,
        String subject,
        String html,
        String text,
        List<Attachment> attachments,
        /** Identifiant métier lié (facture, devis…), pour le journal. */
        UUID relatedId
) {
    public record Attachment(String filename, String contentType, byte[] content) {
    }

    public OutgoingEmail {
        attachments = attachments == null ? List.of() : List.copyOf(attachments);
    }

    public static OutgoingEmail of(EmailKind kind, UUID tenantId, UUID workspaceId, String to, String subject,
                                   String html, String text) {
        return new OutgoingEmail(kind, tenantId, workspaceId, to, subject, html, text, List.of(), null);
    }

    public OutgoingEmail withAttachment(Attachment attachment) {
        List<Attachment> list = new java.util.ArrayList<>(attachments);
        list.add(attachment);
        return new OutgoingEmail(kind, tenantId, workspaceId, to, subject, html, text, list, relatedId);
    }

    public OutgoingEmail relatedTo(UUID id) {
        return new OutgoingEmail(kind, tenantId, workspaceId, to, subject, html, text, attachments, id);
    }
}
