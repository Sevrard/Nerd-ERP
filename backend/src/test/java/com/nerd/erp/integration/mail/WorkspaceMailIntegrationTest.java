package com.nerd.erp.integration.mail;

import com.icegreen.greenmail.junit5.GreenMailExtension;
import com.icegreen.greenmail.util.ServerSetup;
import com.nerd.erp.api.auth.LoginRequest;
import com.nerd.erp.api.auth.LoginResponse;
import com.nerd.erp.api.mail.MailDtos.EmailLogResponse;
import com.nerd.erp.api.mail.MailDtos.MailTestRequest;
import com.nerd.erp.api.mail.MailDtos.MailTestResponse;
import com.nerd.erp.api.mail.MailDtos.WorkspaceMailSettingsRequest;
import com.nerd.erp.api.mail.MailDtos.WorkspaceMailSettingsResponse;
import com.nerd.erp.api.shared.PageResponse;
import com.nerd.erp.api.workspace.WorkspaceResponse;
import com.nerd.erp.application.mail.MailService;
import com.nerd.erp.application.mail.OutgoingEmail;
import com.nerd.erp.domain.mail.EmailKind;
import com.nerd.erp.domain.mail.EmailStatus;
import com.nerd.erp.domain.mail.SmtpSecurity;
import com.nerd.erp.infrastructure.persistence.mail.EmailLogRepository;
import com.nerd.erp.support.PostgresIntegrationTest;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Lot 1 : messagerie par workspace. Un serveur SMTP local (GreenMail) joue le rôle de la boîte Gmail /
 * Outlook du client : enregistrement des paramètres (mot de passe chiffré, jamais renvoyé), test d'envoi,
 * envoi asynchrone journalisé, erreurs lisibles.
 */
class WorkspaceMailIntegrationTest extends PostgresIntegrationTest {

    @RegisterExtension
    static GreenMailExtension greenMail = new GreenMailExtension(new ServerSetup(0, null, ServerSetup.PROTOCOL_SMTP))
            .withPerMethodLifecycle(false);

    @Autowired
    TestRestTemplate restTemplate;
    @Autowired
    MailService mailService;
    @Autowired
    EmailLogRepository emailLogRepository;

    String token;
    UUID tenantId;
    UUID workspaceId;

    @BeforeEach
    void setUp() {
        LoginResponse login = restTemplate.postForObject("/api/v1/auth/login",
                new LoginRequest("admin@nerd.local", "Admin123!", "test-tenant"), LoginResponse.class);
        token = login.accessToken();
        tenantId = login.tenantId();
        ResponseEntity<List<WorkspaceResponse>> workspaces = restTemplate.exchange("/api/v1/workspaces",
                HttpMethod.GET, new HttpEntity<>(headers()), new ParameterizedTypeReference<>() {
                });
        workspaceId = workspaces.getBody().getFirst().id();
        greenMail.setUser("client@acme.test", "client@acme.test", "app-password");
    }

    @Test
    void workspaceSmtpSettingsTestAndSend() throws Exception {
        String base = "/api/v1/workspaces/" + workspaceId + "/mail";
        int port = greenMail.getSmtp().getPort();

        // Sans configuration : rien d'enregistré, pas de secours plateforme (désactivé en test).
        WorkspaceMailSettingsResponse empty = restTemplate.exchange(base + "/settings", HttpMethod.GET,
                new HttpEntity<>(headers()), WorkspaceMailSettingsResponse.class).getBody();
        assertNotNull(empty);
        assertFalse(empty.configured());
        assertFalse(empty.platformFallbackAvailable());
        assertFalse(mailService.canSendFrom(tenantId, workspaceId));

        // Test d'envoi avec des paramètres non enregistrés : le mail arrive, l'utilisateur connecté est destinataire.
        ResponseEntity<String> testRaw = restTemplate.postForEntity(base + "/test",
                new HttpEntity<>(new MailTestRequest("localhost", port, SmtpSecurity.NONE, "client@acme.test",
                        "app-password", "client@acme.test", "ACME", null, null), headers()), String.class);
        assertEquals(HttpStatus.OK, testRaw.getStatusCode(), testRaw.getBody());
        MailTestResponse test = restTemplate.postForObject(base + "/test",
                new HttpEntity<>(new MailTestRequest("localhost", port, SmtpSecurity.NONE, "client@acme.test",
                        "app-password", "client@acme.test", "ACME", null, null), headers()), MailTestResponse.class);
        assertTrue(test.ok());
        assertEquals("admin@nerd.local", test.sentTo());
        assertTrue(greenMail.waitForIncomingEmail(5000, 2));
        MimeMessage received = greenMail.getReceivedMessages()[0];
        assertTrue(received.getSubject().contains("Test de messagerie"));
        assertEquals("ACME <client@acme.test>", received.getFrom()[0].toString());

        // Mauvais mot de passe : erreur explicite, pas de plantage.
        ResponseEntity<String> badAuth = restTemplate.postForEntity(base + "/test",
                new HttpEntity<>(new MailTestRequest("localhost", port, SmtpSecurity.NONE, "client@acme.test",
                        "wrong", "client@acme.test", null, null, null), headers()), String.class);
        assertEquals(HttpStatus.BAD_GATEWAY, badAuth.getStatusCode());
        assertTrue(badAuth.getBody().contains("MAIL_TEST_FAILED"));

        // Enregistrement : le mot de passe n'est jamais renvoyé.
        WorkspaceMailSettingsResponse saved = restTemplate.exchange(base + "/settings", HttpMethod.PUT,
                new HttpEntity<>(new WorkspaceMailSettingsRequest(true, "localhost", port, SmtpSecurity.NONE,
                        "client@acme.test", "app-password", "client@acme.test", "ACME", "compta@acme.test"), headers()),
                WorkspaceMailSettingsResponse.class).getBody();
        assertNotNull(saved);
        assertTrue(saved.configured());
        assertTrue(saved.passwordSet());
        assertEquals("compta@acme.test", saved.replyTo());
        assertTrue(mailService.canSendFrom(tenantId, workspaceId));

        // Mise à jour sans mot de passe : l'ancien est conservé et reste utilisable.
        WorkspaceMailSettingsResponse renamed = restTemplate.exchange(base + "/settings", HttpMethod.PUT,
                new HttpEntity<>(new WorkspaceMailSettingsRequest(true, "localhost", port, SmtpSecurity.NONE,
                        "client@acme.test", "", "client@acme.test", "ACME SAS", null), headers()),
                WorkspaceMailSettingsResponse.class).getBody();
        assertEquals("ACME SAS", renamed.fromName());
        assertNull(renamed.replyTo());

        // Envoi métier asynchrone via le SMTP du workspace, journalisé SENT.
        UUID logId = mailService.send(OutgoingEmail.of(EmailKind.QUOTATION, tenantId, workspaceId,
                "prospect@exemple.test", "Votre devis", "<p>Bonjour</p>", null));
        assertTrue(greenMail.waitForIncomingEmail(5000, 3));
        Instant deadline = Instant.now().plus(Duration.ofSeconds(5));
        while (Instant.now().isBefore(deadline)
                && emailLogRepository.findById(logId).get().getStatus() == EmailStatus.QUEUED) {
            Thread.sleep(100);
        }
        assertEquals(EmailStatus.SENT, emailLogRepository.findById(logId).get().getStatus());
        assertEquals("WORKSPACE", emailLogRepository.findById(logId).get().getSenderSource());

        // Journal consultable, trié du plus récent au plus ancien.
        ResponseEntity<PageResponse<EmailLogResponse>> log = restTemplate.exchange(base + "/log", HttpMethod.GET,
                new HttpEntity<>(headers()), new ParameterizedTypeReference<>() {
                });
        assertTrue(log.getBody().totalElements() >= 3);
        assertEquals("Votre devis", log.getBody().content().getFirst().subject());

        // Suppression : plus d'expéditeur disponible.
        restTemplate.exchange(base + "/settings", HttpMethod.DELETE, new HttpEntity<>(headers()), Void.class);
        assertFalse(mailService.canSendFrom(tenantId, workspaceId));
    }

    private HttpHeaders headers() {
        HttpHeaders h = new HttpHeaders();
        h.setBearerAuth(token);
        return h;
    }
}
