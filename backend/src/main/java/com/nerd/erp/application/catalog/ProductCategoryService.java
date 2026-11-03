package com.nerd.erp.application.catalog;

import com.nerd.erp.api.catalog.ProductCategoryRequest;
import com.nerd.erp.api.catalog.ProductCategoryResponse;
import com.nerd.erp.api.error.ApiException;
import com.nerd.erp.domain.catalog.ProductCategory;
import com.nerd.erp.infrastructure.persistence.catalog.ProductCategoryRepository;
import com.nerd.erp.infrastructure.persistence.catalog.ProductRepository;
import com.nerd.erp.infrastructure.security.core.SecurityContextHelper;
import com.nerd.erp.application.workspace.WorkspaceService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class ProductCategoryService {

    private final ProductCategoryRepository categoryRepository;
    private final ProductRepository productRepository;
    private final WorkspaceService workspaceService;

    public ProductCategoryService(ProductCategoryRepository categoryRepository,
                                  ProductRepository productRepository,
                                  WorkspaceService workspaceService) {
        this.categoryRepository = categoryRepository;
        this.productRepository = productRepository;
        this.workspaceService = workspaceService;
    }

    @Transactional(readOnly = true)
    public List<ProductCategoryResponse> listByWorkspace(UUID workspaceId) {
        UUID tenantId = SecurityContextHelper.requireTenantId();
        workspaceService.requireInTenant(workspaceId, tenantId);
        return categoryRepository.findByTenantIdAndWorkspaceIdOrderBySortOrderAscLabelAsc(tenantId, workspaceId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public ProductCategoryResponse get(UUID id) {
        return toResponse(requireCategory(id));
    }

    @Transactional
    public ProductCategoryResponse create(ProductCategoryRequest request) {
        SecurityContextHelper.requireWriteAccess();
        UUID tenantId = SecurityContextHelper.requireTenantId();
        workspaceService.requireInTenant(request.workspaceId(), tenantId);
        if (categoryRepository.existsByTenantIdAndWorkspaceIdAndCodeIgnoreCase(
                tenantId, request.workspaceId(), request.code().trim())) {
            throw new ApiException(HttpStatus.CONFLICT, "Une catégorie avec ce code existe déjà");
        }
        ProductCategory category = new ProductCategory();
        category.setTenantId(tenantId);
        apply(category, request);
        return toResponse(categoryRepository.save(category));
    }

    @Transactional
    public ProductCategoryResponse update(UUID id, ProductCategoryRequest request) {
        SecurityContextHelper.requireWriteAccess();
        ProductCategory category = requireCategory(id);
        if (!category.getWorkspaceId().equals(request.workspaceId())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Cannot move category to another workspace");
        }
        UUID tenantId = category.getTenantId();
        if (categoryRepository.existsByTenantIdAndWorkspaceIdAndCodeIgnoreCaseAndIdNot(
                tenantId, request.workspaceId(), request.code().trim(), id)) {
            throw new ApiException(HttpStatus.CONFLICT, "Une catégorie avec ce code existe déjà");
        }
        apply(category, request);
        return toResponse(categoryRepository.save(category));
    }

    @Transactional
    public void delete(UUID id) {
        SecurityContextHelper.requireWriteAccess();
        ProductCategory category = requireCategory(id);
        UUID tenantId = category.getTenantId();
        if (productRepository.existsByTenantIdAndCategoryId(tenantId, id)) {
            throw new ApiException(HttpStatus.CONFLICT,
                    "Impossible de supprimer : des produits utilisent cette catégorie");
        }
        categoryRepository.delete(category);
    }

    public ProductCategory requireCategoryInWorkspace(UUID categoryId, UUID workspaceId, UUID tenantId) {
        ProductCategory category = categoryRepository.findByIdAndTenantId(categoryId, tenantId)
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "Catégorie introuvable"));
        if (!category.getWorkspaceId().equals(workspaceId)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "La catégorie n'appartient pas à ce workspace");
        }
        return category;
    }

    private ProductCategory requireCategory(UUID id) {
        UUID tenantId = SecurityContextHelper.requireTenantId();
        ProductCategory category = categoryRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Catégorie introuvable"));
        workspaceService.requireInTenant(category.getWorkspaceId(), tenantId);
        return category;
    }

    private void apply(ProductCategory category, ProductCategoryRequest request) {
        category.setWorkspaceId(request.workspaceId());
        category.setCode(request.code().trim());
        category.setLabel(request.label().trim());
        category.setSortOrder(request.sortOrder() != null ? request.sortOrder() : 0);
    }

    private ProductCategoryResponse toResponse(ProductCategory c) {
        return new ProductCategoryResponse(c.getId(), c.getWorkspaceId(), c.getCode(), c.getLabel(), c.getSortOrder());
    }
}
