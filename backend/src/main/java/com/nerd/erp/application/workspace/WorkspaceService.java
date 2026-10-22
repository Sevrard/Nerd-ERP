package com.nerd.erp.application.workspace;

import com.nerd.erp.infrastructure.security.core.SecurityContextHelper;
import com.nerd.erp.api.error.ApiException;
import com.nerd.erp.api.workspace.WorkspaceCreateRequest;
import com.nerd.erp.application.etsysync.EtsySyncSessionStore;
import com.nerd.erp.application.tenancy.TenantAccessService;
import com.nerd.erp.domain.organization.Organization;
import com.nerd.erp.domain.workspace.Workspace;
import com.nerd.erp.api.workspace.WorkspaceResponse;
import com.nerd.erp.infrastructure.persistence.organization.OrganizationRepository;
import com.nerd.erp.infrastructure.persistence.workspace.WorkspaceCascadeDeleter;
import com.nerd.erp.infrastructure.persistence.workspace.WorkspaceRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class WorkspaceService {

    private final WorkspaceRepository workspaceRepository;
    private final TenantAccessService tenantAccessService;
    private final OrganizationRepository organizationRepository;
    private final WorkspaceCascadeDeleter workspaceCascadeDeleter;
    private final EtsySyncSessionStore etsySyncSessionStore;

    public WorkspaceService(WorkspaceRepository workspaceRepository,
                            TenantAccessService tenantAccessService,
                            OrganizationRepository organizationRepository,
                            WorkspaceCascadeDeleter workspaceCascadeDeleter,
                            EtsySyncSessionStore etsySyncSessionStore) {
        this.workspaceRepository = workspaceRepository;
        this.tenantAccessService = tenantAccessService;
        this.organizationRepository = organizationRepository;
        this.workspaceCascadeDeleter = workspaceCascadeDeleter;
        this.etsySyncSessionStore = etsySyncSessionStore;
    }

    @Transactional(readOnly = true)
    public List<WorkspaceResponse> listActiveForCurrentTenant() {
        UUID tenantId = SecurityContextHelper.requireTenantId();
        return workspaceRepository.findByTenantIdAndActiveTrueOrderByNameAsc(tenantId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public WorkspaceResponse get(UUID workspaceId) {
        UUID tenantId = SecurityContextHelper.requireTenantId();
        Workspace workspace = tenantAccessService.requireWorkspace(workspaceId, tenantId);
        return toResponse(workspace);
    }

    public Workspace requireInTenant(UUID workspaceId, UUID tenantId) {
        return tenantAccessService.requireWorkspace(workspaceId, tenantId);
    }

    @Transactional
    public WorkspaceResponse create(WorkspaceCreateRequest request) {
        SecurityContextHelper.requireAdminAccess();
        UUID tenantId = SecurityContextHelper.requireTenantId();

        UUID organizationId = resolveOrganizationId(tenantId, request.organizationId());
        String slug = resolveUniqueSlug(tenantId, request.slug(), request.name());

        Workspace ws = new Workspace();
        ws.setTenantId(tenantId);
        ws.setOrganizationId(organizationId);
        ws.setName(request.name().trim());
        ws.setSlug(slug);
        ws.setCurrencyCode(blankToDefault(request.currencyCode(), "EUR"));
        ws.setInvoicePrefix(blankToDefault(request.invoicePrefix(), "FAC"));
        ws.setQuotationPrefix(blankToDefault(request.quotationPrefix(), "DEV"));
        ws.setActive(true);
        ws = workspaceRepository.save(ws);
        return toResponse(ws);
    }

    @Transactional
    public void delete(UUID workspaceId) {
        SecurityContextHelper.requireAdminAccess();
        UUID tenantId = SecurityContextHelper.requireTenantId();
        Workspace workspace = tenantAccessService.requireWorkspace(workspaceId, tenantId);

        if (workspaceRepository.countByTenantIdAndActiveTrue(tenantId) <= 1) {
            throw new ApiException(HttpStatus.CONFLICT, "Impossible de supprimer le dernier workspace actif");
        }

        workspaceCascadeDeleter.deleteWorkspaceData(tenantId, workspaceId);
        workspaceRepository.delete(workspace);
        etsySyncSessionStore.removeByWorkspaceId(workspaceId);
    }

    private UUID resolveOrganizationId(UUID tenantId, UUID organizationId) {
        if (organizationId != null) {
            Organization org = organizationRepository.findById(organizationId)
                    .filter(o -> o.getTenantId().equals(tenantId))
                    .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "Organisation introuvable"));
            return org.getId();
        }
        return organizationRepository.findByTenantIdOrderByLegalNameAsc(tenantId).stream()
                .findFirst()
                .map(Organization::getId)
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "Aucune organisation pour ce tenant"));
    }

    private String resolveUniqueSlug(UUID tenantId, String requestedSlug, String name) {
        boolean explicit = requestedSlug != null && !requestedSlug.isBlank();
        String base = explicit ? WorkspaceSlugHelper.normalize(requestedSlug) : WorkspaceSlugHelper.slugFromName(name);
        if (explicit && workspaceRepository.existsByTenantIdAndSlug(tenantId, base)) {
            throw new ApiException(HttpStatus.CONFLICT, "Ce slug est déjà utilisé");
        }
        if (!workspaceRepository.existsByTenantIdAndSlug(tenantId, base)) {
            return base;
        }
        for (int i = 2; i < 1000; i++) {
            String candidate = base + "-" + i;
            if (candidate.length() > 100) {
                candidate = base.substring(0, Math.max(1, 100 - String.valueOf(i).length() - 1)) + "-" + i;
            }
            if (!workspaceRepository.existsByTenantIdAndSlug(tenantId, candidate)) {
                return candidate;
            }
        }
        throw new ApiException(HttpStatus.CONFLICT, "Impossible de générer un slug unique");
    }

    private static String blankToDefault(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value.trim();
    }

    private WorkspaceResponse toResponse(Workspace w) {
        return new WorkspaceResponse(w.getId(), w.getName(), w.getSlug(), w.getOrganizationId(),
                w.getCurrencyCode(), w.getBrandColor(), w.getLogoUrl(), w.isActive());
    }
}
