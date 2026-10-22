package com.nerd.erp.infrastructure.bootstrap.core;

import com.nerd.erp.application.merchant.MerchantExtractionPrompts;
import com.nerd.erp.domain.merchant.Merchant;
import com.nerd.erp.domain.workspace.Workspace;
import com.nerd.erp.infrastructure.persistence.merchant.MerchantRepository;
import com.nerd.erp.infrastructure.persistence.workspace.WorkspaceRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Order(100)
public class MerchantSeedRunner implements ApplicationRunner {

    private static final String NERD_FORGE_SLUG = "nerd-forge";
    private static final String NERD_FORGE_MERCHANT_LOGO = "/branding/nerd-forge-logo.png";

    private final WorkspaceRepository workspaceRepository;
    private final MerchantRepository merchantRepository;

    public MerchantSeedRunner(WorkspaceRepository workspaceRepository, MerchantRepository merchantRepository) {
        this.workspaceRepository = workspaceRepository;
        this.merchantRepository = merchantRepository;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        for (Workspace workspace : workspaceRepository.findAll()) {
            if (!NERD_FORGE_SLUG.equals(workspace.getSlug())) {
                continue;
            }
            ensureMerchant(workspace, "Etsy", MerchantExtractionPrompts.SLUG_ETSY,
                    "/branding/etsy-icon.png", MerchantExtractionPrompts.ETSY);
            ensureMerchant(workspace, "Nerd-Forge", "nerd-forge", NERD_FORGE_MERCHANT_LOGO, null);
        }
    }

    private void ensureMerchant(Workspace workspace, String name, String slug, String logoUrl, String extractionPrompt) {
        merchantRepository.findByTenantIdAndWorkspaceIdAndSlug(workspace.getTenantId(), workspace.getId(), slug)
                .ifPresentOrElse(
                        existing -> syncMerchantMetadata(existing, logoUrl, extractionPrompt),
                        () -> {
                            Merchant merchant = new Merchant();
                            merchant.setTenantId(workspace.getTenantId());
                            merchant.setWorkspaceId(workspace.getId());
                            merchant.setName(name);
                            merchant.setSlug(slug);
                            merchant.setLogoUrl(logoUrl);
                            merchant.setExtractionPrompt(extractionPrompt);
                            merchantRepository.save(merchant);
                        });
    }

    private void syncMerchantMetadata(Merchant merchant, String logoUrl, String templatePrompt) {
        boolean dirty = false;
        if (logoUrl != null && !logoUrl.isBlank() && !logoUrl.equals(merchant.getLogoUrl())) {
            merchant.setLogoUrl(logoUrl);
            dirty = true;
        }
        if (templatePrompt != null && !templatePrompt.isBlank()
                && !templatePrompt.equals(merchant.getExtractionPrompt())) {
            merchant.setExtractionPrompt(templatePrompt);
            dirty = true;
        }
        if (dirty) {
            merchantRepository.save(merchant);
        }
    }
}
