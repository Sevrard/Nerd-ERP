package com.nerd.erp.integration.onboarding;

import com.nerd.erp.api.account.AccountDtos.LoginBrandingResponse;
import com.nerd.erp.api.account.AccountDtos.TermsStatusResponse;
import com.nerd.erp.api.admin.AdminDtos.TenantCreateRequest;
import com.nerd.erp.api.admin.AdminDtos.TenantDetail;
import com.nerd.erp.api.admin.AdminDtos.TenantUpdateRequest;
import com.nerd.erp.api.auth.LoginRequest;
import com.nerd.erp.api.auth.LoginResponse;
import com.nerd.erp.api.catalog.ProductCategoryResponse;
import com.nerd.erp.api.catalog.ProductResponse;
import com.nerd.erp.api.customer.CustomerResponse;
import com.nerd.erp.api.importer.CsvImportResult;
import com.nerd.erp.api.shared.PageResponse;
import com.nerd.erp.api.workspace.WorkspaceResponse;
import com.nerd.erp.application.onboarding.OnboardingService.Checklist;
import com.nerd.erp.application.onboarding.OnboardingService.Step;
import com.nerd.erp.support.PostgresIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Lot 8 : import CSV clients/produits, branding de connexion, CGU, checklist de démarrage. */
class OnboardingIntegrationTest extends PostgresIntegrationTest {

    @Autowired
    TestRestTemplate restTemplate;

    String adminToken;
    String ownerToken;
    String tenantSlug;
    UUID tenantId;
    UUID workspaceId;

    @BeforeEach
    void setUp() {
        restTemplate.getRestTemplate().setRequestFactory(new JdkClientHttpRequestFactory());
        adminToken = login("admin@nerd.local", "Admin123!", "test-tenant");
        // Tenant vierge pour une checklist déterministe.
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String ownerEmail = "owner-" + suffix + "@onboard.test";
        TenantDetail tenant = restTemplate.postForEntity("/api/v1/admin/tenants",
                new HttpEntity<>(new TenantCreateRequest("Onboard " + suffix, null, null, ownerEmail, "Owner",
                        "Owner123!!", null, null), bearer(adminToken)), TenantDetail.class).getBody();
        tenantSlug = tenant.slug();
        tenantId = tenant.id();
        ownerToken = login(ownerEmail, "Owner123!!", tenantSlug);
        ResponseEntity<List<WorkspaceResponse>> workspaces = restTemplate.exchange("/api/v1/workspaces",
                HttpMethod.GET, new HttpEntity<>(bearer(ownerToken)), new ParameterizedTypeReference<>() {
                });
        workspaceId = workspaces.getBody().getFirst().id();
    }

    @Test
    void csvImportsAreIdempotentAndPreviewable() {
        String customers = "\uFEFFType;Raison sociale;Prénom;Nom;E-mail;Téléphone;Code postal;Ville;Statut\n"
                + "societe;ACME SAS;;;contact@acme.test;01 02 03 04 05;75001;Paris;client\n"
                + "particulier;;Marie;Dupont;marie@example.test;;69006;Lyon;prospect\n"
                + "societe;;;;;;;;client\n"; // raison sociale manquante → erreur ligne 4

        CsvImportResult dry = upload("/api/v1/customers/import", customers, true);
        assertTrue(dry.dryRun());
        assertEquals(3, dry.totalRows());
        assertEquals(2, dry.created());
        assertEquals(1, dry.errors().size());
        assertEquals(4, dry.errors().getFirst().line());
        PageResponse<CustomerResponse> none = restTemplate.exchange(
                "/api/v1/customers?workspaceId=" + workspaceId, HttpMethod.GET, new HttpEntity<>(bearer(ownerToken)),
                new ParameterizedTypeReference<PageResponse<CustomerResponse>>() {
                }).getBody();
        assertEquals(0, none.totalElements(), "le dry-run n'écrit rien");

        CsvImportResult real = upload("/api/v1/customers/import", customers, false);
        assertEquals(2, real.created());
        CsvImportResult again = upload("/api/v1/customers/import", customers.replace("75001;Paris", "75002;Paris"), false);
        assertEquals(0, again.created());
        assertEquals(1, again.updated(), "ACME retrouvée par e-mail et mise à jour");
        assertEquals(1, again.skipped());
        PageResponse<CustomerResponse> page = restTemplate.exchange(
                "/api/v1/customers?workspaceId=" + workspaceId, HttpMethod.GET, new HttpEntity<>(bearer(ownerToken)),
                new ParameterizedTypeReference<PageResponse<CustomerResponse>>() {
                }).getBody();
        assertEquals(2, page.totalElements());

        String products = "reference,nom,description,prix_ht,tva,categorie,facturation,unite,actif\n"
                + "DEV-J,\"Développement, sur mesure\",Journée,650,20,Développement,jour,,oui\n"
                + "STICKER,Sticker A6,,\"2,50\",20,Boutique,unite,pièce,oui\n"
                + "BAD,Sans prix,,,20,,unite,,oui\n";
        CsvImportResult prod = upload("/api/v1/products/import", products, false);
        assertEquals(2, prod.created());
        assertEquals(1, prod.errors().size());
        List<ProductCategoryResponse> categories = restTemplate.exchange(
                "/api/v1/product-categories?workspaceId=" + workspaceId, HttpMethod.GET,
                new HttpEntity<>(bearer(ownerToken)), new ParameterizedTypeReference<List<ProductCategoryResponse>>() {
                }).getBody();
        assertEquals(2, categories.size(), "catégories créées à la volée");
        PageResponse<ProductResponse> productPage = restTemplate.exchange(
                "/api/v1/products?workspaceId=" + workspaceId + "&activeOnly=false", HttpMethod.GET,
                new HttpEntity<>(bearer(ownerToken)), new ParameterizedTypeReference<PageResponse<ProductResponse>>() {
                }).getBody();
        ProductResponse sticker = productPage.content().stream().filter(p -> p.referenceCode().equals("STICKER")).findFirst().orElseThrow();
        assertEquals(0, new BigDecimal("2.50").compareTo(sticker.unitPriceHt()));
        assertEquals("Boutique", sticker.categoryLabel());
        ProductResponse dev = productPage.content().stream().filter(p -> p.referenceCode().equals("DEV-J")).findFirst().orElseThrow();
        assertEquals("Développement, sur mesure", dev.name());

        CsvImportResult prodAgain = upload("/api/v1/products/import",
                "reference;prix_ht\nDEV-J;700\n", false);
        assertEquals(1, prodAgain.updated());
        assertEquals(0, prodAgain.created());

        ResponseEntity<byte[]> template = restTemplate.exchange("/api/v1/customers/import/template", HttpMethod.GET,
                new HttpEntity<>(bearer(ownerToken)), byte[].class);
        assertEquals(HttpStatus.OK, template.getStatusCode());
        assertTrue(new String(template.getBody(), StandardCharsets.UTF_8).contains("raison_sociale"));
    }

    @Test
    void brandingTermsAndChecklist() {
        // Branding : neutre tant que non configuré ; inconnu → neutre aussi.
        LoginBrandingResponse before = restTemplate.getForObject("/api/v1/auth/public/branding?tenant=" + tenantSlug,
                LoginBrandingResponse.class);
        assertEquals(tenantSlug, before.tenantSlug());
        assertNull(before.brandColor());
        LoginBrandingResponse unknown = restTemplate.getForObject("/api/v1/auth/public/branding?tenant=nope-" + tenantSlug,
                LoginBrandingResponse.class);
        assertNull(unknown.tenantSlug());

        ResponseEntity<String> badColor = restTemplate.exchange("/api/v1/admin/tenants/" + tenantId, HttpMethod.PATCH,
                new HttpEntity<>(new TenantUpdateRequest(null, null, "rouge", null, null), bearer(adminToken)), String.class);
        assertEquals(HttpStatus.BAD_REQUEST, badColor.getStatusCode());

        TenantDetail branded = restTemplate.exchange("/api/v1/admin/tenants/" + tenantId, HttpMethod.PATCH,
                new HttpEntity<>(new TenantUpdateRequest(null, null, "#FF6600", "https://cdn.example.test/logo.png",
                        "Bienvenue chez Onboard"), bearer(adminToken)), TenantDetail.class).getBody();
        assertEquals("#ff6600", branded.brandColor());
        LoginBrandingResponse after = restTemplate.getForObject("/api/v1/auth/public/branding?tenant=" + tenantSlug,
                LoginBrandingResponse.class);
        assertEquals("#ff6600", after.brandColor());
        assertEquals("https://cdn.example.test/logo.png", after.logoUrl());
        assertEquals("Bienvenue chez Onboard", after.tagline());

        // CGU : non acceptées au départ, exposées dans /me, acceptation idempotente.
        Map<String, Object> me = restTemplate.exchange("/api/v1/auth/me", HttpMethod.GET,
                new HttpEntity<>(bearer(ownerToken)), new ParameterizedTypeReference<Map<String, Object>>() {
                }).getBody();
        @SuppressWarnings("unchecked")
        Map<String, Object> terms = (Map<String, Object>) me.get("terms");
        assertEquals(false, terms.get("accepted"));
        assertNotNull(terms.get("currentVersion"));

        Checklist checklist = restTemplate.exchange("/api/v1/onboarding/checklist?workspaceId=" + workspaceId,
                HttpMethod.GET, new HttpEntity<>(bearer(ownerToken)), Checklist.class).getBody();
        assertEquals(7, checklist.total());
        assertEquals(0, checklist.done());
        assertFalse(step(checklist, "TERMS").done());
        assertFalse(step(checklist, "CUSTOMER").done());

        TermsStatusResponse accepted = restTemplate.postForObject("/api/v1/account/terms/accept",
                new HttpEntity<>(bearer(ownerToken)), TermsStatusResponse.class);
        assertTrue(accepted.accepted());
        assertNotNull(accepted.acceptedAt());
        TermsStatusResponse acceptedAgain = restTemplate.postForObject("/api/v1/account/terms/accept",
                new HttpEntity<>(bearer(ownerToken)), TermsStatusResponse.class);
        assertEquals(accepted.acceptedAt(), acceptedAgain.acceptedAt());

        upload("/api/v1/customers/import", "nom;email\nDurand;durand@example.test\n", false);
        Checklist progressed = restTemplate.exchange("/api/v1/onboarding/checklist?workspaceId=" + workspaceId,
                HttpMethod.GET, new HttpEntity<>(bearer(ownerToken)), Checklist.class).getBody();
        assertEquals(2, progressed.done());
        assertTrue(step(progressed, "TERMS").done());
        assertTrue(step(progressed, "CUSTOMER").done());
    }

    private static Step step(Checklist c, String code) {
        return c.steps().stream().filter(s -> s.code().equals(code)).findFirst().orElseThrow();
    }

    private CsvImportResult upload(String path, String csv, boolean dryRun) {
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new ByteArrayResource(csv.getBytes(StandardCharsets.UTF_8)) {
            @Override
            public String getFilename() {
                return "import.csv";
            }
        });
        HttpHeaders headers = bearer(ownerToken);
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        ResponseEntity<CsvImportResult> response = restTemplate.postForEntity(
                path + "?workspaceId=" + workspaceId + "&dryRun=" + dryRun, new HttpEntity<>(body, headers),
                CsvImportResult.class);
        assertEquals(HttpStatus.OK, response.getStatusCode(), () -> "import " + path);
        return response.getBody();
    }

    private String login(String email, String password, String slug) {
        LoginResponse login = restTemplate.postForObject("/api/v1/auth/login",
                new LoginRequest(email, password, slug), LoginResponse.class);
        assertNotNull(login, "login " + email);
        return login.accessToken();
    }

    private static HttpHeaders bearer(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return headers;
    }
}
