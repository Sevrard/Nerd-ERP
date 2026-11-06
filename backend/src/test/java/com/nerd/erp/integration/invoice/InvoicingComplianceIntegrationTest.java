package com.nerd.erp.integration.invoice;

import com.icegreen.greenmail.junit5.GreenMailExtension;
import com.icegreen.greenmail.util.GreenMailUtil;
import com.icegreen.greenmail.util.ServerSetup;
import com.nerd.erp.api.auth.LoginRequest;
import com.nerd.erp.api.auth.LoginResponse;
import com.nerd.erp.api.customer.CustomerRequest;
import com.nerd.erp.api.customer.CustomerResponse;
import com.nerd.erp.api.document.DocumentEmailRequest;
import com.nerd.erp.api.document.DocumentEmailResponse;
import com.nerd.erp.api.invoice.CreditNoteRequest;
import com.nerd.erp.api.invoice.InvoiceResponse;
import com.nerd.erp.api.mail.MailDtos.WorkspaceMailSettingsRequest;
import com.nerd.erp.api.quotation.DocumentLineRequest;
import com.nerd.erp.api.quotation.PublicQuotationDtos.AcceptRequest;
import com.nerd.erp.api.quotation.PublicQuotationDtos.PublicQuotationView;
import com.nerd.erp.api.quotation.QuotationRequest;
import com.nerd.erp.api.quotation.QuotationResponse;
import com.nerd.erp.api.workspace.WorkspaceCreateRequest;
import com.nerd.erp.api.workspace.WorkspaceResponse;
import com.nerd.erp.api.workspace.WorkspaceUpdateRequest;
import com.nerd.erp.application.invoice.InvoiceReminderScheduler;
import com.nerd.erp.domain.customer.CustomerStatus;
import com.nerd.erp.domain.customer.CustomerType;
import com.nerd.erp.domain.invoice.Invoice;
import com.nerd.erp.domain.invoice.InvoiceStatus;
import com.nerd.erp.domain.invoice.InvoiceType;
import com.nerd.erp.domain.mail.SmtpSecurity;
import com.nerd.erp.domain.quotation.QuotationDecisionSource;
import com.nerd.erp.domain.quotation.QuotationStatus;
import com.nerd.erp.domain.workspace.VatRegime;
import com.nerd.erp.infrastructure.persistence.invoice.InvoiceRepository;
import com.nerd.erp.support.PostgresIntegrationTest;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Lot 3 : facturation conforme. Mentions légales par workspace, envoi du devis par e-mail avec lien
 * d'acceptation en ligne, inaltérabilité de la facture émise, avoir, relance, export comptable.
 */
class InvoicingComplianceIntegrationTest extends PostgresIntegrationTest {

    @RegisterExtension
    static GreenMailExtension greenMail = new GreenMailExtension(new ServerSetup(0, null, ServerSetup.PROTOCOL_SMTP))
            .withPerMethodLifecycle(false);

    private static final Pattern PUBLIC_LINK = Pattern.compile("https?://[^\\s\"<]+/q/([A-Za-z0-9_-]+)");

    @Autowired
    TestRestTemplate restTemplate;
    @Autowired
    InvoiceRepository invoiceRepository;
    @Autowired
    InvoiceReminderScheduler reminderScheduler;

    String token;
    UUID workspaceId;
    UUID customerId;

    @BeforeEach
    void setUp() {
        LoginResponse login = restTemplate.postForObject("/api/v1/auth/login",
                new LoginRequest("admin@nerd.local", "Admin123!", "test-tenant"), LoginResponse.class);
        token = login.accessToken();

        // Workspace dédié : séquences et export isolés des autres tests.
        WorkspaceResponse ws = restTemplate.postForObject("/api/v1/workspaces",
                new HttpEntity<>(new WorkspaceCreateRequest("Compliance " + suffix(), null, null, "EUR", null, null),
                        headers()),
                WorkspaceResponse.class);
        workspaceId = ws.id();

        greenMail.setUser("compta@acme.test", "compta@acme.test", "app-password");
        ResponseEntity<String> smtp = restTemplate.exchange("/api/v1/workspaces/" + workspaceId + "/mail/settings",
                HttpMethod.PUT,
                new HttpEntity<>(new WorkspaceMailSettingsRequest(true, "localhost", greenMail.getSmtp().getPort(),
                        SmtpSecurity.NONE, "compta@acme.test", "app-password", "compta@acme.test", "ACME", null),
                        headers()),
                String.class);
        assertEquals(HttpStatus.OK, smtp.getStatusCode(), smtp.getBody());

        CustomerRequest customerRequest = new CustomerRequest(
                workspaceId, CustomerType.COMPANY, "Client " + suffix(), "Jean", "Dupont",
                "jean.dupont@client.test", null, null, null, null, null, "FR", CustomerStatus.CLIENT, null, null);
        customerId = restTemplate.postForObject("/api/v1/customers",
                new HttpEntity<>(customerRequest, headers()), CustomerResponse.class).id();
    }

    @Test
    void legalSettingsEmailAcceptanceLockCreditNoteAndExport() throws Exception {
        // --- A. Mentions légales et conditions de règlement du workspace ---
        WorkspaceResponse before = restTemplate.exchange("/api/v1/workspaces/" + workspaceId, HttpMethod.GET,
                new HttpEntity<>(headers()), WorkspaceResponse.class).getBody();
        assertEquals(VatRegime.NORMAL, before.vatRegime());
        assertEquals(30, before.paymentTermsDays());
        assertEquals("AV", before.creditNotePrefix());

        WorkspaceUpdateRequest update = new WorkspaceUpdateRequest(before.name(), "SAS au capital de 1 000 €",
                "1 rue de la Paix", null, "75001", "Paris", null, "FR", "compta@acme.test", null,
                "123456789", "FR12345678901", "FR7630006000011234567890189", "AGRIFRPP", "EUR", "FA", "DE",
                "AVR", VatRegime.FRANCHISE_293B, 45, new BigDecimal("10.5"), new BigDecimal("40"),
                "2 % sous 8 jours", "Paiement à réception.", "RCS Paris 123 456 789", true, null, null);
        ResponseEntity<WorkspaceResponse> updated = restTemplate.exchange("/api/v1/workspaces/" + workspaceId,
                HttpMethod.PUT, new HttpEntity<>(update, headers()), WorkspaceResponse.class);
        assertEquals(HttpStatus.OK, updated.getStatusCode());
        assertEquals(VatRegime.FRANCHISE_293B, updated.getBody().vatRegime());
        assertEquals(45, updated.getBody().paymentTermsDays());
        assertEquals("AVR", updated.getBody().creditNotePrefix());
        assertEquals("2 % sous 8 jours", updated.getBody().earlyPaymentDiscount());
        assertTrue(updated.getBody().remindersEnabled());

        // --- D. Devis brouillon envoyé par e-mail : numérotation, statut SENT, PDF joint, lien public ---
        QuotationRequest quotationRequest = new QuotationRequest(
                workspaceId, customerId, LocalDate.now(), LocalDate.now().plusDays(30), "Prestation",
                BigDecimal.ZERO, BigDecimal.ZERO,
                List.of(new DocumentLineRequest(null, "Développement", new BigDecimal("2"),
                        new BigDecimal("500"), new BigDecimal("20"), null)));
        QuotationResponse draft = restTemplate.postForObject("/api/v1/quotations",
                new HttpEntity<>(quotationRequest, headers()), QuotationResponse.class);
        assertEquals(QuotationStatus.DRAFT, draft.status());

        ResponseEntity<DocumentEmailResponse> emailed = restTemplate.postForEntity(
                "/api/v1/quotations/" + draft.id() + "/email",
                new HttpEntity<>(new DocumentEmailRequest(null, null, "Merci de votre confiance."), headers()),
                DocumentEmailResponse.class);
        assertEquals(HttpStatus.OK, emailed.getStatusCode());
        assertEquals("jean.dupont@client.test", emailed.getBody().to());

        QuotationResponse sent = getQuotation(draft.id());
        assertEquals(QuotationStatus.SENT, sent.status());
        assertNotNull(sent.number());
        assertNotNull(sent.sentAt());
        assertTrue(sent.publicLinkActive());

        assertTrue(greenMail.waitForIncomingEmail(5000, 1));
        MimeMessage mail = lastMessageTo("jean.dupont@client.test");
        assertTrue(mail.getSubject().startsWith("Devis " + sent.number()));
        String raw = GreenMailUtil.getBody(mail);
        assertTrue(raw.contains("application/pdf"), "le PDF est joint");
        Matcher m = PUBLIC_LINK.matcher(textOf(mail));
        assertTrue(m.find(), "le lien public figure dans l'e-mail");
        String publicToken = m.group(1);

        // --- E. Parcours client sans session : consultation, PDF, acceptation ---
        String publicBase = "/api/v1/auth/public/quotations/" + publicToken;
        PublicQuotationView view = restTemplate.getForObject(publicBase, PublicQuotationView.class);
        assertEquals(sent.number(), view.number());
        assertTrue(view.decidable());
        assertEquals(0, new BigDecimal("1200").compareTo(view.totalTtc()));
        assertEquals(1, view.lines().size());

        ResponseEntity<byte[]> publicPdf = restTemplate.getForEntity(publicBase + "/pdf", byte[].class);
        assertEquals(HttpStatus.OK, publicPdf.getStatusCode());
        assertTrue(new String(publicPdf.getBody(), 0, 5, StandardCharsets.ISO_8859_1).startsWith("%PDF"));

        ResponseEntity<String> badToken = restTemplate.getForEntity("/api/v1/auth/public/quotations/nope",
                String.class);
        assertEquals(HttpStatus.NOT_FOUND, badToken.getStatusCode());

        PublicQuotationView accepted = restTemplate.postForObject(publicBase + "/accept",
                new AcceptRequest("Jean Dupont", "OK pour nous"), PublicQuotationView.class);
        assertEquals(QuotationStatus.ACCEPTED, accepted.status());
        assertFalse(accepted.decidable());
        assertEquals("Jean Dupont", accepted.decidedBy());

        ResponseEntity<String> twice = restTemplate.postForEntity(publicBase + "/accept",
                new AcceptRequest("Jean Dupont", null), String.class);
        assertEquals(HttpStatus.CONFLICT, twice.getStatusCode());

        QuotationResponse decided = getQuotation(draft.id());
        assertEquals(QuotationStatus.ACCEPTED, decided.status());
        assertEquals(QuotationDecisionSource.ONLINE, decided.decisionSource());
        assertEquals("OK pour nous", decided.decisionComment());

        // --- B. Facture émise : échéance selon le workspace, inaltérable ---
        InvoiceResponse invoice = restTemplate.postForObject(
                "/api/v1/quotations/" + draft.id() + "/convert-to-invoice",
                new HttpEntity<>(headers()), InvoiceResponse.class);
        assertEquals(InvoiceStatus.ISSUED, invoice.status());
        assertEquals(LocalDate.now().plusDays(45), invoice.dueDate(), "délai de paiement du workspace");

        ResponseEntity<String> delete = restTemplate.exchange("/api/v1/invoices/" + invoice.id(),
                HttpMethod.DELETE, new HttpEntity<>(headers()), String.class);
        assertEquals(HttpStatus.CONFLICT, delete.getStatusCode());
        assertTrue(delete.getBody().contains("INVOICE_LOCKED"));

        // PDF : mention de franchise en base et pénalités paramétrées.
        ResponseEntity<byte[]> pdf = restTemplate.exchange("/api/v1/documents/invoices/" + invoice.id() + "/download",
                HttpMethod.GET, new HttpEntity<>(headers()), byte[].class);
        assertEquals(HttpStatus.OK, pdf.getStatusCode());
        String pdfText = PdfText.extract(pdf.getBody());
        assertTrue(pdfText.contains("293 B"), "mention TVA non applicable attendue : " + pdfText);
        assertTrue(pdfText.contains("10,5"), "taux de pénalité paramétré attendu");
        assertTrue(pdfText.contains("2 % sous 8 jours"), "escompte paramétré attendu");

        // Envoi de la facture et relance manuelle.
        ResponseEntity<DocumentEmailResponse> invoiceMail = restTemplate.postForEntity(
                "/api/v1/invoices/" + invoice.id() + "/email", new HttpEntity<>(new DocumentEmailRequest(null, null, null), headers()), DocumentEmailResponse.class);
        assertEquals(HttpStatus.OK, invoiceMail.getStatusCode());
        ResponseEntity<DocumentEmailResponse> reminder = restTemplate.postForEntity(
                "/api/v1/invoices/" + invoice.id() + "/reminders", new HttpEntity<>(new DocumentEmailRequest(null, null, null), headers()), DocumentEmailResponse.class);
        assertEquals(HttpStatus.OK, reminder.getStatusCode());
        assertTrue(greenMail.waitForIncomingEmail(5000, 3));
        InvoiceResponse afterMail = getInvoice(invoice.id());
        assertNotNull(afterMail.sentAt());
        assertEquals(1, afterMail.reminderCount());
        assertNotNull(afterMail.lastReminderAt());

        // --- F. Relance automatique : seuil J+15 atteint mais relance manuelle trop récente → rien ;
        //        une fois le délai minimal écoulé → 2e relance, puis plus jamais automatiquement.
        Invoice entity = invoiceRepository.findById(invoice.id()).orElseThrow();
        entity.setDueDate(LocalDate.now().minusDays(16));
        invoiceRepository.save(entity);
        assertEquals(0, reminderScheduler.run(LocalDate.now()), "relance manuelle récente : pas de doublon");

        entity = invoiceRepository.findById(invoice.id()).orElseThrow();
        entity.setLastReminderAt(Instant.now().minus(6, ChronoUnit.DAYS));
        invoiceRepository.save(entity);
        assertEquals(1, reminderScheduler.run(LocalDate.now()), "2e relance automatique attendue");
        assertEquals(2, getInvoice(invoice.id()).reminderCount());
        assertTrue(greenMail.waitForIncomingEmail(5000, 4));
        assertTrue(lastMessageTo("jean.dupont@client.test").getSubject().startsWith("Rappel n° 2"));
        assertEquals(0, reminderScheduler.run(LocalDate.now()), "plafond de 2 relances automatiques");

        // --- C. Avoir total : numérotation dédiée, montants négatifs, facture d'origine annulée ---
        ResponseEntity<InvoiceResponse> creditResp = restTemplate.postForEntity(
                "/api/v1/invoices/" + invoice.id() + "/credit-notes",
                new HttpEntity<>(new CreditNoteRequest("Erreur de facturation", null), headers()), InvoiceResponse.class);
        assertEquals(HttpStatus.CREATED, creditResp.getStatusCode());
        InvoiceResponse credit = creditResp.getBody();
        assertEquals(InvoiceType.CREDIT_NOTE, credit.invoiceType());
        assertTrue(credit.number().startsWith("AVR"), "préfixe d'avoir du workspace : " + credit.number());
        assertEquals(invoice.id(), credit.creditedInvoiceId());
        assertEquals(invoice.number(), credit.creditedInvoiceNumber());
        assertEquals(0, invoice.totalTtc().negate().compareTo(credit.totalTtc()));
        assertEquals(InvoiceStatus.PAID, credit.status(), "avoir soldé (aucun mouvement de trésorerie)");

        InvoiceResponse cancelled = getInvoice(invoice.id());
        assertEquals(InvoiceStatus.CANCELLED, cancelled.status());
        assertEquals(1, cancelled.creditNotes().size());
        assertEquals(credit.id(), cancelled.creditNotes().getFirst().id());

        ResponseEntity<String> again = restTemplate.postForEntity("/api/v1/invoices/" + invoice.id() + "/credit-notes",
                new HttpEntity<>(new CreditNoteRequest("Encore", null), headers()), String.class);
        assertEquals(HttpStatus.CONFLICT, again.getStatusCode());

        ResponseEntity<byte[]> creditPdf = restTemplate.exchange("/api/v1/documents/invoices/" + credit.id() + "/download",
                HttpMethod.GET, new HttpEntity<>(headers()), byte[].class);
        assertEquals(HttpStatus.OK, creditPdf.getStatusCode());
        String creditText = PdfText.extract(creditPdf.getBody());
        assertTrue(creditText.contains("AVOIR"));
        assertTrue(creditText.contains(invoice.number()), "l'avoir référence la facture d'origine");

        // Le refus manuel d'un devis déjà accepté est impossible ; sur un devis envoyé, il l'est.
        QuotationResponse other = restTemplate.postForObject("/api/v1/quotations",
                new HttpEntity<>(quotationRequest, headers()), QuotationResponse.class);
        restTemplate.postForObject("/api/v1/quotations/" + other.id() + "/send", new HttpEntity<>(headers()),
                QuotationResponse.class);
        QuotationResponse rejected = restTemplate.postForObject("/api/v1/quotations/" + other.id() + "/reject",
                new HttpEntity<>(java.util.Map.of("reason", "Trop cher"), headers()), QuotationResponse.class);
        assertEquals(QuotationStatus.REJECTED, rejected.status());
        assertEquals(QuotationDecisionSource.MANUAL, rejected.decisionSource());
        assertNull(rejected.convertedInvoiceId());

        // --- F. Export comptable CSV ---
        ResponseEntity<byte[]> csv = restTemplate.exchange(
                "/api/v1/invoices/export?workspaceId=" + workspaceId + "&from=" + LocalDate.now().minusDays(1)
                        + "&to=" + LocalDate.now().plusDays(1),
                HttpMethod.GET, new HttpEntity<>(headers()), byte[].class);
        assertEquals(HttpStatus.OK, csv.getStatusCode());
        String content = new String(csv.getBody(), StandardCharsets.UTF_8);
        assertTrue(content.startsWith("\uFEFFType;Numéro;"));
        assertTrue(content.contains(invoice.number() + ";"), "la facture figure dans l'export");
        assertTrue(content.contains(credit.number() + ";"), "l'avoir figure dans l'export");
        assertTrue(content.contains("Annulée"));
        assertTrue(content.contains(";-1200,00;"), "montants négatifs pour l'avoir");
    }

    private QuotationResponse getQuotation(UUID id) {
        return restTemplate.exchange("/api/v1/quotations/" + id, HttpMethod.GET, new HttpEntity<>(headers()),
                QuotationResponse.class).getBody();
    }

    private InvoiceResponse getInvoice(UUID id) {
        return restTemplate.exchange("/api/v1/invoices/" + id, HttpMethod.GET, new HttpEntity<>(headers()),
                InvoiceResponse.class).getBody();
    }

    /** Texte décodé (quoted-printable, multipart) de toutes les parties texte du message. */
    private static String textOf(jakarta.mail.Part part) throws Exception {
        Object content = part.getContent();
        if (content instanceof String s) {
            return s;
        }
        if (content instanceof jakarta.mail.Multipart mp) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < mp.getCount(); i++) {
                sb.append(textOf(mp.getBodyPart(i))).append('\n');
            }
            return sb.toString();
        }
        return "";
    }

    private static MimeMessage lastMessageTo(String recipient) throws Exception {
        MimeMessage[] messages = greenMail.getReceivedMessages();
        for (int i = messages.length - 1; i >= 0; i--) {
            if (messages[i].getAllRecipients()[0].toString().contains(recipient)) {
                return messages[i];
            }
        }
        throw new AssertionError("Aucun e-mail reçu pour " + recipient);
    }

    private static String suffix() {
        return UUID.randomUUID().toString().substring(0, 6).toUpperCase();
    }

    private HttpHeaders headers() {
        HttpHeaders h = new HttpHeaders();
        h.setBearerAuth(token);
        return h;
    }
}
