package com.nerd.erp.integration.invoicescan;

import com.nerd.erp.api.auth.LoginRequest;
import com.nerd.erp.api.auth.LoginResponse;
import com.nerd.erp.api.invoice.InvoiceResponse;
import com.nerd.erp.api.invoicescan.InvoiceScanCommitRequest;
import com.nerd.erp.api.workspace.WorkspaceResponse;
import com.nerd.erp.application.invoicescan.CustomerMatchStatus;
import com.nerd.erp.application.invoicescan.ExtractedInvoiceData;
import com.nerd.erp.application.invoicescan.InvoiceScanSessionStore;
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
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class InvoiceScanCommitIntegrationTest extends PostgresIntegrationTest {

    @Autowired
    TestRestTemplate restTemplate;

    @Autowired
    InvoiceScanSessionStore sessionStore;

    String token;
    UUID tenantId;
    UUID userId;
    UUID workspaceId;

    @BeforeEach
    void setUp() {
        LoginResponse login = restTemplate.postForObject("/api/v1/auth/login",
                new LoginRequest("admin@nerd.local", "Admin123!", "test-tenant"), LoginResponse.class);
        token = login.accessToken();
        tenantId = login.tenantId();
        userId = login.userId();

        HttpHeaders headers = authHeaders();
        ResponseEntity<List<WorkspaceResponse>> workspaces = restTemplate.exchange(
                "/api/v1/workspaces",
                HttpMethod.GET,
                new HttpEntity<>(headers),
                new ParameterizedTypeReference<>() {
                });
        workspaceId = workspaces.getBody().getFirst().id();
    }

    @Test
    void commitCreatesCustomerAndInvoiceFromScanSession() {
        ExtractedInvoiceData.ExtractedCustomer customer = new ExtractedInvoiceData.ExtractedCustomer(
                "INDIVIDUAL", null, "Aude", "Archambault", "aude.test@example.com",
                null, "1 rue Test", null, "75001", "Paris", "FR");
        ExtractedInvoiceData extraction = new ExtractedInvoiceData(
                "392192504",
                null,
                "Etsy",
                "2025-12-24",
                customer,
                List.of(new ExtractedInvoiceData.ExtractedLine(
                        "Article test", BigDecimal.ONE, new BigDecimal("10.83"), new BigDecimal("20"))),
                new BigDecimal("3.33"),
                new BigDecimal("20"),
                new BigDecimal("26.67"),
                new BigDecimal("5.33"),
                new BigDecimal("32.00"),
                "EUR");

        var session = sessionStore.create(tenantId, workspaceId, userId, extraction,
                CustomerMatchStatus.NOT_FOUND, null, null);

        InvoiceScanCommitRequest request = new InvoiceScanCommitRequest(
                session.id(), workspaceId, true, true, false, false,
                null, null, null, null, null, null, null);

        ResponseEntity<InvoiceResponse> response = restTemplate.postForEntity(
                "/api/v1/invoice-scan/commit",
                new HttpEntity<>(request, authHeaders()),
                InvoiceResponse.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(2, response.getBody().lines().size());
        assertEquals("Etsy", response.getBody().merchant());
        assertEquals("392192504", response.getBody().externalReference());
    }

    private HttpHeaders authHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return headers;
    }
}
