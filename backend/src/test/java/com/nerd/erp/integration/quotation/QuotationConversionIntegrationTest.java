package com.nerd.erp.integration.quotation;

import com.nerd.erp.api.customer.CustomerRequest;
import com.nerd.erp.api.customer.CustomerResponse;
import com.nerd.erp.domain.customer.CustomerStatus;
import com.nerd.erp.domain.customer.CustomerType;
import com.nerd.erp.api.invoice.InvoiceResponse;
import com.nerd.erp.api.quotation.DocumentLineRequest;
import com.nerd.erp.api.quotation.QuotationRequest;
import com.nerd.erp.api.quotation.QuotationResponse;
import com.nerd.erp.api.auth.LoginRequest;
import com.nerd.erp.api.auth.LoginResponse;
import com.nerd.erp.support.PostgresIntegrationTest;
import com.nerd.erp.api.workspace.WorkspaceResponse;
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

class QuotationConversionIntegrationTest extends PostgresIntegrationTest {

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

        HttpHeaders headers = authHeaders();
        ResponseEntity<List<WorkspaceResponse>> workspaces = restTemplate.exchange(
                "/api/v1/workspaces",
                HttpMethod.GET,
                new HttpEntity<>(headers),
                new ParameterizedTypeReference<>() {
                });
        workspaceId = workspaces.getBody().getFirst().id();

        CustomerRequest customerRequest = new CustomerRequest(
                workspaceId, CustomerType.COMPANY, "ACME", null, null,
                "contact@acme.test", null, null, null, null, null, "FR",
                CustomerStatus.CLIENT, null, null);
        CustomerResponse customer = restTemplate.postForObject(
                "/api/v1/customers", new HttpEntity<>(customerRequest, headers), CustomerResponse.class);
        customerId = customer.id();
    }

    @Test
    void convertToInvoiceIsIdempotent() {
        HttpHeaders headers = authHeaders();
        QuotationRequest quotationRequest = new QuotationRequest(
                workspaceId,
                customerId,
                null,
                null,
                null,
                List.of(new DocumentLineRequest(null, "Prestation", BigDecimal.ONE, new BigDecimal("500"),
                        new BigDecimal("20"))));
        QuotationResponse draft = restTemplate.postForObject(
                "/api/v1/quotations", new HttpEntity<>(quotationRequest, headers), QuotationResponse.class);
        restTemplate.postForObject("/api/v1/quotations/" + draft.id() + "/send",
                new HttpEntity<>(headers), QuotationResponse.class);
        restTemplate.postForObject("/api/v1/quotations/" + draft.id() + "/accept",
                new HttpEntity<>(headers), QuotationResponse.class);

        InvoiceResponse first = restTemplate.postForObject(
                "/api/v1/quotations/" + draft.id() + "/convert-to-invoice",
                new HttpEntity<>(headers), InvoiceResponse.class);
        InvoiceResponse second = restTemplate.postForObject(
                "/api/v1/quotations/" + draft.id() + "/convert-to-invoice",
                new HttpEntity<>(headers), InvoiceResponse.class);

        assertNotNull(first);
        assertNotNull(second);
        assertEquals(first.id(), second.id());
        assertEquals(HttpStatus.OK, HttpStatus.OK);
    }

    private HttpHeaders authHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return headers;
    }
}
