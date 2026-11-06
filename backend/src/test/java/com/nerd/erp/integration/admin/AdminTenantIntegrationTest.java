package com.nerd.erp.integration.admin;

import com.nerd.erp.api.admin.AdminDtos.ModuleUpdate;
import com.nerd.erp.api.admin.AdminDtos.ModulesUpdateRequest;
import com.nerd.erp.api.admin.AdminDtos.TenantCreateRequest;
import com.nerd.erp.api.admin.AdminDtos.TenantDetail;
import com.nerd.erp.api.admin.AdminDtos.TenantSummary;
import com.nerd.erp.api.admin.AdminDtos.TenantUpdateRequest;
import com.nerd.erp.api.auth.LoginRequest;
import com.nerd.erp.api.auth.LoginResponse;
import com.nerd.erp.api.shared.PageResponse;
import com.nerd.erp.api.workspace.WorkspaceResponse;
import com.nerd.erp.domain.module.ModuleCode;
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
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Lot 5/6 : administration plateforme (onboarding d'un tenant client, activation de modules, suspension)
 * et application des droits côté tenant (403 MODULE_DISABLED / TENANT_SUSPENDED, /auth/me).
 */
class AdminTenantIntegrationTest extends PostgresIntegrationTest {

    @Autowired
    TestRestTemplate restTemplate;

    @BeforeEach
    void usePatchCapableClient() {
        // Le client HTTP JDK par défaut (HttpURLConnection) ne supporte pas PATCH.
        restTemplate.getRestTemplate().setRequestFactory(new JdkClientHttpRequestFactory());
    }

    @Test
    void platformAdminOnboardsTenantAndControlsModules() {
        String adminToken = login("admin@nerd.local", "Admin123!", "test-tenant");

        // L'admin bootstrap est administrateur plateforme et voit tous les modules actifs.
        Map<String, Object> adminMe = me(adminToken);
        assertEquals(true, adminMe.get("platformAdmin"));
        assertTrue(((List<?>) adminMe.get("modules")).contains("MAINTENANCE"));

        // Onboarding d'un client : tenant + organisation + workspace + utilisateur OWNER, module MAINTENANCE seul.
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String ownerEmail = "owner-" + suffix + "@client.test";
        ResponseEntity<TenantDetail> created = restTemplate.postForEntity("/api/v1/admin/tenants",
                new HttpEntity<>(new TenantCreateRequest("Client " + suffix, null, null, ownerEmail, "Owner",
                        "Owner123!!", null, List.of(ModuleCode.MAINTENANCE)), bearer(adminToken)),
                TenantDetail.class);
        assertEquals(HttpStatus.CREATED, created.getStatusCode());
        TenantDetail tenant = created.getBody();
        assertNotNull(tenant);
        assertEquals("client-" + suffix, tenant.slug());
        assertEquals(1, tenant.workspaceCount());
        assertEquals(1, tenant.users().size());
        assertEquals(ownerEmail, tenant.users().getFirst().email());
        assertTrue(tenant.modules().stream().anyMatch(m -> m.code() == ModuleCode.MAINTENANCE && m.currentlyActive()));
        assertTrue(tenant.modules().stream().anyMatch(m -> m.code() == ModuleCode.INVOICE_SCAN && !m.enabled()));

        // Slug dupliqué refusé.
        ResponseEntity<String> duplicate = restTemplate.postForEntity("/api/v1/admin/tenants",
                new HttpEntity<>(new TenantCreateRequest("Client " + suffix, null, null, ownerEmail, null,
                        null, null, null), bearer(adminToken)), String.class);
        assertEquals(HttpStatus.CONFLICT, duplicate.getStatusCode());

        // Le nouveau client se connecte : pas admin plateforme, module MAINTENANCE actif, pas INVOICE_SCAN.
        String ownerToken = login(ownerEmail, "Owner123!!", tenant.slug());
        Map<String, Object> ownerMe = me(ownerToken);
        assertEquals(false, ownerMe.get("platformAdmin"));
        assertEquals(List.of("MAINTENANCE"), ownerMe.get("modules"));
        ResponseEntity<String> adminForbidden = restTemplate.exchange("/api/v1/admin/tenants", HttpMethod.GET,
                new HttpEntity<>(bearer(ownerToken)), String.class);
        assertEquals(HttpStatus.FORBIDDEN, adminForbidden.getStatusCode());

        ResponseEntity<List<WorkspaceResponse>> workspaces = restTemplate.exchange("/api/v1/workspaces",
                HttpMethod.GET, new HttpEntity<>(bearer(ownerToken)), new ParameterizedTypeReference<>() {
                });
        UUID workspaceId = workspaces.getBody().getFirst().id();

        ResponseEntity<String> maintenanceOk = restTemplate.exchange(
                "/api/v1/maintenance-contracts?workspaceId=" + workspaceId, HttpMethod.GET,
                new HttpEntity<>(bearer(ownerToken)), String.class);
        assertEquals(HttpStatus.OK, maintenanceOk.getStatusCode());

        ResponseEntity<String> merchantsDenied = restTemplate.exchange(
                "/api/v1/merchants?workspaceId=" + workspaceId, HttpMethod.GET,
                new HttpEntity<>(bearer(ownerToken)), String.class);
        assertEquals(HttpStatus.FORBIDDEN, merchantsDenied.getStatusCode());
        assertTrue(merchantsDenied.getBody().contains("MODULE_DISABLED"));

        // L'éditeur active INVOICE_SCAN et désactive MAINTENANCE : effet immédiat (cache invalidé).
        TenantDetail updated = restTemplate.exchange("/api/v1/admin/tenants/" + tenant.id() + "/modules",
                HttpMethod.PUT, new HttpEntity<>(new ModulesUpdateRequest(List.of(
                        new ModuleUpdate(ModuleCode.INVOICE_SCAN, true, null),
                        new ModuleUpdate(ModuleCode.MAINTENANCE, false, null))), bearer(adminToken)),
                TenantDetail.class).getBody();
        assertNotNull(updated);
        assertTrue(updated.modules().stream().anyMatch(m -> m.code() == ModuleCode.INVOICE_SCAN && m.currentlyActive()));

        assertEquals(HttpStatus.OK, restTemplate.exchange("/api/v1/merchants?workspaceId=" + workspaceId,
                HttpMethod.GET, new HttpEntity<>(bearer(ownerToken)), String.class).getStatusCode());
        assertEquals(HttpStatus.FORBIDDEN, restTemplate.exchange(
                "/api/v1/maintenance-contracts?workspaceId=" + workspaceId, HttpMethod.GET,
                new HttpEntity<>(bearer(ownerToken)), String.class).getStatusCode());

        // Liste admin : le nouveau tenant apparaît avec ses modules actifs.
        ResponseEntity<PageResponse<TenantSummary>> list = restTemplate.exchange(
                "/api/v1/admin/tenants?q=" + suffix, HttpMethod.GET, new HttpEntity<>(bearer(adminToken)),
                new ParameterizedTypeReference<>() {
                });
        assertEquals(1, list.getBody().totalElements());
        TenantSummary summary = list.getBody().content().getFirst();
        assertEquals(1, summary.userCount());
        assertEquals(List.of(ModuleCode.INVOICE_SCAN), summary.activeModules());

        // Suspension : le JWT du client reste valide mais toute requête est refusée, et le login échoue.
        TenantDetail suspended = restTemplate.exchange("/api/v1/admin/tenants/" + tenant.id(), HttpMethod.PATCH,
                new HttpEntity<>(new TenantUpdateRequest(null, false, null, null, null), bearer(adminToken)), TenantDetail.class)
                .getBody();
        assertNotNull(suspended);
        assertFalse(suspended.active());

        ResponseEntity<String> suspendedCall = restTemplate.exchange("/api/v1/workspaces", HttpMethod.GET,
                new HttpEntity<>(bearer(ownerToken)), String.class);
        assertEquals(HttpStatus.FORBIDDEN, suspendedCall.getStatusCode());
        assertTrue(suspendedCall.getBody().contains("TENANT_SUSPENDED"));
        assertEquals(HttpStatus.UNAUTHORIZED, restTemplate.postForEntity("/api/v1/auth/login",
                new LoginRequest(ownerEmail, "Owner123!!", tenant.slug()), String.class).getStatusCode());

        // L'éditeur ne peut pas suspendre son propre tenant.
        UUID adminTenantId = UUID.fromString((String) adminMe.get("tenantId"));
        ResponseEntity<String> selfSuspend = restTemplate.exchange("/api/v1/admin/tenants/" + adminTenantId,
                HttpMethod.PATCH, new HttpEntity<>(new TenantUpdateRequest(null, false, null, null, null), bearer(adminToken)),
                String.class);
        assertEquals(HttpStatus.BAD_REQUEST, selfSuspend.getStatusCode());
    }

    private String login(String email, String password, String slug) {
        ResponseEntity<LoginResponse> response = restTemplate.postForEntity("/api/v1/auth/login",
                new LoginRequest(email, password, slug), LoginResponse.class);
        assertEquals(HttpStatus.OK, response.getStatusCode(), "login " + email + " @ " + slug);
        return response.getBody().accessToken();
    }

    private Map<String, Object> me(String token) {
        return restTemplate.exchange("/api/v1/auth/me", HttpMethod.GET, new HttpEntity<>(bearer(token)),
                new ParameterizedTypeReference<Map<String, Object>>() {
                }).getBody();
    }

    private static HttpHeaders bearer(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return headers;
    }
}
