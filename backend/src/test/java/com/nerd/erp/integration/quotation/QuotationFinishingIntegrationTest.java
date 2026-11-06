package com.nerd.erp.integration.quotation;

import com.nerd.erp.api.auth.LoginRequest;
import com.nerd.erp.api.auth.LoginResponse;
import com.nerd.erp.api.customer.CustomerRequest;
import com.nerd.erp.api.customer.CustomerResponse;
import com.nerd.erp.api.invoice.InvoiceResponse;
import com.nerd.erp.api.quotation.DocumentLineRequest;
import com.nerd.erp.api.quotation.QuotationRequest;
import com.nerd.erp.api.quotation.QuotationResponse;
import com.nerd.erp.api.workspace.WorkspaceController.SequenceUpdateRequest;
import com.nerd.erp.api.workspace.WorkspaceResponse;
import com.nerd.erp.api.workspace.WorkspaceUpdateRequest;
import com.nerd.erp.application.document.DocumentNumberService;
import com.nerd.erp.domain.customer.CustomerStatus;
import com.nerd.erp.domain.customer.CustomerType;
import com.nerd.erp.domain.document.DocumentSequenceType;
import com.nerd.erp.domain.invoice.InvoiceType;
import com.nerd.erp.domain.quotation.QuotationStatus;
import com.nerd.erp.domain.workspace.SequenceReset;
import com.nerd.erp.support.PostgresIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Lot 4 : remise par ligne, récap TVA par taux, duplication, facture d'acompte + solde, numérotation configurable.
 */
class QuotationFinishingIntegrationTest extends PostgresIntegrationTest {

    @Autowired
    TestRestTemplate restTemplate;

    String token;
    UUID workspaceId;
    UUID customerId;

    @BeforeEach
    void setUp() {
        LoginResponse login = restTemplate.postForObject("/api/v1/auth/login",
                new LoginRequest("admin@nerd.local", "Admin123!", "test-tenant"), LoginResponse.class);
        token = login.accessToken();
        ResponseEntity<List<WorkspaceResponse>> workspaces = restTemplate.exchange("/api/v1/workspaces",
                HttpMethod.GET, new HttpEntity<>(headers()), new ParameterizedTypeReference<>() {
                });
        workspaceId = workspaces.getBody().getFirst().id();
        CustomerRequest customerRequest = new CustomerRequest(
                workspaceId, CustomerType.COMPANY, "Finitions SAS", null, null,
                "finitions@acme.test", null, null, null, null, null, "FR", CustomerStatus.CLIENT, null, null);
        customerId = restTemplate.postForObject("/api/v1/customers",
                new HttpEntity<>(customerRequest, headers()), CustomerResponse.class).id();
    }

    @Test
    void lineDiscountVatBreakdownDuplicateDepositAndBalance() {
        // Ligne A : 2 × 500 HT, TVA 20 %, remise ligne 10 % → 900 HT ; ligne B : 1 × 200 HT, TVA 5,5 % → 200 HT.
        // Remise globale 10 % → HT net 990 ; TVA 20 % : base 810 → 162 ; TVA 5,5 % : base 180 → 9,90 ; TTC 1 161,90.
        QuotationRequest request = new QuotationRequest(workspaceId, customerId, LocalDate.now(),
                LocalDate.now().plusDays(45), "Finitions", new BigDecimal("10"), new BigDecimal("30"),
                List.of(new DocumentLineRequest(null, "Développement", new BigDecimal("2"), new BigDecimal("500"),
                                new BigDecimal("20"), new BigDecimal("10")),
                        new DocumentLineRequest(null, "Livre", BigDecimal.ONE, new BigDecimal("200"),
                                new BigDecimal("5.5"), null)));
        QuotationResponse draft = restTemplate.postForObject("/api/v1/quotations",
                new HttpEntity<>(request, headers()), QuotationResponse.class);
        assertMoney("900", draft.lines().getFirst().lineTotalHt());
        assertMoney("10", draft.lines().getFirst().discountPercent());
        assertMoney("110", draft.discountAmountHt());
        assertMoney("990", draft.totalHt());
        assertMoney("171.90", draft.totalVat());
        assertMoney("1161.90", draft.totalTtc());
        assertEquals(2, draft.vatBreakdown().size());
        assertMoney("5.5", draft.vatBreakdown().get(0).rate());
        assertMoney("180", draft.vatBreakdown().get(0).baseHt());
        assertMoney("9.90", draft.vatBreakdown().get(0).vatAmount());
        assertMoney("810", draft.vatBreakdown().get(1).baseHt());
        assertMoney("162", draft.vatBreakdown().get(1).vatAmount());

        // --- Duplication : nouveau brouillon identique, validité recalculée ---
        ResponseEntity<QuotationResponse> dup = restTemplate.exchange("/api/v1/quotations/" + draft.id() + "/duplicate",
                HttpMethod.POST, new HttpEntity<>(headers()), QuotationResponse.class);
        assertEquals(HttpStatus.CREATED, dup.getStatusCode());
        QuotationResponse copy = dup.getBody();
        assertNotEquals(draft.id(), copy.id());
        assertEquals(QuotationStatus.DRAFT, copy.status());
        assertNull(copy.number());
        assertEquals(2, copy.lines().size());
        assertMoney("10", copy.lines().getFirst().discountPercent());
        assertMoney("1161.90", copy.totalTtc());
        assertMoney("30", copy.depositPercent());
        assertEquals(LocalDate.now().plusDays(45), copy.validUntil());

        // --- Acompte impossible avant acceptation ---
        ResponseEntity<String> tooEarly = restTemplate.exchange("/api/v1/quotations/" + draft.id() + "/deposit-invoice",
                HttpMethod.POST, new HttpEntity<>(headers()), String.class);
        assertEquals(HttpStatus.CONFLICT, tooEarly.getStatusCode());

        restTemplate.postForObject("/api/v1/quotations/" + draft.id() + "/send", new HttpEntity<>(headers()), QuotationResponse.class);
        restTemplate.postForObject("/api/v1/quotations/" + draft.id() + "/accept", new HttpEntity<>(headers()), QuotationResponse.class);

        // --- Facture d'acompte : 30 % par taux → 243 HT (20 %) + 54 HT (5,5 %) = 297 HT ; TVA 51,57 ; TTC 348,57 ---
        InvoiceResponse deposit = restTemplate.postForObject("/api/v1/quotations/" + draft.id() + "/deposit-invoice",
                new HttpEntity<>(headers()), InvoiceResponse.class);
        assertEquals(InvoiceType.DEPOSIT, deposit.invoiceType());
        assertNotNull(deposit.number());
        assertEquals(2, deposit.lines().size());
        assertMoney("297", deposit.totalHt());
        assertMoney("51.57", deposit.totalVat());
        assertMoney("348.57", deposit.totalTtc());
        assertMoney("348.57", draft.depositAmountTtc());
        InvoiceResponse depositAgain = restTemplate.postForObject("/api/v1/quotations/" + draft.id() + "/deposit-invoice",
                new HttpEntity<>(headers()), InvoiceResponse.class);
        assertEquals(deposit.id(), depositAgain.id());

        QuotationResponse accepted = restTemplate.exchange("/api/v1/quotations/" + draft.id(), HttpMethod.GET,
                new HttpEntity<>(headers()), QuotationResponse.class).getBody();
        assertEquals(deposit.id(), accepted.depositInvoiceId());
        assertEquals(deposit.number(), accepted.depositInvoiceNumber());

        ResponseEntity<byte[]> depositPdf = restTemplate.exchange("/api/v1/documents/invoices/" + deposit.id() + "/download",
                HttpMethod.GET, new HttpEntity<>(headers()), byte[].class);
        assertEquals(HttpStatus.OK, depositPdf.getStatusCode());
        assertEquals("%PDF", new String(depositPdf.getBody(), 0, 4));

        // --- Facture de solde : prestations − acompte → 693 HT ; 120,33 TVA ; 813,33 TTC ---
        InvoiceResponse balance = restTemplate.postForObject("/api/v1/quotations/" + draft.id() + "/convert-to-invoice",
                new HttpEntity<>(headers()), InvoiceResponse.class);
        assertEquals(InvoiceType.STANDARD, balance.invoiceType());
        assertEquals(4, balance.lines().size());
        assertNotNull(balance.depositInvoice());
        assertEquals(deposit.id(), balance.depositInvoice().id());
        assertMoney("110", balance.discountAmountHt());
        assertMoney("693", balance.totalHt());
        assertMoney("120.33", balance.totalVat());
        assertMoney("813.33", balance.totalTtc());
        assertEquals(2, balance.vatBreakdown().size());
        assertTrue(balance.lines().stream().anyMatch(l -> l.description().startsWith("Déduction acompte")));

        InvoiceResponse depositDetail = restTemplate.exchange("/api/v1/invoices/" + deposit.id(), HttpMethod.GET,
                new HttpEntity<>(headers()), InvoiceResponse.class).getBody();
        assertNotNull(depositDetail.balanceInvoice());
        assertEquals(balance.id(), depositDetail.balanceInvoice().id());

        ResponseEntity<byte[]> balancePdf = restTemplate.exchange("/api/v1/documents/invoices/" + balance.id() + "/download",
                HttpMethod.GET, new HttpEntity<>(headers()), byte[].class);
        assertEquals(HttpStatus.OK, balancePdf.getStatusCode());

        // L'acompte déduit sur un solde ne peut plus être supprimé.
        ResponseEntity<String> del = restTemplate.exchange("/api/v1/invoices/" + deposit.id(), HttpMethod.DELETE,
                new HttpEntity<>(headers()), String.class);
        assertEquals(HttpStatus.CONFLICT, del.getStatusCode());
    }

    @Test
    void configurableNumberingWithMonthlyResetAndAdjustableCounter() {
        WorkspaceResponse before = restTemplate.exchange("/api/v1/workspaces/" + workspaceId, HttpMethod.GET,
                new HttpEntity<>(headers()), WorkspaceResponse.class).getBody();
        try {
            // Motif incohérent avec la périodicité → 400
            ResponseEntity<String> bad = restTemplate.exchange("/api/v1/workspaces/" + workspaceId, HttpMethod.PUT,
                    new HttpEntity<>(update(before, "{PREFIX}-{YYYY}-{SEQ}", SequenceReset.MONTHLY), headers()), String.class);
            assertEquals(HttpStatus.BAD_REQUEST, bad.getStatusCode());

            ResponseEntity<WorkspaceResponse> ok = restTemplate.exchange("/api/v1/workspaces/" + workspaceId, HttpMethod.PUT,
                    new HttpEntity<>(update(before, "{PREFIX}/{YY}{MM}/{SEQ:3}", SequenceReset.MONTHLY), headers()),
                    WorkspaceResponse.class);
            assertEquals(HttpStatus.OK, ok.getStatusCode());
            assertEquals(SequenceReset.MONTHLY, ok.getBody().sequenceReset());

            // Reprise de numérotation : prochain devis = 100
            ResponseEntity<DocumentNumberService.Counter> set = restTemplate.exchange(
                    "/api/v1/workspaces/" + workspaceId + "/sequences/QUOTATION", HttpMethod.PUT,
                    new HttpEntity<>(new SequenceUpdateRequest(100L), headers()), DocumentNumberService.Counter.class);
            assertEquals(HttpStatus.OK, set.getStatusCode());
            LocalDate today = LocalDate.now();
            String expected = "%s/%02d%02d/100".formatted(before.quotationPrefix(), today.getYear() % 100, today.getMonthValue());
            assertEquals(expected, set.getBody().nextNumber());

            ResponseEntity<List<DocumentNumberService.Counter>> counters = restTemplate.exchange(
                    "/api/v1/workspaces/" + workspaceId + "/sequences", HttpMethod.GET, new HttpEntity<>(headers()),
                    new ParameterizedTypeReference<>() {
                    });
            assertEquals(DocumentSequenceType.values().length, counters.getBody().size());
            assertTrue(counters.getBody().stream().anyMatch(c -> c.type() == DocumentSequenceType.QUOTATION
                    && c.lastValue() == 99L && expected.equals(c.nextNumber())));

            QuotationRequest request = new QuotationRequest(workspaceId, customerId, null, null, null, null, null,
                    List.of(new DocumentLineRequest(null, "Prestation", BigDecimal.ONE, new BigDecimal("100"),
                            new BigDecimal("20"), null)));
            QuotationResponse draft = restTemplate.postForObject("/api/v1/quotations",
                    new HttpEntity<>(request, headers()), QuotationResponse.class);
            QuotationResponse sent = restTemplate.postForObject("/api/v1/quotations/" + draft.id() + "/send",
                    new HttpEntity<>(headers()), QuotationResponse.class);
            assertEquals(expected, sent.number());

            // Un compteur ne recule jamais
            ResponseEntity<String> backwards = restTemplate.exchange(
                    "/api/v1/workspaces/" + workspaceId + "/sequences/QUOTATION", HttpMethod.PUT,
                    new HttpEntity<>(new SequenceUpdateRequest(50L), headers()), String.class);
            assertEquals(HttpStatus.CONFLICT, backwards.getStatusCode());
        } finally {
            restTemplate.exchange("/api/v1/workspaces/" + workspaceId, HttpMethod.PUT,
                    new HttpEntity<>(update(before, before.numberPattern(), before.sequenceReset()), headers()),
                    WorkspaceResponse.class);
        }
    }

    private static WorkspaceUpdateRequest update(WorkspaceResponse w, String pattern, SequenceReset reset) {
        return new WorkspaceUpdateRequest(w.name(), w.legalSubtitle(), w.addressLine1(), w.addressLine2(),
                w.postalCode(), w.city(), w.region(), w.countryCode(), w.contactEmail(), w.websiteUrl(), w.siren(),
                w.vatNumber(), w.iban(), w.bic(), w.currencyCode(), w.invoicePrefix(), w.quotationPrefix(),
                w.creditNotePrefix(), w.vatRegime(), w.paymentTermsDays(), w.latePenaltyRate(), w.recoveryFeeAmount(),
                w.earlyPaymentDiscount(), w.paymentTermsText(), w.legalFooterText(), w.remindersEnabled(),
                pattern, reset);
    }

    private static void assertMoney(String expected, BigDecimal actual) {
        assertNotNull(actual);
        assertEquals(0, new BigDecimal(expected).compareTo(actual), "attendu " + expected + " mais " + actual);
    }

    private HttpHeaders headers() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return headers;
    }
}
