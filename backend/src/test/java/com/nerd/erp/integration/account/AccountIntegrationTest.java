package com.nerd.erp.integration.account;

import com.nerd.erp.api.account.AccountDtos.ActivateRequest;
import com.nerd.erp.api.account.AccountDtos.ChangePasswordRequest;
import com.nerd.erp.api.account.AccountDtos.ForgotPasswordRequest;
import com.nerd.erp.api.account.AccountDtos.ProfileResponse;
import com.nerd.erp.api.account.AccountDtos.ProfileUpdateRequest;
import com.nerd.erp.api.account.AccountDtos.ResetPasswordRequest;
import com.nerd.erp.api.account.AccountDtos.TokenInfoResponse;
import com.nerd.erp.api.auth.LoginRequest;
import com.nerd.erp.api.auth.LoginResponse;
import com.nerd.erp.api.users.TenantUserDtos.InvitationResult;
import com.nerd.erp.api.users.TenantUserDtos.InviteUserRequest;
import com.nerd.erp.api.users.TenantUserDtos.TenantUserResponse;
import com.nerd.erp.api.users.TenantUserDtos.TenantUserUpdateRequest;
import com.nerd.erp.application.account.AccountTokenService;
import com.nerd.erp.domain.account.AccountTokenKind;
import com.nerd.erp.domain.tenancy.TenantRole;
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
import org.springframework.http.client.JdkClientHttpRequestFactory;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Lot 2 : invitation d'un utilisateur par un administrateur du tenant, activation par lien à usage
 * unique, gestion des rôles / accès, profil, changement et réinitialisation de mot de passe.
 * La boîte plateforme n'est pas configurée en test : le lien d'activation est rendu à l'administrateur.
 */
class AccountIntegrationTest extends PostgresIntegrationTest {

    @Autowired
    TestRestTemplate restTemplate;
    @Autowired
    AccountTokenService tokenService;

    @BeforeEach
    void usePatchCapableClient() {
        restTemplate.getRestTemplate().setRequestFactory(new JdkClientHttpRequestFactory());
    }

    @Test
    void inviteActivateManageAndResetPassword() {
        String adminToken = login("admin@nerd.local", "Admin123!", "test-tenant");
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String email = "invite-" + suffix + "@client.test";

        // Invitation : compte créé sans mot de passe, lien d'activation rendu faute de boîte plateforme.
        ResponseEntity<InvitationResult> invited = restTemplate.postForEntity("/api/v1/users/invitations",
                new HttpEntity<>(new InviteUserRequest(email, "Invité " + suffix, TenantRole.USER), bearer(adminToken)),
                InvitationResult.class);
        assertEquals(HttpStatus.CREATED, invited.getStatusCode());
        InvitationResult invitation = invited.getBody();
        assertNotNull(invitation);
        assertFalse(invitation.emailSent());
        assertNotNull(invitation.activationUrl());
        assertTrue(invitation.user().pending());
        UUID userId = invitation.user().userId();

        // Doublon refusé ; la liste montre l'invité en attente et l'admin comme « self ».
        assertEquals(HttpStatus.CONFLICT, restTemplate.postForEntity("/api/v1/users/invitations",
                new HttpEntity<>(new InviteUserRequest(email, null, TenantRole.USER), bearer(adminToken)),
                String.class).getStatusCode());
        List<TenantUserResponse> users = restTemplate.exchange("/api/v1/users", HttpMethod.GET,
                new HttpEntity<>(bearer(adminToken)), new ParameterizedTypeReference<List<TenantUserResponse>>() {
                }).getBody();
        assertNotNull(users);
        assertTrue(users.stream().anyMatch(u -> u.userId().equals(userId) && u.pending()));
        assertTrue(users.stream().anyMatch(u -> u.email().equals("admin@nerd.local") && u.self()));

        // Impossible de se connecter avant activation.
        assertEquals(HttpStatus.UNAUTHORIZED, restTemplate.postForEntity("/api/v1/auth/login",
                new LoginRequest(email, "Whatever123!", "test-tenant"), String.class).getStatusCode());

        // La page publique lit le jeton, puis l'activation ouvre une session sur le tenant d'invitation.
        String token = tokenFrom(invitation.activationUrl());
        TokenInfoResponse info = restTemplate.getForObject("/api/v1/auth/public/token-info?token=" + token,
                TokenInfoResponse.class);
        assertNotNull(info);
        assertEquals(AccountTokenKind.INVITATION, info.kind());
        assertEquals(email, info.email());
        assertEquals("test-tenant", info.tenantSlug());

        ResponseEntity<LoginResponse> activated = restTemplate.postForEntity("/api/v1/auth/public/activate",
                new ActivateRequest(token, "Jean Invité", "Invite123!"), LoginResponse.class);
        assertEquals(HttpStatus.OK, activated.getStatusCode());
        LoginResponse session = activated.getBody();
        assertNotNull(session);
        assertEquals("test-tenant", session.tenantSlug());
        assertEquals("Jean Invité", session.displayName());
        assertEquals(TenantRole.USER, session.role());

        // Jeton à usage unique.
        ResponseEntity<String> replay = restTemplate.postForEntity("/api/v1/auth/public/activate",
                new ActivateRequest(token, null, "Other123!"), String.class);
        assertEquals(HttpStatus.BAD_REQUEST, replay.getStatusCode());
        assertTrue(replay.getBody().contains("TOKEN_INVALID"));

        // Simple utilisateur : pas d'accès à la gestion des utilisateurs, mais à son profil.
        assertEquals(HttpStatus.FORBIDDEN, restTemplate.exchange("/api/v1/users", HttpMethod.GET,
                new HttpEntity<>(bearer(session.accessToken())), String.class).getStatusCode());
        ProfileResponse profile = restTemplate.exchange("/api/v1/account/profile", HttpMethod.PUT,
                new HttpEntity<>(new ProfileUpdateRequest("Jean Dupont"), bearer(session.accessToken())),
                ProfileResponse.class).getBody();
        assertNotNull(profile);
        assertEquals("Jean Dupont", profile.displayName());
        assertNotNull(profile.lastLoginAt());

        // Changement de mot de passe : ancien requis.
        assertEquals(HttpStatus.BAD_REQUEST, restTemplate.postForEntity("/api/v1/account/password",
                new HttpEntity<>(new ChangePasswordRequest("Wrong123!", "Changed123!"), bearer(session.accessToken())),
                String.class).getStatusCode());
        assertEquals(HttpStatus.NO_CONTENT, restTemplate.postForEntity("/api/v1/account/password",
                new HttpEntity<>(new ChangePasswordRequest("Invite123!", "Changed123!"), bearer(session.accessToken())),
                String.class).getStatusCode());
        login(email, "Changed123!", "test-tenant");

        // Règles d'administration : pas d'auto-modification, promotion OWNER réservée aux OWNER (l'admin l'est).
        UUID adminId = UUID.fromString((String) me(adminToken).get("userId"));
        assertEquals(HttpStatus.BAD_REQUEST, restTemplate.exchange("/api/v1/users/" + adminId, HttpMethod.PATCH,
                new HttpEntity<>(new TenantUserUpdateRequest(TenantRole.USER, null), bearer(adminToken)),
                String.class).getStatusCode());
        TenantUserResponse promoted = restTemplate.exchange("/api/v1/users/" + userId, HttpMethod.PATCH,
                new HttpEntity<>(new TenantUserUpdateRequest(TenantRole.ADMIN, null), bearer(adminToken)),
                TenantUserResponse.class).getBody();
        assertNotNull(promoted);
        assertEquals(TenantRole.ADMIN, promoted.role());
        assertFalse(promoted.pending());

        // Mot de passe oublié : toujours 202, même pour une adresse inconnue.
        assertEquals(HttpStatus.ACCEPTED, restTemplate.postForEntity("/api/v1/auth/public/forgot-password",
                new ForgotPasswordRequest("nobody-" + suffix + "@client.test"), String.class).getStatusCode());
        assertEquals(HttpStatus.ACCEPTED, restTemplate.postForEntity("/api/v1/auth/public/forgot-password",
                new ForgotPasswordRequest(email), String.class).getStatusCode());

        // Réinitialisation avec un jeton émis côté serveur (l'e-mail n'est pas envoyé en test).
        String resetToken = tokenService.issue(userId, null, AccountTokenKind.PASSWORD_RESET, null);
        TokenInfoResponse resetInfo = restTemplate.getForObject("/api/v1/auth/public/token-info?token=" + resetToken,
                TokenInfoResponse.class);
        assertNotNull(resetInfo);
        assertEquals(AccountTokenKind.PASSWORD_RESET, resetInfo.kind());
        assertNull(resetInfo.tenantSlug());
        assertEquals(HttpStatus.BAD_REQUEST, restTemplate.postForEntity("/api/v1/auth/public/activate",
                new ActivateRequest(resetToken, null, "Nope12345"), String.class).getStatusCode());
        assertEquals(HttpStatus.NO_CONTENT, restTemplate.postForEntity("/api/v1/auth/public/reset-password",
                new ResetPasswordRequest(resetToken, "Reset1234!"), String.class).getStatusCode());
        login(email, "Reset1234!", "test-tenant");

        // Retrait de l'accès : le compte global subsiste mais le login sur ce tenant est refusé.
        assertEquals(HttpStatus.NO_CONTENT, restTemplate.exchange("/api/v1/users/" + userId, HttpMethod.DELETE,
                new HttpEntity<>(bearer(adminToken)), String.class).getStatusCode());
        assertEquals(HttpStatus.FORBIDDEN, restTemplate.postForEntity("/api/v1/auth/login",
                new LoginRequest(email, "Reset1234!", "test-tenant"), String.class).getStatusCode());

        // Le dernier OWNER actif ne peut pas être rétrogradé par un autre admin.
        assertEquals(HttpStatus.BAD_REQUEST, restTemplate.exchange("/api/v1/users/" + adminId, HttpMethod.DELETE,
                new HttpEntity<>(bearer(adminToken)), String.class).getStatusCode());
    }

    private static String tokenFrom(String url) {
        return url.substring(url.indexOf("token=") + "token=".length());
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
