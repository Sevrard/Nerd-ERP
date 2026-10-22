package com.nerd.erp.application.tenancy;

import com.nerd.erp.api.error.ApiException;
import com.nerd.erp.infrastructure.persistence.tenancy.TenantMembershipRepository;
import com.nerd.erp.domain.workspace.Workspace;
import com.nerd.erp.infrastructure.persistence.workspace.WorkspaceRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class TenantAccessService {

    private final WorkspaceRepository workspaceRepository;
    private final TenantMembershipRepository membershipRepository;

    public TenantAccessService(WorkspaceRepository workspaceRepository,
                               TenantMembershipRepository membershipRepository) {
        this.workspaceRepository = workspaceRepository;
        this.membershipRepository = membershipRepository;
    }

    public void assertSameTenant(UUID resourceTenantId, UUID currentTenantId) {
        if (!resourceTenantId.equals(currentTenantId)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Cross-tenant access denied");
        }
    }

    public Workspace requireWorkspace(UUID workspaceId, UUID tenantId) {
        Workspace workspace = workspaceRepository.findByIdAndTenantId(workspaceId, tenantId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Workspace not found"));
        if (!workspace.isActive()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Workspace is inactive");
        }
        return workspace;
    }

    public void assertUserBelongsToTenant(UUID userId, UUID tenantId) {
        membershipRepository.findByTenantIdAndUserId(tenantId, userId)
                .orElseThrow(() -> new ApiException(HttpStatus.FORBIDDEN, "Not a member of this tenant"));
    }
}
