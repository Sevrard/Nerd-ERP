package com.nerd.erp.api.merchant;

import com.nerd.erp.domain.module.ModuleCode;
import com.nerd.erp.infrastructure.security.core.RequireModule;
import com.nerd.erp.application.merchant.MerchantService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/merchants")
@RequireModule(ModuleCode.INVOICE_SCAN)
public class MerchantController {

    private final MerchantService merchantService;

    public MerchantController(MerchantService merchantService) {
        this.merchantService = merchantService;
    }

    @GetMapping
    public List<MerchantResponse> list(@RequestParam UUID workspaceId) {
        return merchantService.listByWorkspace(workspaceId);
    }
}
