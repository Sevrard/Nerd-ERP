package com.nerd.erp.application.invoice;

import com.nerd.erp.application.document.DocumentMailService;
import com.nerd.erp.application.mail.MailService;
import com.nerd.erp.domain.customer.Customer;
import com.nerd.erp.domain.invoice.Invoice;
import com.nerd.erp.domain.invoice.InvoiceStatus;
import com.nerd.erp.domain.workspace.Workspace;
import com.nerd.erp.infrastructure.persistence.customer.CustomerRepository;
import com.nerd.erp.infrastructure.persistence.invoice.InvoiceRepository;
import com.nerd.erp.infrastructure.persistence.workspace.WorkspaceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

/**
 * Règles des relances automatiques (workspace opt-in) : 1ʳᵉ relance à J+7 après l'échéance, 2ᵉ à J+15,
 * jamais plus de deux automatiques, délai minimal entre deux relances (manuelles comprises).
 * Chaque facture est traitée dans sa propre transaction.
 */
@Service
public class InvoiceReminderService {

    private static final Logger log = LoggerFactory.getLogger(InvoiceReminderService.class);

    static final int FIRST_REMINDER_DAYS = 7;
    static final int SECOND_REMINDER_DAYS = 15;
    static final int MAX_AUTOMATIC_REMINDERS = 2;
    /** Délai minimal entre deux relances (évite un doublon le jour d'une relance manuelle). */
    static final int MIN_GAP_DAYS = 5;

    private final InvoiceRepository invoiceRepository;
    private final WorkspaceRepository workspaceRepository;
    private final CustomerRepository customerRepository;
    private final DocumentMailService documentMailService;
    private final MailService mailService;

    public InvoiceReminderService(InvoiceRepository invoiceRepository,
                                  WorkspaceRepository workspaceRepository,
                                  CustomerRepository customerRepository,
                                  DocumentMailService documentMailService,
                                  MailService mailService) {
        this.invoiceRepository = invoiceRepository;
        this.workspaceRepository = workspaceRepository;
        this.customerRepository = customerRepository;
        this.documentMailService = documentMailService;
        this.mailService = mailService;
    }

    /** Identifiants des factures candidates (échues depuis au moins J+7, moins de 2 relances). */
    @Transactional(readOnly = true)
    public List<UUID> candidateIds(LocalDate today) {
        return invoiceRepository.findOverdueForReminders(today.minusDays(FIRST_REMINDER_DAYS), MAX_AUTOMATIC_REMINDERS)
                .stream()
                .map(Invoice::getId)
                .toList();
    }

    /** Relance la facture si toutes les conditions sont réunies ; retourne {@code true} si un e-mail est parti. */
    @Transactional
    public boolean remindIfDue(UUID invoiceId, LocalDate today) {
        Invoice invoice = invoiceRepository.findById(invoiceId).orElse(null);
        if (invoice == null || invoice.isCreditNote()
                || (invoice.getStatus() != InvoiceStatus.ISSUED && invoice.getStatus() != InvoiceStatus.PARTIALLY_PAID)) {
            return false;
        }
        Workspace workspace = workspaceRepository.findByIdAndTenantId(invoice.getWorkspaceId(), invoice.getTenantId())
                .orElse(null);
        if (workspace == null || !workspace.isActive() || !workspace.isRemindersEnabled() || !due(invoice, today)) {
            return false;
        }
        Customer customer = customerRepository.findByIdAndTenantId(invoice.getCustomerId(), invoice.getTenantId())
                .orElse(null);
        if (customer == null || customer.getEmail() == null || customer.getEmail().isBlank()) {
            log.debug("Relance ignorée (client sans e-mail) : facture {}", invoice.getNumber());
            return false;
        }
        if (!mailService.canSendFrom(invoice.getTenantId(), invoice.getWorkspaceId())) {
            log.debug("Relance ignorée (aucune boîte d'envoi) : facture {}", invoice.getNumber());
            return false;
        }
        documentMailService.sendReminderInternal(invoice, workspace, customer, customer.getEmail(), null, "AUTO");
        return true;
    }

    static boolean due(Invoice invoice, LocalDate today) {
        long overdue = ChronoUnit.DAYS.between(invoice.getDueDate(), today);
        int count = invoice.getReminderCount();
        boolean threshold = (count == 0 && overdue >= FIRST_REMINDER_DAYS)
                || (count == 1 && overdue >= SECOND_REMINDER_DAYS);
        if (!threshold) {
            return false;
        }
        Instant last = invoice.getLastReminderAt();
        if (last == null) {
            return true;
        }
        Instant startOfToday = today.atStartOfDay(ZoneId.systemDefault()).toInstant();
        return ChronoUnit.DAYS.between(last, startOfToday) >= MIN_GAP_DAYS;
    }
}
