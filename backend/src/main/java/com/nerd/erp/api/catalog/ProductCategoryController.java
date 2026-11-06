package com.nerd.erp.api.catalog;

import com.nerd.erp.application.catalog.ProductCategoryService;
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
@RequestMapping("/api/v1/product-categories")
public class ProductCategoryController {

    private final ProductCategoryService categoryService;

    public ProductCategoryController(ProductCategoryService categoryService) {
        this.categoryService = categoryService;
    }

    @GetMapping
    public List<ProductCategoryResponse> list(@RequestParam UUID workspaceId) {
        return categoryService.listByWorkspace(workspaceId);
    }

    @GetMapping("/{id}")
    public ProductCategoryResponse get(@PathVariable UUID id) {
        return categoryService.get(id);
    }

    @PostMapping
    @RequireWriteAccess
    public ProductCategoryResponse create(@Valid @RequestBody ProductCategoryRequest request) {
        return categoryService.create(request);
    }

    @PutMapping("/{id}")
    @RequireWriteAccess
    public ProductCategoryResponse update(@PathVariable UUID id, @Valid @RequestBody ProductCategoryRequest request) {
        return categoryService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @RequireWriteAccess
    public void delete(@PathVariable UUID id) {
        categoryService.delete(id);
    }
}
