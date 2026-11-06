package com.nerd.erp.application.document;

import com.nerd.erp.api.document.DocumentEmailRequest;
import com.nerd.erp.api.document.DocumentEmailResponse;
import com.nerd.erp.api.error.ApiException;
import com.nerd.erp.application.audit.AuditService;
import com.nerd.erp.application.customer.CustomerService;
import com.nerd.erp.application.invoice.InvoiceService;
import com.nerd.erp.application.mail.MailService;
import com.nerd.erp.application.mail.MailTemplates;
import com.nerd.erp.application.mail.OutgoingEmail;
import com.nerd.erp.application.quotation.QuotationPublicLinkService;
import com.nerd.erp.application.quotation.QuotationService;
import com.nerd.erp.application.workspace.WorkspaceService;
import com.nerd.erp.domain.customer.Customer;
import com.nerd.erp.domain.invoice.Invoice;
import com.nerd.erp.domain.invoice.InvoiceStatus;
import com.nerd.erp.domain.mail.EmailKind;
import com.nerd.erp.domain.quotation.Quotation;
import com.nerd.erp.domain.quotation.QuotationStatus;
import com.nerd.erp.domain.workspace.Workspace;
import com.nerd.erp.infrastructure.persistence.invoice.InvoiceRepository;
import com.nerd.erp.infrastructure.persistence.quotation.QuotationRepository;
import com.nerd.erp.infrastructure.security.core.SecurityContextHelper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Envoi des documents commerciaux au client (devis, facture, relance) avec le PDF en pièce jointe,
 * depuis la boîte du workspace si elle est configurée, sinon la boîte plateforme.
 */
@Service
public class DocumentMailService {

    public static final String CUSTOMER_EMAIL_MISSING = "CUSTOMER_EMAIL_MISSING";
    public static final String DOCUMENT_NOT_SENDABLE = "DOCUMENT_NOT_SENDABLE";

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final QuotationService quotationService;
    private final QuotationRepository quotationRepository;
    private final QuotationPdfService quotationPdfService;
    private final QuotationPublicLinkService publicLinkService;
    private final InvoiceService invoiceService;
    private final InvoiceRepository invoiceRepository;
    private final InvoicePdfService invoicePdfService;
    private final CustomerService customerService;
    private final WorkspaceService workspaceService;
    private final MailService mailService;
    private final AuditService auditService;

    public DocumentMailService(QuotationService quotationService,
                               QuotationRepository quotationRepository,
                               QuotationPdfService quotationPdfService,
                               QuotationPublicLinkService publicLinkService,
                               InvoiceService invoiceService,
                               InvoiceRepository invoiceRepository,
                               InvoicePdfService invoicePdfService,
                               CustomerService customerService,
                               WorkspaceService workspaceService,
                               MailService mailService,
                               AuditService auditService) {
        this.quotationService = quotationService;
        this.quotationRepository = quotationRepository;
        this.quotationPdfService = quotationPdfService;
        this.publicLinkService = publicLinkService;
        this.invoiceService = invoiceService;
        this.invoiceRepository = invoiceRepository;
        this.invoicePdfService = invoicePdfService;
        this.customerService = customerService;
        this.workspaceService = workspaceService;
        this.mailService = mailService;
        this.auditService = auditService;
    }

    /**
     * Envoie le devis au client avec le lien d'acceptation en ligne. Un brouillon est d'abord
     * émis (numérotation, statut SENT) ; un devis déjà envoyé peut être renvoyé.
     */
    @Transactional
    public DocumentEmailResponse sendQuotation(UUID quotationId, DocumentEmailRequest request) {
        SecurityContextHelper.requireWriteAccess();
        Quotation quotation = quotationService.requireQuotation(quotationId);
        if (quotation.getStatus() == QuotationStatus.DRAFT) {
            quotationService.send(quotationId);
            quotation = quotationService.requireQuotation(quotationId);
        }
        if (quotation.getStatus() != QuotationStatus.SENT) {
            throw new ApiException(HttpStatus.CONFLICT, DOCUMENT_NOT_SENDABLE,
                    "Seul un devis envoyé (non encore accepté, refusé ou converti) peut être transmis au client");
        }
        Workspace workspace = workspaceService.requireInTenant(quotation.getWorkspaceId(), quotation.getTenantId());
        Customer customer = customerService.requireCustomer(quotation.getCustomerId());
        String to = resolveRecipient(request, customer);
        requireSender(quotation.getTenantId(), quotation.getWorkspaceId());

        String publicUrl = publicLinkService.issueLink(quotation);
        GeneratedDocument pdf = quotationPdfService.generate(quotation, customer);

        String title = "Devis " + quotation.getNumber() + " — " + workspace.getName();
        String validity = quotation.getValidUntil() == null
                ? ""
                : " Il est valable jusqu'au " + DATE_FMT.format(quotation.getValidUntil()) + ".";
        String intro = "Veuillez trouver ci-joint notre devis n° " + quotation.getNumber() + " d'un montant de "
                + money(quotation.getTotalTtc(), workspace.getCurrencyCode()) + " TTC." + validity;
        String html = MailTemplates.layout(title,
                MailTemplates.paragraph(greeting(customer))
                        + personalMessage(request)
                        + MailTemplates.paragraph(intro)
                        + MailTemplates.paragraph("Vous pouvez consulter, accepter ou refuser ce devis en ligne :")
                        + MailTemplates.button("Consulter et répondre au devis", publicUrl)
                        + MailTemplates.paragraph("Nous restons à votre disposition pour toute question.")
                        + MailTemplates.paragraph("Cordialement,\n" + workspace.getName()),
                footer(workspace));
        String text = greeting(customer) + "\n\n" + personalMessageText(request) + intro
                + "\n\nConsulter et répondre au devis : " + publicUrl + "\n\nCordialement,\n" + workspace.getName() + "\n";

        OutgoingEmail email = OutgoingEmail.of(EmailKind.QUOTATION, quotation.getTenantId(), quotation.getWorkspaceId(),
                        to, title, html, text)
                .withAttachment(new OutgoingEmail.Attachment(pdf.filename(), "application/pdf", pdf.content()))
                .relatedTo(quotation.getId());
        dispatch(email, request);

        Instant now = Instant.now();
        quotation.setSentAt(now);
        quotationRepository.save(quotation);
        auditService.record(quotation.getTenantId(), "Quotation", quotation.getId(), "EMAIL_SENT",
                Map.of("to", to, "cc", request == null || request.cc() == null ? "" : request.cc()));
        return new DocumentEmailResponse(to, now);
    }

    /** Envoie une facture ou un avoir émis, PDF joint. */
    @Transactional
    public DocumentEmailResponse sendInvoice(UUID invoiceId, DocumentEmailRequest request) {
        SecurityContextHelper.requireWriteAccess();
        Invoice invoice = invoiceService.requireInvoice(invoiceId);
        if (invoice.getStatus() == InvoiceStatus.DRAFT || invoice.getIssuedAt() == null) {
            throw new ApiException(HttpStatus.CONFLICT, DOCUMENT_NOT_SENDABLE, "La facture n'est pas émise");
        }
        Workspace workspace = workspaceService.requireInTenant(invoice.getWorkspaceId(), invoice.getTenantId());
        Customer customer = customerService.requireCustomer(invoice.getCustomerId());
        String to = resolveRecipient(request, customer);
        requireSender(invoice.getTenantId(), invoice.getWorkspaceId());

        GeneratedDocument pdf = invoicePdfService.generate(invoice);
        boolean creditNote = invoice.isCreditNote();
        String label = creditNote ? "Avoir" : "Facture";
        String title = label + " " + invoice.getNumber() + " — " + workspace.getName();
        String intro = creditNote
                ? "Veuillez trouver ci-joint notre avoir n° " + invoice.getNumber() + " d'un montant de "
                + money(invoice.getTotalTtc().abs(), workspace.getCurrencyCode()) + " TTC."
                : "Veuillez trouver ci-joint notre facture n° " + invoice.getNumber() + " d'un montant de "
                + money(invoice.getTotalTtc(), workspace.getCurrencyCode()) + " TTC, payable avant le "
                + DATE_FMT.format(invoice.getDueDate()) + ".";
        String body = MailTemplates.paragraph(greeting(customer))
                + personalMessage(request)
                + MailTemplates.paragraph(intro)
                + bankBlock(workspace, creditNote)
                + MailTemplates.paragraph("Nous vous remercions de votre confiance.")
                + MailTemplates.paragraph("Cordialement,\n" + workspace.getName());
        String text = greeting(customer) + "\n\n" + personalMessageText(request) + intro
                + bankText(workspace, creditNote) + "\n\nCordialement,\n" + workspace.getName() + "\n";

        OutgoingEmail email = OutgoingEmail.of(EmailKind.INVOICE, invoice.getTenantId(), invoice.getWorkspaceId(),
                        to, title, MailTemplates.layout(title, body, footer(workspace)), text)
                .withAttachment(new OutgoingEmail.Attachment(pdf.filename(), "application/pdf", pdf.content()))
                .relatedTo(invoice.getId());
        dispatch(email, request);

        Instant now = Instant.now();
        invoice.setSentAt(now);
        invoiceRepository.save(invoice);
        auditService.record(invoice.getTenantId(), "Invoice", invoice.getId(), "EMAIL_SENT",
                Map.of("to", to, "cc", request == null || request.cc() == null ? "" : request.cc()));
        return new DocumentEmailResponse(to, now);
    }

    /** Relance manuelle d'une facture échue ou en attente. */
    @Transactional
    public DocumentEmailResponse sendReminder(UUID invoiceId, DocumentEmailRequest request) {
        SecurityContextHelper.requireWriteAccess();
        Invoice invoice = invoiceService.requireInvoice(invoiceId);
        if (invoice.isCreditNote()
                || (invoice.getStatus() != InvoiceStatus.ISSUED && invoice.getStatus() != InvoiceStatus.PARTIALLY_PAID)) {
            throw new ApiException(HttpStatus.CONFLICT, DOCUMENT_NOT_SENDABLE,
                    "Seule une facture émise et non soldée peut être relancée");
        }
        Workspace workspace = workspaceService.requireInTenant(invoice.getWorkspaceId(), invoice.getTenantId());
        Customer customer = customerService.requireCustomer(invoice.getCustomerId());
        String to = resolveRecipient(request, customer);
        requireSender(invoice.getTenantId(), invoice.getWorkspaceId());
        return sendReminderInternal(invoice, workspace, customer, to, request, "MANUAL");
    }

    /**
     * Cœur de la relance, partagé avec le planificateur (pas de contrôle de session ici :
     * l'appelant a déjà validé le périmètre).
     */
    @Transactional
    public DocumentEmailResponse sendReminderInternal(Invoice invoice, Workspace workspace, Customer customer,
                                                      String to, DocumentEmailRequest request, String trigger) {
        GeneratedDocument pdf = invoicePdfService.generate(invoice);
        long overdueDays = Math.max(0, LocalDate.now().toEpochDay() - invoice.getDueDate().toEpochDay());
        int sequence = invoice.getReminderCount() + 1;
        String title = (sequence > 1 ? "Rappel n° " + sequence + " — " : "Rappel — ") + "Facture " + invoice.getNumber()
                + " — " + workspace.getName();
        String remaining = money(invoice.remainingAmount(), workspace.getCurrencyCode());
        String intro = overdueDays > 0
                ? "Sauf erreur de notre part, la facture n° " + invoice.getNumber() + " du "
                + DATE_FMT.format(invoice.getIssueDate()) + ", échue le " + DATE_FMT.format(invoice.getDueDate())
                + " (depuis " + overdueDays + " jour" + (overdueDays > 1 ? "s" : "") + "), reste à régler pour un montant de "
                + remaining + " TTC."
                : "Nous nous permettons de vous rappeler que la facture n° " + invoice.getNumber()
                + ", à échéance du " + DATE_FMT.format(invoice.getDueDate()) + ", reste à régler pour un montant de "
                + remaining + " TTC.";
        String closing = "Si votre règlement a déjà été effectué, nous vous prions de ne pas tenir compte de ce message. "
                + "Dans le cas contraire, nous vous remercions de bien vouloir procéder au paiement dans les meilleurs délais.";
        String body = MailTemplates.paragraph(greeting(customer))
                + personalMessage(request)
                + MailTemplates.paragraph(intro)
                + bankBlock(workspace, false)
                + MailTemplates.paragraph(closing)
                + MailTemplates.paragraph("Cordialement,\n" + workspace.getName());
        String text = greeting(customer) + "\n\n" + personalMessageText(request) + intro
                + bankText(workspace, false) + "\n\n" + closing + "\n\nCordialement,\n" + workspace.getName() + "\n";

        OutgoingEmail email = OutgoingEmail.of(EmailKind.REMINDER, invoice.getTenantId(), invoice.getWorkspaceId(),
                        to, title, MailTemplates.layout(title, body, footer(workspace)), text)
                .withAttachment(new OutgoingEmail.Attachment(pdf.filename(), "application/pdf", pdf.content()))
                .relatedTo(invoice.getId());
        dispatch(email, request);

        Instant now = Instant.now();
        invoice.setReminderCount(sequence);
        invoice.setLastReminderAt(now);
        invoiceRepository.save(invoice);
        auditService.record(invoice.getTenantId(), "Invoice", invoice.getId(), "REMINDER_SENT",
                Map.of("to", to, "sequence", sequence, "trigger", trigger, "overdueDays", overdueDays));
        return new DocumentEmailResponse(to, now);
    }

    // --- helpers ---

    private void requireSender(UUID tenantId, UUID workspaceId) {
        if (!mailService.canSendFrom(tenantId, workspaceId)) {
            throw new ApiException(HttpStatus.CONFLICT, MailService.MAIL_NOT_CONFIGURED,
                    "Aucune boîte d'envoi configurée. Renseignez la messagerie du workspace (Paramètres → Messagerie).");
        }
    }

    static String resolveRecipient(DocumentEmailRequest request, Customer customer) {
        String to = request == null || request.to() == null || request.to().isBlank()
                ? customer.getEmail()
                : request.to().trim();
        if (to == null || to.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, CUSTOMER_EMAIL_MISSING,
                    "Le client n'a pas d'adresse e-mail : renseignez-la sur sa fiche ou indiquez un destinataire.");
        }
        return to;
    }

    /** Le transport ne gère qu'un destinataire par message : la copie reçoit un second envoi identique. */
    private void dispatch(OutgoingEmail email, DocumentEmailRequest request) {
        mailService.send(email);
        if (request != null && request.cc() != null && !request.cc().isBlank()
                && !request.cc().trim().equalsIgnoreCase(email.to())) {
            mailService.send(new OutgoingEmail(email.kind(), email.tenantId(), email.workspaceId(), request.cc().trim(),
                    email.subject(), email.html(), email.text(), email.attachments(), email.relatedId()));
        }
    }

    static String greeting(Customer customer) {
        String person = join(customer.getFirstName(), customer.getLastName());
        return person.isBlank() ? "Bonjour," : "Bonjour " + person + ",";
    }

    private static String personalMessage(DocumentEmailRequest request) {
        if (request == null || request.message() == null || request.message().isBlank()) {
            return "";
        }
        return MailTemplates.rawParagraph(MailTemplates.esc(request.message().trim()).replace("\n", "<br>"));
    }

    private static String personalMessageText(DocumentEmailRequest request) {
        if (request == null || request.message() == null || request.message().isBlank()) {
            return "";
        }
        return request.message().trim() + "\n\n";
    }

    private static String bankBlock(Workspace w, boolean creditNote) {
        if (creditNote || w.getIban() == null || w.getIban().isBlank()) {
            return "";
        }
        String inner = "<strong>Règlement par virement</strong><br>IBAN : " + MailTemplates.esc(w.getIban())
                + (w.getBic() == null || w.getBic().isBlank() ? "" : "<br>BIC : " + MailTemplates.esc(w.getBic()));
        return MailTemplates.rawParagraph(inner);
    }

    private static String bankText(Workspace w, boolean creditNote) {
        if (creditNote || w.getIban() == null || w.getIban().isBlank()) {
            return "";
        }
        return "\n\nRèglement par virement — IBAN : " + w.getIban()
                + (w.getBic() == null || w.getBic().isBlank() ? "" : " — BIC : " + w.getBic());
    }

    static String footer(Workspace w) {
        StringBuilder sb = new StringBuilder(w.getName());
        if (w.getSiren() != null && !w.getSiren().isBlank()) {
            sb.append(" — SIREN ").append(w.getSiren());
        }
        if (w.getContactEmail() != null && !w.getContactEmail().isBlank()) {
            sb.append(" — ").append(w.getContactEmail());
        }
        return sb.toString();
    }

    static String money(BigDecimal amount, String currency) {
        DecimalFormatSymbols symbols = DecimalFormatSymbols.getInstance(Locale.FRANCE);
        DecimalFormat fmt = new DecimalFormat("#,##0.00", symbols);
        String value = fmt.format((amount == null ? BigDecimal.ZERO : amount).setScale(2, RoundingMode.HALF_UP));
        return value + " " + ("EUR".equalsIgnoreCase(currency) || currency == null ? "€" : currency);
    }

    private static String join(String a, String b) {
        return ((a == null ? "" : a.trim()) + " " + (b == null ? "" : b.trim())).trim();
    }
}
