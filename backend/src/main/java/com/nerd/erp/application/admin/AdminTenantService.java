package com.nerd.erp.application.admin;

import com.nerd.erp.api.admin.AdminDtos.Invitation;
import com.nerd.erp.api.admin.AdminDtos.ModuleDefinition;
import com.nerd.erp.api.admin.AdminDtos.ModuleUpdate;
import com.nerd.erp.api.admin.AdminDtos.ModulesUpdateRequest;
import com.nerd.erp.api.admin.AdminDtos.TenantCreateRequest;
import com.nerd.erp.api.admin.AdminDtos.TenantDetail;
import com.nerd.erp.api.admin.AdminDtos.TenantModuleState;
import com.nerd.erp.api.admin.AdminDtos.TenantSummary;
import com.nerd.erp.api.admin.AdminDtos.TenantUpdateRequest;
import com.nerd.erp.api.admin.AdminDtos.TenantUser;
import com.nerd.erp.api.admin.AdminDtos.TenantUserCreateRequest;
import com.nerd.erp.api.error.ApiException;
import com.nerd.erp.application.account.InvitationService;
import com.nerd.erp.application.account.InvitationService.Outcome;
import com.nerd.erp.application.module.ModuleAccessService;
import com.nerd.erp.application.tenancy.TenantStatusService;
import com.nerd.erp.application.workspace.WorkspaceSlugHelper;
import com.nerd.erp.domain.module.ModuleCode;
import com.nerd.erp.domain.module.TenantModule;
import com.nerd.erp.domain.organization.Organization;
import com.nerd.erp.domain.tenancy.AppUser;
import com.nerd.erp.domain.tenancy.Tenant;
import com.nerd.erp.domain.tenancy.TenantMembership;
import com.nerd.erp.domain.tenancy.TenantRole;
import com.nerd.erp.domain.workspace.Workspace;
import com.nerd.erp.infrastructure.persistence.module.TenantModuleRepository;
import com.nerd.erp.infrastructure.persistence.organization.OrganizationRepository;
import com.nerd.erp.infrastructure.persistence.tenancy.AppUserRepository;
import com.nerd.erp.infrastructure.persistence.tenancy.TenantMembershipRepository;
import com.nerd.erp.infrastructure.persistence.tenancy.TenantRepository;
import com.nerd.erp.infrastructure.persistence.workspace.WorkspaceRepository;
import com.nerd.erp.infrastructure.security.core.AuthenticatedUser;
import com.nerd.erp.infrastructure.security.core.SecurityContextHelper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Gestion des tenants clients par l'éditeur : onboarding, modules, suspension, utilisateurs.
 * Réservé aux administrateurs plateforme (contrôle d'accès au niveau du contrôleur).
 */
@Service
public class AdminTenantService {

    private final TenantRepository tenantRepository;
    private final AppUserRepository appUserRepository;
    private final TenantMembershipRepository membershipRepository;
    private final OrganizationRepository organizationRepository;
    private final WorkspaceRepository workspaceRepository;
    private final TenantModuleRepository tenantModuleRepository;
    private final ModuleAccessService moduleAccessService;
    private final TenantStatusService tenantStatusService;
    private final InvitationService invitationService;

    public AdminTenantService(TenantRepository tenantRepository,
                              AppUserRepository appUserRepository,
                              TenantMembershipRepository membershipRepository,
                              OrganizationRepository organizationRepository,
                              WorkspaceRepository workspaceRepository,
                              TenantModuleRepository tenantModuleRepository,
                              ModuleAccessService moduleAccessService,
                              TenantStatusService tenantStatusService,
                              InvitationService invitationService) {
        this.tenantRepository = tenantRepository;
        this.appUserRepository = appUserRepository;
        this.membershipRepository = membershipRepository;
        this.organizationRepository = organizationRepository;
        this.workspaceRepository = workspaceRepository;
        this.tenantModuleRepository = tenantModuleRepository;
        this.moduleAccessService = moduleAccessService;
        this.tenantStatusService = tenantStatusService;
        this.invitationService = invitationService;
    }

    public List<ModuleDefinition> listModuleDefinitions() {
        return Arrays.stream(ModuleCode.values()).map(ModuleDefinition::of).toList();
    }

    @Transactional(readOnly = true)
    public Page<TenantSummary> list(String q, Pageable pageable) {
        String pattern = (q == null || q.isBlank()) ? null : "%" + q.trim().toLowerCase() + "%";
        Page<Tenant> page = tenantRepository.search(pattern, pageable);
        List<UUID> ids = page.getContent().stream().map(Tenant::getId).toList();
        Map<UUID, Long> userCounts = new HashMap<>();
        Map<UUID, Set<ModuleCode>> activeModules = new HashMap<>();
        if (!ids.isEmpty()) {
            for (TenantMembership m : membershipRepository.findByTenantIdIn(ids)) {
                userCounts.merge(m.getTenantId(), 1L, Long::sum);
            }
            LocalDate today = LocalDate.now();
            for (TenantModule tm : tenantModuleRepository.findByTenantIdIn(ids)) {
                if (tm.isCurrentlyActive(today)) {
                    activeModules.computeIfAbsent(tm.getTenantId(), k -> EnumSet.noneOf(ModuleCode.class))
                            .add(tm.getModuleCode());
                }
            }
        }
        return page.map(t -> new TenantSummary(
                t.getId(), t.getName(), t.getSlug(), t.isActive(), t.getCreatedAt(),
                userCounts.getOrDefault(t.getId(), 0L),
                activeModules.getOrDefault(t.getId(), Set.of()).stream().sorted().toList()));
    }

    @Transactional(readOnly = true)
    public TenantDetail get(UUID tenantId) {
        return toDetail(requireTenant(tenantId));
    }

    @Transactional
    public TenantDetail create(TenantCreateRequest request) {
        String slug = resolveSlug(request.slug(), request.name());
        if (tenantRepository.existsBySlug(slug)) {
            throw new ApiException(HttpStatus.CONFLICT, "TENANT_SLUG_TAKEN", "Ce slug de compte est déjà utilisé");
        }
        Tenant tenant = new Tenant();
        tenant.setName(request.name().trim());
        tenant.setSlug(slug);
        tenant.setActive(true);
        tenant = tenantRepository.save(tenant);

        AuthenticatedUser current = SecurityContextHelper.requireUser();
        Outcome outcome = invitationService.invite(tenant, request.ownerEmail(), request.ownerDisplayName(),
                TenantRole.OWNER, request.ownerPassword(), current.userId(), inviterName(current));
        AppUser owner = outcome.user();

        String legalName = blankToDefault(request.legalName(), tenant.getName());
        Organization org = new Organization();
        org.setTenantId(tenant.getId());
        org.setLegalName(legalName);
        org.setTradeName(tenant.getName());
        org.setCountryCode("FR");
        org.setEmail(owner.getEmail());
        org = organizationRepository.save(org);

        String workspaceName = blankToDefault(request.workspaceName(), tenant.getName());
        Workspace ws = new Workspace();
        ws.setTenantId(tenant.getId());
        ws.setOrganizationId(org.getId());
        ws.setName(workspaceName);
        ws.setSlug(WorkspaceSlugHelper.normalize(workspaceName));
        ws.setCurrencyCode("EUR");
        ws.setInvoicePrefix("FAC");
        ws.setQuotationPrefix("DEV");
        ws.setActive(true);
        workspaceRepository.save(ws);

        Set<ModuleCode> requested = request.modules() == null
                ? EnumSet.noneOf(ModuleCode.class)
                : EnumSet.copyOf(request.modules());
        for (ModuleCode code : ModuleCode.values()) {
            TenantModule tm = new TenantModule();
            tm.setTenantId(tenant.getId());
            tm.setModuleCode(code);
            boolean enabled = requested.contains(code);
            tm.setEnabled(enabled);
            tm.setActivatedAt(enabled ? Instant.now() : null);
            tenantModuleRepository.save(tm);
        }
        return toDetail(tenant).withInvitation(toInvitation(outcome));
    }

    @Transactional
    public TenantDetail update(UUID tenantId, TenantUpdateRequest request) {
        Tenant tenant = requireTenant(tenantId);
        if (request.name() != null && !request.name().isBlank()) {
            tenant.setName(request.name().trim());
        }
        if (request.active() != null && request.active() != tenant.isActive()) {
            if (!request.active() && tenantId.equals(SecurityContextHelper.requireTenantId())) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "CANNOT_SUSPEND_OWN_TENANT",
                        "Impossible de suspendre votre propre compte");
            }
            tenant.setActive(request.active());
            tenantStatusService.invalidate(tenantId);
        }
        if (request.brandColor() != null) {
            tenant.setBrandColor(request.brandColor().isBlank() ? null : request.brandColor().toLowerCase(Locale.ROOT));
        }
        if (request.logoUrl() != null) {
            tenant.setLogoUrl(request.logoUrl().isBlank() ? null : request.logoUrl().trim());
        }
        if (request.loginTagline() != null) {
            tenant.setLoginTagline(request.loginTagline().isBlank() ? null : request.loginTagline().trim());
        }
        return toDetail(tenantRepository.save(tenant));
    }

    @Transactional
    public TenantDetail updateModules(UUID tenantId, ModulesUpdateRequest request) {
        Tenant tenant = requireTenant(tenantId);
        Map<ModuleCode, TenantModule> existing = tenantModuleRepository.findByTenantId(tenantId).stream()
                .collect(Collectors.toMap(TenantModule::getModuleCode, tm -> tm, (a, b) -> a,
                        () -> new EnumMap<>(ModuleCode.class)));
        for (ModuleUpdate update : request.modules()) {
            TenantModule tm = existing.get(update.code());
            if (tm == null) {
                tm = new TenantModule();
                tm.setTenantId(tenantId);
                tm.setModuleCode(update.code());
            }
            if (update.enabled() && !tm.isEnabled()) {
                tm.setActivatedAt(Instant.now());
            }
            tm.setEnabled(update.enabled());
            tm.setExpiresAt(update.expiresAt());
            tenantModuleRepository.save(tm);
        }
        moduleAccessService.invalidate(tenantId);
        return toDetail(tenant);
    }

    @Transactional
    public TenantDetail addUser(UUID tenantId, TenantUserCreateRequest request) {
        Tenant tenant = requireTenant(tenantId);
        AuthenticatedUser current = SecurityContextHelper.requireUser();
        Outcome outcome = invitationService.invite(tenant, request.email(), request.displayName(), request.role(),
                request.password(), current.userId(), inviterName(current));
        return toDetail(tenant).withInvitation(toInvitation(outcome));
    }

    /** Nouveau lien d'activation pour un utilisateur dont l'invitation est en attente. */
    @Transactional
    public TenantDetail resendInvitation(UUID tenantId, UUID userId) {
        Tenant tenant = requireTenant(tenantId);
        AuthenticatedUser current = SecurityContextHelper.requireUser();
        AppUser user = appUserRepository.findById(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Utilisateur introuvable"));
        Outcome outcome = invitationService.resend(tenant, user, current.userId(), inviterName(current));
        return toDetail(tenant).withInvitation(toInvitation(outcome));
    }

    private Tenant requireTenant(UUID tenantId) {
        return tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Compte introuvable"));
    }

    private String inviterName(AuthenticatedUser current) {
        return appUserRepository.findById(current.userId()).map(AppUser::getDisplayName).orElse(current.email());
    }

    private static Invitation toInvitation(Outcome outcome) {
        if (outcome.user().isPasswordSet() && !outcome.emailSent()) {
            return null;
        }
        return new Invitation(outcome.user().getEmail(), outcome.emailSent(), outcome.activationUrl());
    }

    private TenantDetail toDetail(Tenant tenant) {
        List<TenantMembership> memberships = membershipRepository.findByTenantId(tenant.getId());
        Map<UUID, AppUser> users = appUserRepository.findAllById(
                        memberships.stream().map(TenantMembership::getUserId).toList()).stream()
                .collect(Collectors.toMap(AppUser::getId, u -> u));
        List<TenantUser> tenantUsers = new ArrayList<>();
        for (TenantMembership m : memberships) {
            AppUser u = users.get(m.getUserId());
            if (u != null) {
                tenantUsers.add(new TenantUser(u.getId(), u.getEmail(), u.getDisplayName(), m.getRole(),
                        u.isEnabled(), u.isPlatformAdmin(), !u.isPasswordSet(), u.getLastLoginAt()));
            }
        }
        tenantUsers.sort(Comparator.comparing(TenantUser::email));

        LocalDate today = LocalDate.now();
        Map<ModuleCode, TenantModule> byCode = tenantModuleRepository.findByTenantId(tenant.getId()).stream()
                .collect(Collectors.toMap(TenantModule::getModuleCode, tm -> tm, (a, b) -> a,
                        () -> new EnumMap<>(ModuleCode.class)));
        List<TenantModuleState> modules = new ArrayList<>();
        for (ModuleCode code : ModuleCode.values()) {
            TenantModule tm = byCode.get(code);
            modules.add(tm == null
                    ? new TenantModuleState(code, code.label(), false, false, null, null)
                    : new TenantModuleState(code, code.label(), tm.isEnabled(), tm.isCurrentlyActive(today),
                    tm.getActivatedAt(), tm.getExpiresAt()));
        }

        return new TenantDetail(
                tenant.getId(), tenant.getName(), tenant.getSlug(), tenant.isActive(),
                tenant.getCreatedAt(), tenant.getUpdatedAt(),
                workspaceRepository.countByTenantIdAndActiveTrue(tenant.getId()),
                tenantUsers, modules, tenant.getBrandColor(), tenant.getLogoUrl(), tenant.getLoginTagline(), null);
    }

    private static String resolveSlug(String requested, String name) {
        String base = (requested == null || requested.isBlank()) ? name : requested;
        return WorkspaceSlugHelper.normalize(base);
    }

    private static String blankToDefault(String value, String fallback) {
        return (value == null || value.isBlank()) ? fallback : value.trim();
    }
}
