package com.nerd.erp.integration.tenancy;

import com.nerd.erp.domain.customer.Customer;
import com.nerd.erp.domain.customer.CustomerStatus;
import com.nerd.erp.domain.customer.CustomerType;
import com.nerd.erp.infrastructure.persistence.customer.CustomerRepository;
import com.nerd.erp.domain.organization.Organization;
import com.nerd.erp.infrastructure.persistence.organization.OrganizationRepository;
import com.nerd.erp.api.auth.LoginRequest;
import com.nerd.erp.api.auth.LoginResponse;
import com.nerd.erp.support.PostgresIntegrationTest;
import com.nerd.erp.domain.tenancy.Tenant;
import com.nerd.erp.infrastructure.persistence.tenancy.TenantRepository;
import com.nerd.erp.domain.workspace.Workspace;
import com.nerd.erp.infrastructure.persistence.workspace.WorkspaceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TenantIsolationIntegrationTest extends PostgresIntegrationTest {

    @Autowired
    TestRestTemplate restTemplate;
    @Autowired
    TenantRepository tenantRepository;
    @Autowired
    OrganizationRepository organizationRepository;
    @Autowired
    WorkspaceRepository workspaceRepository;
    @Autowired
    CustomerRepository customerRepository;
    UUID customerBId;
    String tokenTenantA;

    @BeforeEach
    void setUp() {
        LoginResponse loginA = restTemplate.postForObject("/api/v1/auth/login",
                new LoginRequest("admin@nerd.local", "Admin123!", "test-tenant"), LoginResponse.class);
        tokenTenantA = loginA.accessToken();

        Tenant tenantB = new Tenant();
        tenantB.setName("Tenant B");
        tenantB.setSlug("tenant-b-" + UUID.randomUUID().toString().substring(0, 8));
        tenantB = tenantRepository.save(tenantB);

        Organization org = new Organization();
        org.setTenantId(tenantB.getId());
        org.setLegalName("Org B");
        org = organizationRepository.save(org);

        Workspace ws = new Workspace();
        ws.setTenantId(tenantB.getId());
        ws.setOrganizationId(org.getId());
        ws.setName("WS B");
        ws.setSlug("ws-b-" + UUID.randomUUID().toString().substring(0, 6));
        ws = workspaceRepository.save(ws);

        Customer customer = new Customer();
        customer.setTenantId(tenantB.getId());
        customer.setWorkspaceId(ws.getId());
        customer.setCustomerType(CustomerType.COMPANY);
        customer.setCompanyName("Secret B");
        customer.setStatus(CustomerStatus.PROSPECT);
        customer = customerRepository.save(customer);
        customerBId = customer.getId();
    }

    @Test
    void cannotReadCustomerFromAnotherTenant() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(tokenTenantA);
        ResponseEntity<String> response = restTemplate.exchange(
                "/api/v1/customers/" + customerBId,
                HttpMethod.GET,
                new HttpEntity<>(headers),
                String.class);
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }
}
