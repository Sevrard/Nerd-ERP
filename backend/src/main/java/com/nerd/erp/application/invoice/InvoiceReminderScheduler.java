package com.nerd.erp.application.invoice;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Passe quotidienne de relances automatiques des factures échues. Chaque envoi est isolé
 * (transaction propre, erreur journalisée) : un client sans e-mail ou un SMTP absent ne bloque pas les autres.
 */
@Component
public class InvoiceReminderScheduler {

    private static final Logger log = LoggerFactory.getLogger(InvoiceReminderScheduler.class);

    private final InvoiceReminderService reminderService;
    private final boolean enabled;

    public InvoiceReminderScheduler(InvoiceReminderService reminderService,
                                    @Value("${nerd.reminders.enabled:true}") boolean enabled) {
        this.reminderService = reminderService;
        this.enabled = enabled;
    }

    @Scheduled(cron = "${nerd.reminders.cron:0 0 8 * * *}")
    public void runDaily() {
        if (!enabled) {
            return;
        }
        int sent = run(LocalDate.now());
        if (sent > 0) {
            log.info("Relances automatiques : {} e-mail(s) envoyé(s)", sent);
        }
    }

    /** Exécute une passe pour la date donnée ; retourne le nombre d'envois. */
    public int run(LocalDate today) {
        int sent = 0;
        for (UUID invoiceId : reminderService.candidateIds(today)) {
            try {
                if (reminderService.remindIfDue(invoiceId, today)) {
                    sent++;
                }
            } catch (RuntimeException e) {
                log.warn("Relance automatique en échec pour la facture {} : {}", invoiceId, e.getMessage());
            }
        }
        return sent;
    }
}
