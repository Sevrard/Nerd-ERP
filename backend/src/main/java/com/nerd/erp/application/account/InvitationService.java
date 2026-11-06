package com.nerd.erp.application.account;

import com.nerd.erp.api.error.ApiException;
import com.nerd.erp.domain.account.AccountTokenKind;
import com.nerd.erp.domain.tenancy.AppUser;
import com.nerd.erp.domain.tenancy.Tenant;
import com.nerd.erp.domain.tenancy.TenantMembership;
import com.nerd.erp.domain.tenancy.TenantRole;
import com.nerd.erp.infrastructure.persistence.tenancy.AppUserRepository;
import com.nerd.erp.infrastructure.persistence.tenancy.TenantMembershipRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Ajout d'un utilisateur à un tenant, partagé entre l'administration plateforme et les administrateurs
 * du tenant :
 * <ul>
 *   <li>utilisateur inconnu → compte créé sans mot de passe + jeton d'activation envoyé par e-mail ;</li>
 *   <li>utilisateur existant → simple ajout d'accès, e-mail d'information ;</li>
 *   <li>mot de passe fourni (admin plateforme) → compte directement utilisable, sans e-mail.</li>
 * </ul>
 */
@Service
public class InvitationService {

    /** Résultat : l'utilisateur, si l'e-mail a pu partir et, sinon, le lien d'activation à transmettre. */
    public record Outcome(AppUser user, TenantMembership membership, boolean emailSent, String activationUrl) {
    }

    private final AppUserRepository appUserRepository;
    private final TenantMembershipRepository membershipRepository;
    private final AccountTokenService tokenService;
    private final AccountMailer mailer;
    private final PasswordEncoder passwordEncoder;

    public InvitationService(AppUserRepository appUserRepository,
                             TenantMembershipRepository membershipRepository,
                             AccountTokenService tokenService,
                             AccountMailer mailer,
                             PasswordEncoder passwordEncoder) {
        this.appUserRepository = appUserRepository;
        this.membershipRepository = membershipRepository;
        this.tokenService = tokenService;
        this.mailer = mailer;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public Outcome invite(Tenant tenant, String email, String displayName, TenantRole role,
                          String initialPassword, UUID inviterId, String inviterName) {
        String normalizedEmail = email.trim().toLowerCase();
        AppUser existing = appUserRepository.findByEmailIgnoreCase(normalizedEmail).orElse(null);
        if (existing != null && membershipRepository.findByTenantIdAndUserId(tenant.getId(), existing.getId()).isPresent()) {
            throw new ApiException(HttpStatus.CONFLICT, "USER_ALREADY_MEMBER",
                    "Cet utilisateur a déjà accès à ce compte");
        }

        boolean created = existing == null;
        AppUser user = existing;
        if (created) {
            user = new AppUser();
            user.setEmail(normalizedEmail);
            user.setDisplayName(blankToDefault(displayName, normalizedEmail));
            user.setEnabled(true);
            boolean withPassword = initialPassword != null && !initialPassword.isBlank();
            // Sans mot de passe initial : hachage aléatoire inutilisable jusqu'à l'activation.
            user.setPasswordHash(passwordEncoder.encode(withPassword ? initialPassword : UUID.randomUUID().toString()));
            user.setPasswordSet(withPassword);
            user = appUserRepository.save(user);
        }

        TenantMembership membership = new TenantMembership();
        membership.setTenantId(tenant.getId());
        membership.setUserId(user.getId());
        membership.setRole(role);
        membership = membershipRepository.save(membership);

        if (!user.isPasswordSet()) {
            return sendActivation(tenant, user, membership, inviterId, inviterName);
        }
        boolean sent = !created && mailer.sendAccessGranted(user, tenant, inviterName);
        return new Outcome(user, membership, sent, null);
    }

    /** Nouveau lien d'activation pour un utilisateur dont l'invitation est en attente. */
    @Transactional
    public Outcome resend(Tenant tenant, AppUser user, UUID inviterId, String inviterName) {
        if (user.isPasswordSet()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "USER_ALREADY_ACTIVE",
                    "Ce compte est déjà activé ; l'utilisateur peut utiliser « Mot de passe oublié »");
        }
        TenantMembership membership = membershipRepository.findByTenantIdAndUserId(tenant.getId(), user.getId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Utilisateur introuvable dans ce compte"));
        return sendActivation(tenant, user, membership, inviterId, inviterName);
    }

    private Outcome sendActivation(Tenant tenant, AppUser user, TenantMembership membership,
                                   UUID inviterId, String inviterName) {
        String token = tokenService.issue(user.getId(), tenant.getId(), AccountTokenKind.INVITATION, inviterId);
        String url = mailer.activationUrl(token);
        boolean sent = mailer.sendInvitation(user, tenant, inviterName, url);
        return new Outcome(user, membership, sent, sent ? null : url);
    }

    private static String blankToDefault(String value, String fallback) {
        return (value == null || value.isBlank()) ? fallback : value.trim();
    }
}
