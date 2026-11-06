package com.nerd.erp.api.catalog;

import com.nerd.erp.application.catalog.CatalogPackageService;
import com.nerd.erp.infrastructure.security.core.RequireWriteAccess;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
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

@RestController
@RequestMapping("/api/v1/catalog-packages")
public class CatalogPackageController {

    private final CatalogPackageService packageService;

    public CatalogPackageController(CatalogPackageService packageService) {
        this.packageService = packageService;
    }

    @GetMapping
    public List<CatalogPackageResponse> list(@RequestParam UUID workspaceId) {
        return packageService.listByWorkspace(workspaceId);
    }

    @GetMapping("/{id}")
    public CatalogPackageResponse get(@PathVariable UUID id) {
        return packageService.get(id);
    }

    @PostMapping
    @RequireWriteAccess
    public CatalogPackageResponse create(@Valid @RequestBody CatalogPackageRequest request) {
        return packageService.create(request);
    }

    @PutMapping("/{id}")
    @RequireWriteAccess
    public CatalogPackageResponse update(@PathVariable UUID id, @Valid @RequestBody CatalogPackageRequest request) {
        return packageService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @RequireWriteAccess
    public void delete(@PathVariable UUID id) {
        packageService.delete(id);
    }
}
