package com.nerd.erp.application.account;

import com.nerd.erp.api.account.AccountDtos.ActivateRequest;
import com.nerd.erp.api.account.AccountDtos.ChangePasswordRequest;
import com.nerd.erp.api.account.AccountDtos.ProfileResponse;
import com.nerd.erp.api.account.AccountDtos.ProfileUpdateRequest;
import com.nerd.erp.api.account.AccountDtos.ResetPasswordRequest;
import com.nerd.erp.api.account.AccountDtos.LoginBrandingResponse;
import com.nerd.erp.api.account.AccountDtos.TermsStatusResponse;
import com.nerd.erp.api.account.AccountDtos.TokenInfoResponse;
import com.nerd.erp.api.auth.LoginResponse;
import com.nerd.erp.api.error.ApiException;
import com.nerd.erp.application.auth.AuthService;
import com.nerd.erp.domain.account.AccountToken;
import com.nerd.erp.domain.account.AccountTokenKind;
import com.nerd.erp.domain.tenancy.AppUser;
import com.nerd.erp.domain.tenancy.Tenant;
import com.nerd.erp.domain.tenancy.TenantMembership;
import com.nerd.erp.infrastructure.persistence.tenancy.AppUserRepository;
import com.nerd.erp.infrastructure.persistence.tenancy.TenantMembershipRepository;
import com.nerd.erp.infrastructure.persistence.tenancy.TenantRepository;
import com.nerd.erp.infrastructure.security.core.AuthenticatedUser;
import com.nerd.erp.infrastructure.security.core.SecurityContextHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** Parcours de compte : mot de passe oublié, activation d'une invitation, profil et mot de passe. */
@Service
public class AccountService {

    private static final Logger log = LoggerFactory.getLogger(AccountService.class);
    /** Anti-abus minimal : une demande de réinitialisation par adresse et par minute. */
    private static final Duration FORGOT_THROTTLE = Duration.ofMinutes(1);

    private final AppUserRepository appUserRepository;
    private final TenantRepository tenantRepository;
    private final TenantMembershipRepository membershipRepository;
    private final AccountTokenService tokenService;
    private final AccountMailer mailer;
    private final AuthService authService;
    private final PasswordEncoder passwordEncoder;
    private final Map<String, Instant> lastForgotRequests = new ConcurrentHashMap<>();

    public AccountService(AppUserRepository appUserRepository,
                          TenantRepository tenantRepository,
                          TenantMembershipRepository membershipRepository,
                          AccountTokenService tokenService,
                          AccountMailer mailer,
                          AuthService authService,
                          PasswordEncoder passwordEncoder) {
        this.appUserRepository = appUserRepository;
        this.tenantRepository = tenantRepository;
        this.membershipRepository = membershipRepository;
        this.tokenService = tokenService;
        this.mailer = mailer;
        this.authService = authService;
        this.passwordEncoder = passwordEncoder;
    }

    // ---------------------------------------------------------------- parcours publics

    /**
     * Toujours silencieux (pas d'énumération des comptes) : si l'adresse correspond à un compte actif,
     * un lien de réinitialisation valable une heure est envoyé.
     */
    @Transactional
    public void forgotPassword(String email) {
        String normalized = email.trim().toLowerCase();
        Instant now = Instant.now();
        Instant previous = lastForgotRequests.put(normalized, now);
        if (previous != null && previous.plus(FORGOT_THROTTLE).isAfter(now)) {
            return;
        }
        lastForgotRequests.entrySet().removeIf(e -> e.getValue().plus(FORGOT_THROTTLE).isBefore(now));

        Optional<AppUser> candidate = appUserRepository.findByEmailIgnoreCase(normalized)
                .filter(AppUser::isEnabled)
                .filter(AppUser::isPasswordSet);
        if (candidate.isEmpty()) {
            return;
        }
        AppUser user = candidate.get();
        Tenant tenant = firstTenantOf(user).orElse(null);
        if (tenant == null) {
            return;
        }
        String token = tokenService.issue(user.getId(), null, AccountTokenKind.PASSWORD_RESET, null);
        if (!mailer.sendPasswordReset(user, tenant, mailer.resetUrl(token))) {
            log.warn("Réinitialisation demandée pour {} mais aucune boîte plateforme n'est configurée", normalized);
        }
    }

    @Transactional(readOnly = true)
    public TokenInfoResponse tokenInfo(String rawToken) {
        AccountToken token = tokenService.requireUsable(rawToken, null);
        AppUser user = appUserRepository.findById(token.getUserId())
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, AccountTokenService.TOKEN_INVALID,
                        "Ce lien est invalide."));
        Tenant tenant = token.getTenantId() == null ? null : tenantRepository.findById(token.getTenantId()).orElse(null);
        return new TokenInfoResponse(token.getKind(), user.getEmail(), user.getDisplayName(),
                tenant == null ? null : tenant.getName(), tenant == null ? null : tenant.getSlug());
    }

    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        AccountToken token = tokenService.consume(request.token(), AccountTokenKind.PASSWORD_RESET);
        AppUser user = appUserRepository.findById(token.getUserId())
                .filter(AppUser::isEnabled)
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, AccountTokenService.TOKEN_INVALID,
                        "Ce lien est invalide."));
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setPasswordSet(true);
        appUserRepository.save(user);
    }

    /** Active un compte invité (mot de passe choisi) et ouvre directement une session sur le tenant d'invitation. */
    @Transactional
    public LoginResponse activate(ActivateRequest request) {
        AccountToken token = tokenService.consume(request.token(), AccountTokenKind.INVITATION);
        AppUser user = appUserRepository.findById(token.getUserId())
                .filter(AppUser::isEnabled)
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, AccountTokenService.TOKEN_INVALID,
                        "Ce lien est invalide."));
        Tenant tenant = Optional.ofNullable(token.getTenantId())
                .flatMap(tenantRepository::findById)
                .filter(Tenant::isActive)
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, AccountTokenService.TOKEN_INVALID,
                        "Le compte lié à cette invitation n'est plus disponible."));
        TenantMembership membership = membershipRepository.findByTenantIdAndUserId(tenant.getId(), user.getId())
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, AccountTokenService.TOKEN_INVALID,
                        "Votre accès à ce compte a été retiré."));

        if (request.displayName() != null && !request.displayName().isBlank()) {
            user.setDisplayName(request.displayName().trim());
        }
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setPasswordSet(true);
        appUserRepository.save(user);
        return authService.openSession(user, tenant, membership);
    }

    // ---------------------------------------------------------------- utilisateur connecté

    @Transactional(readOnly = true)
    public ProfileResponse profile() {
        return toProfile(requireCurrentUser());
    }

    @Transactional
    public ProfileResponse updateProfile(ProfileUpdateRequest request) {
        AppUser user = requireCurrentUser();
        user.setDisplayName(request.displayName().trim());
        return toProfile(appUserRepository.save(user));
    }

    @Transactional
    public void changePassword(ChangePasswordRequest request) {
        AppUser user = requireCurrentUser();
        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "CURRENT_PASSWORD_INVALID",
                    "Le mot de passe actuel est incorrect");
        }
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        user.setPasswordSet(true);
        appUserRepository.save(user);
    }

    /** Branding public d'un tenant actif ; réponse neutre (sans nom) si slug absent, inconnu ou inactif. */
    @Transactional(readOnly = true)
    public LoginBrandingResponse loginBranding(String slug) {
        if (slug == null || slug.isBlank()) {
            return new LoginBrandingResponse(null, null, null, null, null);
        }
        return tenantRepository.findBySlug(slug.trim().toLowerCase(java.util.Locale.ROOT))
                .filter(Tenant::isActive)
                .map(t -> new LoginBrandingResponse(t.getSlug(), t.getName(), t.getBrandColor(), t.getLogoUrl(),
                        t.getLoginTagline()))
                .orElseGet(() -> new LoginBrandingResponse(null, null, null, null, null));
    }

    /** Enregistre l'acceptation de la version courante des CGU/CGV (idempotent). */
    @Transactional
    public TermsStatusResponse acceptTerms() {
        AppUser user = requireCurrentUser();
        if (!TermsPolicy.accepted(user.getTermsVersion())) {
            user.setTermsVersion(TermsPolicy.CURRENT_VERSION);
            user.setTermsAcceptedAt(Instant.now());
            appUserRepository.save(user);
        }
        return termsStatus(user);
    }

    @Transactional(readOnly = true)
    public TermsStatusResponse termsStatus() {
        return termsStatus(requireCurrentUser());
    }

    private static TermsStatusResponse termsStatus(AppUser user) {
        return new TermsStatusResponse(TermsPolicy.CURRENT_VERSION, user.getTermsVersion(),
                user.getTermsAcceptedAt(), TermsPolicy.accepted(user.getTermsVersion()));
    }

    private AppUser requireCurrentUser() {
        AuthenticatedUser current = SecurityContextHelper.requireUser();
        return appUserRepository.findById(current.userId())
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Compte introuvable"));
    }

    private Optional<Tenant> firstTenantOf(AppUser user) {
        return membershipRepository.findByUserId(user.getId()).stream()
                .sorted(Comparator.comparing(TenantMembership::getCreatedAt))
                .map(m -> tenantRepository.findById(m.getTenantId()).orElse(null))
                .filter(t -> t != null && t.isActive())
                .findFirst();
    }

    private static ProfileResponse toProfile(AppUser user) {
        return new ProfileResponse(user.getId(), user.getEmail(), user.getDisplayName(), user.getLastLoginAt());
    }
}
