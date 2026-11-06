package com.nerd.erp.application.mail;

import com.nerd.erp.api.error.ApiException;
import com.nerd.erp.api.mail.MailDtos.EmailLogResponse;
import com.nerd.erp.api.mail.MailDtos.MailTestRequest;
import com.nerd.erp.api.mail.MailDtos.MailTestResponse;
import com.nerd.erp.api.mail.MailDtos.WorkspaceMailSettingsRequest;
import com.nerd.erp.api.mail.MailDtos.WorkspaceMailSettingsResponse;
import com.nerd.erp.application.tenancy.TenantAccessService;
import com.nerd.erp.domain.mail.EmailKind;
import com.nerd.erp.domain.mail.EmailLog;
import com.nerd.erp.domain.mail.WorkspaceMailSettings;
import com.nerd.erp.domain.workspace.Workspace;
import com.nerd.erp.infrastructure.mail.SecretCipher;
import com.nerd.erp.infrastructure.mail.SmtpSenderFactory;
import com.nerd.erp.infrastructure.mail.SmtpSenderFactory.ResolvedSender;
import com.nerd.erp.infrastructure.persistence.mail.EmailLogRepository;
import com.nerd.erp.infrastructure.persistence.mail.WorkspaceMailSettingsRepository;
import com.nerd.erp.infrastructure.security.core.AuthenticatedUser;
import com.nerd.erp.infrastructure.security.core.SecurityContextHelper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import java.util.UUID;

/** Configuration de la boîte d'envoi d'un workspace et consultation du journal des e-mails. */
@Service
public class WorkspaceMailSettingsService {

    private final WorkspaceMailSettingsRepository settingsRepository;
    private final EmailLogRepository emailLogRepository;
    private final TenantAccessService tenantAccessService;
    private final SecretCipher cipher;
    private final SmtpSenderFactory senderFactory;
    private final MailService mailService;

    public WorkspaceMailSettingsService(WorkspaceMailSettingsRepository settingsRepository,
                                        EmailLogRepository emailLogRepository,
                                        TenantAccessService tenantAccessService,
                                        SecretCipher cipher,
                                        SmtpSenderFactory senderFactory,
                                        MailService mailService) {
        this.settingsRepository = settingsRepository;
        this.emailLogRepository = emailLogRepository;
        this.tenantAccessService = tenantAccessService;
        this.cipher = cipher;
        this.senderFactory = senderFactory;
        this.mailService = mailService;
    }

    @Transactional(readOnly = true)
    public WorkspaceMailSettingsResponse get(UUID workspaceId) {
        UUID tenantId = SecurityContextHelper.requireTenantId();
        tenantAccessService.requireWorkspace(workspaceId, tenantId);
        return toResponse(workspaceId, settingsRepository.findByWorkspaceIdAndTenantId(workspaceId, tenantId).orElse(null));
    }

    @Transactional
    public WorkspaceMailSettingsResponse save(UUID workspaceId, WorkspaceMailSettingsRequest request) {
        UUID tenantId = SecurityContextHelper.requireTenantId();
        tenantAccessService.requireWorkspace(workspaceId, tenantId);
        requireCipher();
        WorkspaceMailSettings settings = settingsRepository.findByWorkspaceIdAndTenantId(workspaceId, tenantId)
                .orElseGet(() -> {
                    WorkspaceMailSettings created = new WorkspaceMailSettings();
                    created.setTenantId(tenantId);
                    created.setWorkspaceId(workspaceId);
                    return created;
                });
        boolean passwordProvided = request.password() != null && !request.password().isBlank();
        if (!passwordProvided && settings.getPasswordEncrypted() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "MAIL_PASSWORD_REQUIRED",
                    "Le mot de passe (ou mot de passe d'application) est requis");
        }
        settings.setEnabled(request.enabled());
        settings.setHost(request.host().trim());
        settings.setPort(request.port());
        settings.setSecurity(request.security());
        settings.setUsername(request.username().trim());
        if (passwordProvided) {
            settings.setPasswordEncrypted(cipher.encrypt(request.password()));
        }
        settings.setFromEmail(request.fromEmail().trim().toLowerCase());
        settings.setFromName(blankToNull(request.fromName()));
        settings.setReplyTo(blankToNull(request.replyTo()));
        settings = settingsRepository.save(settings);
        senderFactory.evict(workspaceId);
        return toResponse(workspaceId, settings);
    }

    @Transactional
    public void delete(UUID workspaceId) {
        UUID tenantId = SecurityContextHelper.requireTenantId();
        tenantAccessService.requireWorkspace(workspaceId, tenantId);
        settingsRepository.findByWorkspaceIdAndTenantId(workspaceId, tenantId).ifPresent(settingsRepository::delete);
        senderFactory.evict(workspaceId);
    }

    /** Envoie un e-mail de test avec les paramètres du formulaire et mémorise le résultat si des réglages existent. */
    @Transactional
    public MailTestResponse test(UUID workspaceId, MailTestRequest request) {
        AuthenticatedUser user = SecurityContextHelper.requireUser();
        Workspace workspace = tenantAccessService.requireWorkspace(workspaceId, user.tenantId());
        requireCipher();
        Optional<WorkspaceMailSettings> existing = settingsRepository.findByWorkspaceIdAndTenantId(workspaceId, user.tenantId());

        String password = request.password();
        if (password == null || password.isBlank()) {
            password = existing.map(s -> cipher.decrypt(s.getPasswordEncrypted()))
                    .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "MAIL_PASSWORD_REQUIRED",
                            "Saisissez le mot de passe pour tester la connexion"));
        }
        String to = request.to() == null || request.to().isBlank() ? user.email() : request.to().trim();
        ResolvedSender sender = new ResolvedSender(
                senderFactory.transientSender(request.host().trim(), request.port(), request.security(),
                        request.username().trim(), password),
                request.fromEmail().trim(), blankToNull(request.fromName()), blankToNull(request.replyTo()),
                ResolvedSender.WORKSPACE);

        String when = DateTimeFormatter.ISO_INSTANT.format(Instant.now());
        String html = MailTemplates.layout("Test de messagerie réussi",
                MailTemplates.paragraph("Cet e-mail confirme que le workspace « " + workspace.getName()
                        + " » peut envoyer des messages depuis " + request.fromEmail().trim() + ".")
                        + MailTemplates.muted("Envoyé le " + when + " via " + request.host().trim() + ":" + request.port()),
                workspace.getName() + " — Nerd ERP");
        OutgoingEmail email = OutgoingEmail.of(EmailKind.TEST, user.tenantId(), workspaceId, to,
                "[Nerd ERP] Test de messagerie — " + workspace.getName(), html, null);

        try {
            mailService.sendTestWith(sender, email);
            existing.ifPresent(s -> recordTest(s, true, null));
            return new MailTestResponse(true, to, "E-mail de test envoyé à " + to);
        } catch (ApiException e) {
            existing.ifPresent(s -> recordTest(s, false, e.getMessage()));
            throw e;
        }
    }

    @Transactional(readOnly = true)
    public Page<EmailLogResponse> log(UUID workspaceId, Pageable pageable) {
        UUID tenantId = SecurityContextHelper.requireTenantId();
        tenantAccessService.requireWorkspace(workspaceId, tenantId);
        return emailLogRepository.findByTenantIdAndWorkspaceId(tenantId, workspaceId, pageable).map(this::toLog);
    }

    private void recordTest(WorkspaceMailSettings settings, boolean ok, String error) {
        settings.setLastTestAt(Instant.now());
        settings.setLastTestOk(ok);
        settings.setLastError(ok ? null : MailErrors.truncate(error));
        settingsRepository.save(settings);
    }

    private void requireCipher() {
        if (!cipher.isConfigured()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "MAIL_SECRETS_KEY_MISSING",
                    "La clé de chiffrement des mots de passe SMTP (nerd.mail.secrets-key) n'est pas configurée sur le serveur");
        }
    }

    private WorkspaceMailSettingsResponse toResponse(UUID workspaceId, WorkspaceMailSettings s) {
        boolean platform = senderFactory.platform() != null;
        if (s == null) {
            return new WorkspaceMailSettingsResponse(workspaceId, false, false, null, 587, null, null, false,
                    null, null, null, null, null, null, platform);
        }
        return new WorkspaceMailSettingsResponse(workspaceId, true, s.isEnabled(), s.getHost(), s.getPort(),
                s.getSecurity(), s.getUsername(), s.getPasswordEncrypted() != null, s.getFromEmail(), s.getFromName(),
                s.getReplyTo(), s.getLastTestAt(), s.getLastTestOk(), s.getLastError(), platform);
    }

    private EmailLogResponse toLog(EmailLog e) {
        return new EmailLogResponse(e.getId(), e.getKind(), e.getStatus(), e.getFromEmail(), e.getToEmail(),
                e.getSubject(), e.getRelatedId(), e.getSenderSource(), e.getAttempts(), e.getCreatedAt(),
                e.getSentAt(), e.getLastError());
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
