package com.nerd.erp.integration.sales;

import com.nerd.erp.api.auth.LoginRequest;
import com.nerd.erp.api.auth.LoginResponse;
import com.nerd.erp.api.catalog.ProductRequest;
import com.nerd.erp.api.catalog.ProductResponse;
import com.nerd.erp.api.catalog.ProductStatsResponse;
import com.nerd.erp.api.customer.CustomerRequest;
import com.nerd.erp.api.customer.CustomerResponse;
import com.nerd.erp.api.invoice.InvoiceResponse;
import com.nerd.erp.api.payment.PaymentRequest;
import com.nerd.erp.api.payment.PaymentResponse;
import com.nerd.erp.api.quotation.DocumentLineRequest;
import com.nerd.erp.api.quotation.QuotationRequest;
import com.nerd.erp.api.quotation.QuotationResponse;
import com.nerd.erp.api.shared.PageResponse;
import com.nerd.erp.api.workspace.WorkspaceCreateRequest;
import com.nerd.erp.api.workspace.WorkspaceResponse;
import com.nerd.erp.domain.catalog.BillingType;
import com.nerd.erp.domain.customer.CustomerStatus;
import com.nerd.erp.domain.customer.CustomerType;
import com.nerd.erp.domain.invoice.InvoiceStatus;
import com.nerd.erp.domain.payment.PaymentMethod;
import com.nerd.erp.domain.quotation.QuotationStatus;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Cycle de vente complet via l'API : produit catalogue (mode de tarif) → devis → envoi / acceptation →
 * facture → paiement partiel → solde, avec vérification des statuts, des montants et des filtres
 * « factures ouvertes » utilisés par l'écran Paiements.
 */
class SalesCycleIntegrationTest extends PostgresIntegrationTest {

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

        ResponseEntity<List<WorkspaceResponse>> workspaces = restTemplate.exchange(
                "/api/v1/workspaces", HttpMethod.GET, new HttpEntity<>(authHeaders()),
                new ParameterizedTypeReference<>() {
                });
        workspaceId = workspaces.getBody().getFirst().id();

        CustomerRequest customerRequest = new CustomerRequest(
                workspaceId, CustomerType.COMPANY, "Cycle " + UUID.randomUUID().toString().substring(0, 8),
                null, null, "cycle@acme.test", null, null, null, null, null, "FR",
                CustomerStatus.CLIENT, null, null);
        customerId = restTemplate.postForObject("/api/v1/customers",
                new HttpEntity<>(customerRequest, authHeaders()), CustomerResponse.class).id();
    }

    @Test
    void productBillingTypeIsPersistedAndFeedsStats() {
        // Workspace dédié : les stats sont globales au workspace, le test ne doit pas dépendre des autres.
        WorkspaceResponse ws = restTemplate.postForObject("/api/v1/workspaces",
                new HttpEntity<>(new WorkspaceCreateRequest("Stats " + suffix(), null, null, "EUR", null, null),
                        authHeaders()),
                WorkspaceResponse.class);
        workspaceId = ws.id();

        ProductResponse days = createProduct("TJM-" + suffix(), "Développement", BillingType.DAYS, null,
                new BigDecimal("600"));
        createProduct("TJM-" + suffix(), "Conseil", BillingType.DAYS, null, new BigDecimal("800"));
        ProductResponse fixed = createProduct("FOR-" + suffix(), "Audit", BillingType.FIXED_PRICE, "mission",
                new BigDecimal("1500"));
        ProductResponse unit = createProduct("UNI-" + suffix(), "Câble", null, null, new BigDecimal("9.90"));

        assertEquals(BillingType.DAYS, days.billingType());
        assertEquals("jour", days.unitLabel(), "unité par défaut du mode");
        assertEquals("mission", fixed.unitLabel(), "unité personnalisée conservée");
        assertEquals(BillingType.UNIT, unit.billingType(), "mode par défaut quand absent");
        assertEquals("unité", unit.unitLabel());

        ProductStatsResponse stats = restTemplate.exchange(
                "/api/v1/products/stats?workspaceId=" + workspaceId, HttpMethod.GET,
                new HttpEntity<>(authHeaders()), ProductStatsResponse.class).getBody();
        assertNotNull(stats);
        // Seuls les produits DAYS entrent dans le TJM moyen : (600 + 800) / 2.
        assertEquals(0, new BigDecimal("700").compareTo(stats.averageDayRateHt()),
                "TJM moyen attendu 700, obtenu " + stats.averageDayRateHt());
        assertEquals(4, stats.activeProducts());
    }

    @Test
    void fullCycleQuotationToPaidInvoice() {
        HttpHeaders headers = authHeaders();
        ProductResponse product = createProduct("DEV-" + suffix(), "Développement", BillingType.DAYS, null,
                new BigDecimal("500"));

        // Devis : 3 jours × 500 € HT, TVA 20 %, sans remise → 1500 HT / 300 TVA / 1800 TTC
        QuotationRequest quotationRequest = new QuotationRequest(
                workspaceId, customerId, LocalDate.now(), LocalDate.now().plusDays(30), "Devis cycle",
                BigDecimal.ZERO, BigDecimal.ZERO,
                List.of(new DocumentLineRequest(product.id(), product.name(), new BigDecimal("3"),
                        product.unitPriceHt(), product.vatRate())));
        QuotationResponse draft = restTemplate.postForObject("/api/v1/quotations",
                new HttpEntity<>(quotationRequest, headers), QuotationResponse.class);
        assertEquals(QuotationStatus.DRAFT, draft.status());
        assertEquals(0, new BigDecimal("1800").compareTo(draft.totalTtc()));

        // Conversion impossible tant que le devis n'est pas accepté.
        ResponseEntity<String> tooEarly = restTemplate.postForEntity(
                "/api/v1/quotations/" + draft.id() + "/convert-to-invoice", new HttpEntity<>(headers), String.class);
        assertTrue(tooEarly.getStatusCode().is4xxClientError(),
                "conversion refusée sur un brouillon, reçu " + tooEarly.getStatusCode());

        QuotationResponse sent = restTemplate.postForObject("/api/v1/quotations/" + draft.id() + "/send",
                new HttpEntity<>(headers), QuotationResponse.class);
        assertEquals(QuotationStatus.SENT, sent.status());
        assertNotNull(sent.number(), "le numéro est attribué à l'envoi");
        QuotationResponse accepted = restTemplate.postForObject("/api/v1/quotations/" + draft.id() + "/accept",
                new HttpEntity<>(headers), QuotationResponse.class);
        assertEquals(QuotationStatus.ACCEPTED, accepted.status());

        InvoiceResponse invoice = restTemplate.postForObject(
                "/api/v1/quotations/" + draft.id() + "/convert-to-invoice",
                new HttpEntity<>(headers), InvoiceResponse.class);
        assertEquals(InvoiceStatus.ISSUED, invoice.status());
        assertNotNull(invoice.number());
        assertEquals(draft.id(), invoice.sourceQuotationId());
        assertEquals(0, new BigDecimal("1800").compareTo(invoice.totalTtc()));
        assertEquals(0, BigDecimal.ZERO.compareTo(invoice.amountPaid()));
        assertEquals(1, invoice.lines().size());
        assertEquals(product.id(), invoice.lines().getFirst().productId());

        QuotationResponse converted = restTemplate.exchange("/api/v1/quotations/" + draft.id(),
                HttpMethod.GET, new HttpEntity<>(headers), QuotationResponse.class).getBody();
        assertEquals(QuotationStatus.CONVERTED, converted.status());

        // La facture apparaît dans les « ouvertes » du client.
        assertTrue(openInvoiceIds().contains(invoice.id()));

        // Sur-affectation refusée.
        ResponseEntity<String> overpay = restTemplate.postForEntity("/api/v1/payments",
                new HttpEntity<>(payment(new BigDecimal("2000"), invoice.id(), new BigDecimal("2000")), headers),
                String.class);
        assertEquals(HttpStatus.BAD_REQUEST, overpay.getStatusCode());

        // Acompte 800 → PARTIALLY_PAID
        PaymentResponse deposit = restTemplate.postForObject("/api/v1/payments",
                new HttpEntity<>(payment(new BigDecimal("800"), invoice.id(), new BigDecimal("800")), headers),
                PaymentResponse.class);
        assertNotNull(deposit.id());
        InvoiceResponse afterDeposit = getInvoice(invoice.id());
        assertEquals(InvoiceStatus.PARTIALLY_PAID, afterDeposit.status());
        assertEquals(0, new BigDecimal("800").compareTo(afterDeposit.amountPaid()));
        assertTrue(openInvoiceIds().contains(invoice.id()), "encore un reste à payer");

        // Solde 1000 → PAID et disparition des factures ouvertes
        restTemplate.postForObject("/api/v1/payments",
                new HttpEntity<>(payment(new BigDecimal("1000"), invoice.id(), new BigDecimal("1000")), headers),
                PaymentResponse.class);
        InvoiceResponse paid = getInvoice(invoice.id());
        assertEquals(InvoiceStatus.PAID, paid.status());
        assertEquals(0, paid.totalTtc().compareTo(paid.amountPaid()));
        assertFalse(openInvoiceIds().contains(invoice.id()));

        // Un paiement supplémentaire sur une facture soldée est refusé.
        ResponseEntity<String> extra = restTemplate.postForEntity("/api/v1/payments",
                new HttpEntity<>(payment(new BigDecimal("1"), invoice.id(), new BigDecimal("1")), headers),
                String.class);
        assertEquals(HttpStatus.BAD_REQUEST, extra.getStatusCode());

        // Le produit utilisé sur un devis ne peut plus être supprimé.
        ResponseEntity<String> delete = restTemplate.exchange("/api/v1/products/" + product.id(),
                HttpMethod.DELETE, new HttpEntity<>(headers), String.class);
        assertEquals(HttpStatus.CONFLICT, delete.getStatusCode());

        // Recherche de facture par numéro.
        PageResponse<InvoiceResponse> byNumber = restTemplate.exchange(
                "/api/v1/invoices?workspaceId=" + workspaceId + "&q=" + invoice.number().toLowerCase(),
                HttpMethod.GET, new HttpEntity<>(headers),
                new ParameterizedTypeReference<PageResponse<InvoiceResponse>>() {
                }).getBody();
        assertEquals(1, byNumber.totalElements());
        assertEquals(invoice.id(), byNumber.content().getFirst().id());
    }

    private ProductResponse createProduct(String ref, String name, BillingType billingType, String unitLabel,
                                          BigDecimal priceHt) {
        ProductRequest request = new ProductRequest(workspaceId, ref, name, null, priceHt,
                new BigDecimal("20"), true, null, billingType, unitLabel);
        ResponseEntity<ProductResponse> response = restTemplate.postForEntity("/api/v1/products",
                new HttpEntity<>(request, authHeaders()), ProductResponse.class);
        assertEquals(HttpStatus.OK, response.getStatusCode(), "création produit " + ref);
        return response.getBody();
    }

    private PaymentRequest payment(BigDecimal amount, UUID invoiceId, BigDecimal allocated) {
        return new PaymentRequest(workspaceId, customerId, amount, LocalDate.now(), PaymentMethod.TRANSFER,
                null, null, List.of(new PaymentRequest.AllocationRequest(invoiceId, allocated)));
    }

    private InvoiceResponse getInvoice(UUID id) {
        return restTemplate.exchange("/api/v1/invoices/" + id, HttpMethod.GET,
                new HttpEntity<>(authHeaders()), InvoiceResponse.class).getBody();
    }

    private List<UUID> openInvoiceIds() {
        PageResponse<InvoiceResponse> page = restTemplate.exchange(
                "/api/v1/invoices?workspaceId=" + workspaceId + "&customerId=" + customerId + "&openOnly=true",
                HttpMethod.GET, new HttpEntity<>(authHeaders()),
                new ParameterizedTypeReference<PageResponse<InvoiceResponse>>() {
                }).getBody();
        return page.content().stream().map(InvoiceResponse::id).toList();
    }

    private static String suffix() {
        return UUID.randomUUID().toString().substring(0, 6).toUpperCase();
    }

    private HttpHeaders authHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return headers;
    }
}
