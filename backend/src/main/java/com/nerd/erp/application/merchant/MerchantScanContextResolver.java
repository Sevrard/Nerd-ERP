package com.nerd.erp.application.merchant;

import com.nerd.erp.api.error.ApiException;
import com.nerd.erp.application.workspace.WorkspaceService;
import com.nerd.erp.domain.merchant.Merchant;
import com.nerd.erp.infrastructure.persistence.merchant.MerchantRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class MerchantScanContextResolver {

    private final MerchantRepository merchantRepository;
    private final WorkspaceService workspaceService;

    public MerchantScanContextResolver(MerchantRepository merchantRepository,
                                       WorkspaceService workspaceService) {
        this.merchantRepository = merchantRepository;
        this.workspaceService = workspaceService;
    }

    public MerchantScanContext resolve(UUID tenantId, UUID workspaceId, UUID merchantId) {
        if (merchantId == null) {
            return MerchantScanContext.none();
        }
        Merchant merchant = merchantRepository.findByIdAndTenantId(merchantId, tenantId)
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "Marchand inconnu"));
        workspaceService.requireInTenant(workspaceId, tenantId);
        if (!merchant.getWorkspaceId().equals(workspaceId)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Marchand hors du workspace sélectionné");
        }
        String prompt = merchant.getExtractionPrompt();
        if (prompt == null || prompt.isBlank()) {
            prompt = MerchantExtractionPrompts.templateForSlug(merchant.getSlug());
        }
        return new MerchantScanContext(
                merchant.getId(),
                merchant.getName(),
                merchant.getSlug(),
                prompt);
    }
}
