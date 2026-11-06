package com.nerd.erp.application.mail;

import com.nerd.erp.domain.mail.EmailLog;
import com.nerd.erp.domain.mail.EmailStatus;
import com.nerd.erp.infrastructure.mail.MimeMessageComposer;
import com.nerd.erp.infrastructure.mail.SmtpSenderFactory;
import com.nerd.erp.infrastructure.persistence.mail.EmailLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

/**
 * Envoi effectif en arrière-plan avec quelques tentatives espacées (erreurs réseau / SMTP transitoires),
 * puis mise à jour du journal. Bean séparé pour que {@link Async} s'applique via le proxy.
 */
@Component
public class EmailDispatcher {

    private static final Logger log = LoggerFactory.getLogger(EmailDispatcher.class);
    static final int MAX_ATTEMPTS = 3;
    private static final long[] BACKOFF_MS = {0, 3_000, 15_000};

    private final EmailLogRepository emailLogRepository;

    public EmailDispatcher(EmailLogRepository emailLogRepository) {
        this.emailLogRepository = emailLogRepository;
    }

    @Async("mailExecutor")
    public void dispatch(UUID logId, SmtpSenderFactory.ResolvedSender sender, OutgoingEmail email) {
        String lastError = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                if (BACKOFF_MS[attempt - 1] > 0) {
                    Thread.sleep(BACKOFF_MS[attempt - 1]);
                }
                MimeMessageComposer.send(sender, email);
                markSent(logId, attempt);
                return;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                lastError = "Interrompu";
                break;
            } catch (Exception e) {
                lastError = MailErrors.describe(e);
                log.warn("E-mail {} tentative {}/{} échouée vers {} : {}", logId, attempt, MAX_ATTEMPTS,
                        email.to(), lastError);
                if (!MailErrors.isRetryable(e)) {
                    break;
                }
            }
        }
        markFailed(logId, lastError);
    }

    private void markSent(UUID logId, int attempts) {
        emailLogRepository.findById(logId).ifPresent(entry -> {
            entry.setStatus(EmailStatus.SENT);
            entry.setAttempts(attempts);
            entry.setSentAt(Instant.now());
            entry.setLastError(null);
            emailLogRepository.save(entry);
        });
    }

    private void markFailed(UUID logId, String error) {
        emailLogRepository.findById(logId).ifPresent(entry -> {
            entry.setStatus(EmailStatus.FAILED);
            entry.setAttempts(Math.max(entry.getAttempts(), 1));
            entry.setLastError(MailErrors.truncate(error));
            emailLogRepository.save(entry);
        });
    }

    /** Pour les cas synchrones (test SMTP). */
    public static EmailLog newLog(OutgoingEmail email, SmtpSenderFactory.ResolvedSender sender) {
        EmailLog entry = new EmailLog();
        entry.setTenantId(email.tenantId());
        entry.setWorkspaceId(email.workspaceId());
        entry.setKind(email.kind());
        entry.setStatus(EmailStatus.QUEUED);
        entry.setFromEmail(sender.fromEmail());
        entry.setToEmail(email.to());
        entry.setSubject(MailErrors.truncate(email.subject(), 512));
        entry.setRelatedId(email.relatedId());
        entry.setSenderSource(sender.source());
        return entry;
    }
}
