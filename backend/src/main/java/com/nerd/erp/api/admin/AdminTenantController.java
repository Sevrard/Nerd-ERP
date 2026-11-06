package com.nerd.erp.api.admin;

import com.nerd.erp.api.admin.AdminDtos.ModuleDefinition;
import com.nerd.erp.api.admin.AdminDtos.ModulesUpdateRequest;
import com.nerd.erp.api.admin.AdminDtos.TenantCreateRequest;
import com.nerd.erp.api.admin.AdminDtos.TenantDetail;
import com.nerd.erp.api.admin.AdminDtos.TenantSummary;
import com.nerd.erp.api.admin.AdminDtos.TenantUpdateRequest;
import com.nerd.erp.api.admin.AdminDtos.TenantUserCreateRequest;
import com.nerd.erp.api.shared.PageResponse;
import com.nerd.erp.application.admin.AdminTenantService;
import com.nerd.erp.infrastructure.security.core.RequirePlatformAdmin;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** Administration plateforme (éditeur) : tenants clients, modules, utilisateurs. */
@RestController
@RequestMapping("/api/v1/admin")
@RequirePlatformAdmin
public class AdminTenantController {

    private final AdminTenantService adminTenantService;

    public AdminTenantController(AdminTenantService adminTenantService) {
        this.adminTenantService = adminTenantService;
    }

    @GetMapping("/modules")
    public List<ModuleDefinition> modules() {
        return adminTenantService.listModuleDefinitions();
    }

    @GetMapping("/tenants")
    public PageResponse<TenantSummary> listTenants(
            @RequestParam(required = false) String q,
            @PageableDefault(size = 20, sort = "name", direction = Sort.Direction.ASC) Pageable pageable) {
        return PageResponse.from(adminTenantService.list(q, pageable));
    }

    @PostMapping("/tenants")
    @ResponseStatus(HttpStatus.CREATED)
    public TenantDetail createTenant(@Valid @RequestBody TenantCreateRequest request) {
        return adminTenantService.create(request);
    }

    @GetMapping("/tenants/{id}")
    public TenantDetail getTenant(@PathVariable UUID id) {
        return adminTenantService.get(id);
    }

    @PatchMapping("/tenants/{id}")
    public TenantDetail updateTenant(@PathVariable UUID id, @Valid @RequestBody TenantUpdateRequest request) {
        return adminTenantService.update(id, request);
    }

    @PutMapping("/tenants/{id}/modules")
    public TenantDetail updateModules(@PathVariable UUID id, @Valid @RequestBody ModulesUpdateRequest request) {
        return adminTenantService.updateModules(id, request);
    }

    @PostMapping("/tenants/{id}/users")
    @ResponseStatus(HttpStatus.CREATED)
    public TenantDetail addUser(@PathVariable UUID id, @Valid @RequestBody TenantUserCreateRequest request) {
        return adminTenantService.addUser(id, request);
    }
}
