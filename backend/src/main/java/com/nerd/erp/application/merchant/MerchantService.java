package com.nerd.erp.application.merchant;

import com.nerd.erp.api.merchant.MerchantResponse;
import com.nerd.erp.application.workspace.WorkspaceService;
import com.nerd.erp.domain.merchant.Merchant;
import com.nerd.erp.infrastructure.persistence.merchant.MerchantRepository;
import com.nerd.erp.infrastructure.security.core.SecurityContextHelper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class MerchantService {

    private final MerchantRepository merchantRepository;
    private final WorkspaceService workspaceService;

    public MerchantService(MerchantRepository merchantRepository, WorkspaceService workspaceService) {
        this.merchantRepository = merchantRepository;
        this.workspaceService = workspaceService;
    }

    @Transactional(readOnly = true)
    public List<MerchantResponse> listByWorkspace(UUID workspaceId) {
        UUID tenantId = SecurityContextHelper.requireTenantId();
        workspaceService.requireInTenant(workspaceId, tenantId);
        return merchantRepository.findByTenantIdAndWorkspaceIdOrderByNameAsc(tenantId, workspaceId).stream()
                .map(this::toResponse)
                .toList();
    }

    private MerchantResponse toResponse(Merchant merchant) {
        return new MerchantResponse(
                merchant.getId(),
                merchant.getWorkspaceId(),
                merchant.getName(),
                merchant.getSlug(),
                merchant.getLogoUrl());
    }
}
