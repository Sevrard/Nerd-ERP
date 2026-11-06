package com.nerd.erp.integration.tenancy;

import com.nerd.erp.api.auth.LoginRequest;
import com.nerd.erp.api.auth.LoginResponse;
import com.nerd.erp.api.customer.CustomerRequest;
import com.nerd.erp.api.workspace.WorkspaceResponse;
import com.nerd.erp.domain.customer.CustomerStatus;
import com.nerd.erp.domain.customer.CustomerType;
import com.nerd.erp.domain.tenancy.AppUser;
import com.nerd.erp.domain.tenancy.Tenant;
import com.nerd.erp.domain.tenancy.TenantMembership;
import com.nerd.erp.domain.tenancy.TenantRole;
import com.nerd.erp.infrastructure.persistence.tenancy.AppUserRepository;
import com.nerd.erp.infrastructure.persistence.tenancy.TenantMembershipRepository;
import com.nerd.erp.infrastructure.persistence.tenancy.TenantRepository;
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
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Vérifie les gardes déclaratives {@code @RequireWriteAccess} / {@code @RequireAdminAccess}. */
class AuthorizationIntegrationTest extends PostgresIntegrationTest {

    @Autowired
    TestRestTemplate restTemplate;
    @Autowired
    TenantRepository tenantRepository;
    @Autowired
    AppUserRepository appUserRepository;
    @Autowired
    TenantMembershipRepository membershipRepository;
    @Autowired
    PasswordEncoder passwordEncoder;

    String readonlyToken;
    String userToken;
    UUID workspaceId;

    @BeforeEach
    void setUp() {
        Tenant tenant = tenantRepository.findBySlug("test-tenant").orElseThrow();
        readonlyToken = tokenFor(tenant, TenantRole.READONLY);
        userToken = tokenFor(tenant, TenantRole.USER);

        HttpHeaders headers = bearer(readonlyToken);
        ResponseEntity<List<WorkspaceResponse>> workspaces = restTemplate.exchange(
                "/api/v1/workspaces", HttpMethod.GET, new HttpEntity<>(headers),
                new ParameterizedTypeReference<>() {
                });
        assertEquals(HttpStatus.OK, workspaces.getStatusCode());
        workspaceId = workspaces.getBody().getFirst().id();
    }

    @Test
    void readonlyUserCannotCreateCustomer() {
        CustomerRequest request = new CustomerRequest(
                workspaceId, CustomerType.COMPANY, "Forbidden Corp", null, null,
                null, null, null, null, null, null, "FR", CustomerStatus.PROSPECT, null, null);
        ResponseEntity<String> response = restTemplate.exchange(
                "/api/v1/customers", HttpMethod.POST, new HttpEntity<>(request, bearer(readonlyToken)), String.class);
        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
    }

    @Test
    void readonlyUserCanStillRead() {
        ResponseEntity<String> response = restTemplate.exchange(
                "/api/v1/customers?workspaceId=" + workspaceId, HttpMethod.GET,
                new HttpEntity<>(bearer(readonlyToken)), String.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    void regularUserCannotAdministerWorkspaces() {
        Map<String, Object> body = Map.of("name", "Hack", "countryCode", "FR", "currencyCode", "EUR",
                "invoicePrefix", "FAC", "quotationPrefix", "DEV");
        ResponseEntity<String> response = restTemplate.exchange(
                "/api/v1/workspaces/" + workspaceId, HttpMethod.PUT,
                new HttpEntity<>(body, bearer(userToken)), String.class);
        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());

        ResponseEntity<String> delete = restTemplate.exchange(
                "/api/v1/workspaces/" + workspaceId, HttpMethod.DELETE,
                new HttpEntity<>(bearer(userToken)), String.class);
        assertEquals(HttpStatus.FORBIDDEN, delete.getStatusCode());
    }

    private String tokenFor(Tenant tenant, TenantRole role) {
        String email = role.name().toLowerCase() + "-" + UUID.randomUUID().toString().substring(0, 8) + "@nerd.local";
        AppUser user = new AppUser();
        user.setEmail(email);
        user.setDisplayName(role.name());
        user.setPasswordHash(passwordEncoder.encode("Secret123!"));
        user = appUserRepository.save(user);

        TenantMembership membership = new TenantMembership();
        membership.setTenantId(tenant.getId());
        membership.setUserId(user.getId());
        membership.setRole(role);
        membershipRepository.save(membership);

        LoginResponse login = restTemplate.postForObject("/api/v1/auth/login",
                new LoginRequest(email, "Secret123!", tenant.getSlug()), LoginResponse.class);
        return login.accessToken();
    }

    private static HttpHeaders bearer(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return headers;
    }
}
