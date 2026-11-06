package com.nerd.erp.application.mail;

import com.nerd.erp.api.error.ApiException;
import com.nerd.erp.domain.mail.EmailKind;
import com.nerd.erp.domain.mail.EmailLog;
import com.nerd.erp.domain.mail.EmailStatus;
import com.nerd.erp.domain.mail.WorkspaceMailSettings;
import com.nerd.erp.infrastructure.config.mail.MailProperties;
import com.nerd.erp.infrastructure.mail.MimeMessageComposer;
import com.nerd.erp.infrastructure.mail.SmtpSenderFactory;
import com.nerd.erp.infrastructure.mail.SmtpSenderFactory.ResolvedSender;
import com.nerd.erp.infrastructure.persistence.mail.EmailLogRepository;
import com.nerd.erp.infrastructure.persistence.mail.WorkspaceMailSettingsRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Point d'entrée pour émettre un e-mail. Choix de l'expéditeur :
 * <ol>
 *   <li>e-mail métier (devis, facture, relance) avec workspace : SMTP du workspace s'il est configuré
 *       et activé, sinon boîte plateforme ;</li>
 *   <li>e-mail de compte : boîte plateforme.</li>
 * </ol>
 * Chaque envoi est journalisé dans {@code email_log} puis expédié en arrière-plan.
 */
@Service
public class MailService {

    public static final String MAIL_NOT_CONFIGURED = "MAIL_NOT_CONFIGURED";

    private final MailProperties properties;
    private final SmtpSenderFactory senderFactory;
    private final WorkspaceMailSettingsRepository settingsRepository;
    private final EmailLogRepository emailLogRepository;
    private final EmailDispatcher dispatcher;

    public MailService(MailProperties properties,
                       SmtpSenderFactory senderFactory,
                       WorkspaceMailSettingsRepository settingsRepository,
                       EmailLogRepository emailLogRepository,
                       EmailDispatcher dispatcher) {
        this.properties = properties;
        this.senderFactory = senderFactory;
        this.settingsRepository = settingsRepository;
        this.emailLogRepository = emailLogRepository;
        this.dispatcher = dispatcher;
    }

    public String appBaseUrl() {
        String url = properties.appBaseUrl();
        return url == null || url.isBlank() ? "http://localhost:4200" : url.replaceAll("/+$", "");
    }

    /** La boîte plateforme (e-mails de compte : invitations, mots de passe) est-elle configurée ? */
    public boolean platformSenderAvailable() {
        return senderFactory.platform() != null;
    }

    /** Un expéditeur est-il disponible pour ce workspace (SMTP propre ou boîte plateforme) ? */
    @Transactional(readOnly = true)
    public boolean canSendFrom(UUID tenantId, UUID workspaceId) {
        return resolve(tenantId, workspaceId, EmailKind.QUOTATION) != null;
    }

    /** Journalise puis envoie en arrière-plan. Retourne l'identifiant du journal. */
    @Transactional
    public UUID send(OutgoingEmail email) {
        ResolvedSender sender = resolve(email.tenantId(), email.workspaceId(), email.kind());
        if (sender == null) {
            throw new ApiException(HttpStatus.CONFLICT, MAIL_NOT_CONFIGURED,
                    "Aucune boîte d'envoi configurée. Renseignez la messagerie du workspace (Paramètres → Messagerie).");
        }
        EmailLog entry = emailLogRepository.save(EmailDispatcher.newLog(email, sender));
        dispatcher.dispatch(entry.getId(), sender, email);
        return entry.getId();
    }

    /** Envoi synchrone de test avec des paramètres donnés (enregistrés ou non). */
    public void sendTestWith(ResolvedSender sender, OutgoingEmail email) {
        EmailLog entry = EmailDispatcher.newLog(email, sender);
        try {
            MimeMessageComposer.send(sender, email);
            entry.setStatus(EmailStatus.SENT);
            entry.setAttempts(1);
            entry.setSentAt(Instant.now());
        } catch (Exception e) {
            entry.setStatus(EmailStatus.FAILED);
            entry.setAttempts(1);
            entry.setLastError(MailErrors.truncate(MailErrors.describe(e)));
            emailLogRepository.save(entry);
            throw new ApiException(HttpStatus.BAD_GATEWAY, "MAIL_TEST_FAILED", MailErrors.describe(e));
        }
        emailLogRepository.save(entry);
    }

    private ResolvedSender resolve(UUID tenantId, UUID workspaceId, EmailKind kind) {
        if (workspaceId != null && kind != EmailKind.ACCOUNT) {
            Optional<WorkspaceMailSettings> settings = settingsRepository.findByWorkspaceIdAndTenantId(workspaceId, tenantId)
                    .filter(WorkspaceMailSettings::isEnabled);
            if (settings.isPresent()) {
                return senderFactory.forWorkspace(settings.get());
            }
        }
        return senderFactory.platform();
    }
}
