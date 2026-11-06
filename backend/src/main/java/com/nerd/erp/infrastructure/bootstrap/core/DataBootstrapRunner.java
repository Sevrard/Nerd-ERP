package com.nerd.erp.infrastructure.bootstrap.core;

import com.nerd.erp.domain.organization.Organization;
import com.nerd.erp.infrastructure.persistence.organization.OrganizationRepository;
import com.nerd.erp.domain.tenancy.TenantRole;
import com.nerd.erp.infrastructure.config.core.BootstrapProperties;
import com.nerd.erp.domain.module.ModuleCode;
import com.nerd.erp.domain.module.TenantModule;
import com.nerd.erp.domain.tenancy.AppUser;
import com.nerd.erp.domain.tenancy.Tenant;
import com.nerd.erp.domain.tenancy.TenantMembership;
import com.nerd.erp.infrastructure.persistence.module.TenantModuleRepository;
import com.nerd.erp.infrastructure.persistence.tenancy.AppUserRepository;
import com.nerd.erp.infrastructure.persistence.tenancy.TenantMembershipRepository;
import com.nerd.erp.infrastructure.persistence.tenancy.TenantRepository;
import com.nerd.erp.domain.workspace.Workspace;
import com.nerd.erp.infrastructure.persistence.workspace.WorkspaceRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Component
public class DataBootstrapRunner implements ApplicationRunner {

    private final BootstrapProperties bootstrapProperties;
    private final TenantRepository tenantRepository;
    private final AppUserRepository appUserRepository;
    private final TenantMembershipRepository membershipRepository;
    private final OrganizationRepository organizationRepository;
    private final WorkspaceRepository workspaceRepository;
    private final TenantModuleRepository tenantModuleRepository;
    private final PasswordEncoder passwordEncoder;

    public DataBootstrapRunner(BootstrapProperties bootstrapProperties,
                               TenantRepository tenantRepository,
                               AppUserRepository appUserRepository,
                               TenantMembershipRepository membershipRepository,
                               OrganizationRepository organizationRepository,
                               WorkspaceRepository workspaceRepository,
                               TenantModuleRepository tenantModuleRepository,
                               PasswordEncoder passwordEncoder) {
        this.bootstrapProperties = bootstrapProperties;
        this.tenantRepository = tenantRepository;
        this.appUserRepository = appUserRepository;
        this.membershipRepository = membershipRepository;
        this.organizationRepository = organizationRepository;
        this.workspaceRepository = workspaceRepository;
        this.tenantModuleRepository = tenantModuleRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!bootstrapProperties.enabled()) {
            return;
        }
        if (tenantRepository.existsBySlug(bootstrapProperties.tenantSlug())) {
            ensurePlatformAdmin();
            return;
        }
        Tenant tenant = new Tenant();
        tenant.setName(bootstrapProperties.tenantName());
        tenant.setSlug(bootstrapProperties.tenantSlug());
        tenant = tenantRepository.save(tenant);

        AppUser admin = new AppUser();
        admin.setEmail(bootstrapProperties.adminEmail());
        admin.setDisplayName("Administrator");
        admin.setPasswordHash(passwordEncoder.encode(bootstrapProperties.adminPassword()));
        admin.setPlatformAdmin(true);
        admin = appUserRepository.save(admin);

        TenantMembership membership = new TenantMembership();
        membership.setTenantId(tenant.getId());
        membership.setUserId(admin.getId());
        membership.setRole(TenantRole.OWNER);
        membershipRepository.save(membership);

        Organization org = new Organization();
        org.setTenantId(tenant.getId());
        org.setLegalName(bootstrapProperties.tenantName());
        org.setTradeName(bootstrapProperties.tenantName());
        org.setCountryCode("FR");
        org = organizationRepository.save(org);

        Workspace nerdGroup = workspace("Nerd Group", "nerd-group", tenant.getId(), org.getId());
        Workspace nerdForge = workspace("Nerd-Forge", "nerd-forge", tenant.getId(), org.getId());
        workspaceRepository.save(nerdGroup);
        workspaceRepository.save(nerdForge);

        for (ModuleCode code : ModuleCode.values()) {
            TenantModule module = new TenantModule();
            module.setTenantId(tenant.getId());
            module.setModuleCode(code);
            module.setEnabled(true);
            module.setActivatedAt(Instant.now());
            tenantModuleRepository.save(module);
        }
    }

    /** Base déjà initialisée : l'e-mail d'administration configuré est promu administrateur plateforme. */
    private void ensurePlatformAdmin() {
        appUserRepository.findByEmailIgnoreCase(bootstrapProperties.adminEmail())
                .filter(user -> !user.isPlatformAdmin())
                .ifPresent(user -> {
                    user.setPlatformAdmin(true);
                    appUserRepository.save(user);
                });
    }

    private Workspace workspace(String name, String slug, UUID tenantId, UUID orgId) {
        Workspace ws = new Workspace();
        ws.setTenantId(tenantId);
        ws.setOrganizationId(orgId);
        ws.setName(name);
        ws.setSlug(slug);
        ws.setCurrencyCode("EUR");
        ws.setInvoicePrefix("FAC");
        ws.setQuotationPrefix("DEV");
        ws.setActive(true);
        return ws;
    }
}
