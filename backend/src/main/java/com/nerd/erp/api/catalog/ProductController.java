package com.nerd.erp.api.catalog;

import com.nerd.erp.api.catalog.ProductRequest;
import com.nerd.erp.api.catalog.ProductResponse;
import com.nerd.erp.application.catalog.ProductService;
import com.nerd.erp.api.shared.PageResponse;
import com.nerd.erp.infrastructure.security.core.RequireWriteAccess;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/products")
public class ProductController {

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    @GetMapping
    public PageResponse<ProductResponse> search(
            @RequestParam UUID workspaceId,
            @RequestParam(defaultValue = "true") boolean activeOnly,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) UUID categoryId,
            @PageableDefault(size = 20, sort = "name", direction = Sort.Direction.ASC) Pageable pageable) {
        return productService.search(workspaceId, activeOnly, q, categoryId, pageable);
    }

    @GetMapping("/stats")
    public ProductStatsResponse stats(@RequestParam UUID workspaceId) {
        return productService.stats(workspaceId);
    }

    @GetMapping("/{id}")
    public ProductResponse get(@PathVariable UUID id) {
        return productService.get(id);
    }

    @PostMapping
    @RequireWriteAccess
    public ProductResponse create(@Valid @RequestBody ProductRequest request) {
        return productService.create(request);
    }

    @PutMapping("/{id}")
    @RequireWriteAccess
    public ProductResponse update(@PathVariable UUID id, @Valid @RequestBody ProductRequest request) {
        return productService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @RequireWriteAccess
    public void delete(@PathVariable UUID id) {
        productService.delete(id);
    }
}
