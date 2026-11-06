package com.nerd.erp.application.catalog;

import com.nerd.erp.domain.catalog.BillingType;
import com.nerd.erp.domain.catalog.Product;
import com.nerd.erp.domain.catalog.ProductCategory;
import com.nerd.erp.api.catalog.ProductRequest;
import com.nerd.erp.api.catalog.ProductResponse;
import com.nerd.erp.api.catalog.ProductStatsResponse;
import com.nerd.erp.infrastructure.persistence.catalog.CatalogPackageItemRepository;
import com.nerd.erp.infrastructure.persistence.catalog.ProductCategoryRepository;
import com.nerd.erp.infrastructure.persistence.catalog.ProductRepository;
import com.nerd.erp.infrastructure.persistence.maintenance.MaintenanceContractRepository;
import com.nerd.erp.infrastructure.persistence.quotation.QuotationLineRepository;
import com.nerd.erp.infrastructure.security.core.SecurityContextHelper;
import com.nerd.erp.api.error.ApiException;
import com.nerd.erp.api.shared.PageResponse;
import com.nerd.erp.application.workspace.WorkspaceService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class ProductService {

    private final ProductRepository productRepository;
    private final ProductCategoryRepository categoryRepository;
    private final CatalogPackageItemRepository catalogPackageItemRepository;
    private final ProductCategoryService categoryService;
    private final WorkspaceService workspaceService;
    private final QuotationLineRepository quotationLineRepository;
    private final MaintenanceContractRepository maintenanceContractRepository;

    public ProductService(ProductRepository productRepository,
                          ProductCategoryRepository categoryRepository,
                          CatalogPackageItemRepository catalogPackageItemRepository,
                          ProductCategoryService categoryService,
                          WorkspaceService workspaceService,
                          QuotationLineRepository quotationLineRepository,
                          MaintenanceContractRepository maintenanceContractRepository) {
        this.productRepository = productRepository;
        this.categoryRepository = categoryRepository;
        this.catalogPackageItemRepository = catalogPackageItemRepository;
        this.categoryService = categoryService;
        this.workspaceService = workspaceService;
        this.quotationLineRepository = quotationLineRepository;
        this.maintenanceContractRepository = maintenanceContractRepository;
    }

    @Transactional(readOnly = true)
    public PageResponse<ProductResponse> search(UUID workspaceId, boolean activeOnly, String q,
                                                UUID categoryId, Pageable pageable) {
        UUID tenantId = SecurityContextHelper.requireTenantId();
        workspaceService.requireInTenant(workspaceId, tenantId);
        if (categoryId != null) {
            categoryService.requireCategoryInWorkspace(categoryId, workspaceId, tenantId);
        }
        Map<UUID, String> categoryLabels = categoryLabels(tenantId, workspaceId);
        String term = q == null || q.isBlank() ? null : q.trim();
        Page<Product> page = term == null
                ? productRepository.listByWorkspace(tenantId, workspaceId, activeOnly, categoryId, pageable)
                : productRepository.searchByWorkspace(tenantId, workspaceId, activeOnly, categoryId,
                "%" + term.toLowerCase() + "%", pageable);
        return PageResponse.from(page.map(p -> toResponse(p, categoryLabels)));
    }

    @Transactional(readOnly = true)
    public ProductStatsResponse stats(UUID workspaceId) {
        UUID tenantId = SecurityContextHelper.requireTenantId();
        workspaceService.requireInTenant(workspaceId, tenantId);
        Map<UUID, Long> byCategory = new HashMap<>();
        for (Object[] row : productRepository.countByCategory(tenantId, workspaceId)) {
            byCategory.put((UUID) row[0], (Long) row[1]);
        }
        Double avgDayRate = productRepository.averageActiveDayRate(tenantId, workspaceId);
        return new ProductStatsResponse(
                productRepository.countByTenantIdAndWorkspaceIdAndActiveTrue(tenantId, workspaceId),
                avgDayRate == null ? BigDecimal.ZERO : BigDecimal.valueOf(avgDayRate).setScale(2, RoundingMode.HALF_UP),
                productRepository.sumActiveUnitPriceHt(tenantId, workspaceId),
                byCategory);
    }

    @Transactional(readOnly = true)
    public ProductResponse get(UUID id) {
        Product product = requireProduct(id);
        Map<UUID, String> categoryLabels = categoryLabels(
                product.getTenantId(), product.getWorkspaceId());
        return toResponse(product, categoryLabels);
    }

    @Transactional
    public ProductResponse create(ProductRequest request) {
        SecurityContextHelper.requireWriteAccess();
        UUID tenantId = SecurityContextHelper.requireTenantId();
        workspaceService.requireInTenant(request.workspaceId(), tenantId);
        Product product = new Product();
        product.setTenantId(tenantId);
        apply(product, request);
        Product saved = productRepository.save(product);
        return toResponse(saved, categoryLabels(saved.getTenantId(), saved.getWorkspaceId()));
    }

    @Transactional
    public ProductResponse update(UUID id, ProductRequest request) {
        SecurityContextHelper.requireWriteAccess();
        Product product = requireProduct(id);
        if (!product.getWorkspaceId().equals(request.workspaceId())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Cannot move product to another workspace");
        }
        apply(product, request);
        Product saved = productRepository.save(product);
        return toResponse(saved, categoryLabels(saved.getTenantId(), saved.getWorkspaceId()));
    }

    @Transactional
    public void delete(UUID id) {
        SecurityContextHelper.requireWriteAccess();
        Product product = requireProduct(id);
        UUID tenantId = SecurityContextHelper.requireTenantId();
        if (quotationLineRepository.existsByTenantIdAndProductId(tenantId, id)) {
            throw new ApiException(HttpStatus.CONFLICT,
                    "Impossible de supprimer : produit utilisé sur un devis");
        }
        if (maintenanceContractRepository.existsByTenantIdAndProductId(tenantId, id)) {
            throw new ApiException(HttpStatus.CONFLICT,
                    "Impossible de supprimer : produit lié à un contrat de maintenance");
        }
        if (catalogPackageItemRepository.existsByTenantIdAndProductId(tenantId, id)) {
            throw new ApiException(HttpStatus.CONFLICT,
                    "Impossible de supprimer : produit inclus dans un package catalogue");
        }
        productRepository.delete(product);
    }

    public Product requireProduct(UUID id, UUID tenantId) {
        return productRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Product not found"));
    }

    private Product requireProduct(UUID id) {
        UUID tenantId = SecurityContextHelper.requireTenantId();
        Product product = requireProduct(id, tenantId);
        workspaceService.requireInTenant(product.getWorkspaceId(), tenantId);
        return product;
    }

    private void apply(Product product, ProductRequest request) {
        product.setWorkspaceId(request.workspaceId());
        product.setReferenceCode(request.referenceCode());
        product.setName(request.name());
        product.setDescription(request.description());
        product.setUnitPriceHt(request.unitPriceHt());
        product.setVatRate(request.vatRate());
        if (request.active() != null) {
            product.setActive(request.active());
        }
        product.setBillingType(request.billingType() != null ? request.billingType() : BillingType.UNIT);
        String unitLabel = request.unitLabel() == null ? null : request.unitLabel().trim();
        product.setUnitLabel(unitLabel == null || unitLabel.isEmpty() ? null : unitLabel);
        if (request.categoryId() != null) {
            categoryService.requireCategoryInWorkspace(
                    request.categoryId(), request.workspaceId(), product.getTenantId());
            product.setCategoryId(request.categoryId());
        } else {
            product.setCategoryId(null);
        }
    }

    private Map<UUID, String> categoryLabels(UUID tenantId, UUID workspaceId) {
        return categoryRepository.findByTenantIdAndWorkspaceIdOrderBySortOrderAscLabelAsc(tenantId, workspaceId)
                .stream()
                .collect(Collectors.toMap(ProductCategory::getId, ProductCategory::getLabel, (a, b) -> a));
    }

    private ProductResponse toResponse(Product p, Map<UUID, String> categoryLabels) {
        UUID categoryId = p.getCategoryId();
        String categoryLabel = categoryId != null ? categoryLabels.get(categoryId) : null;
        return new ProductResponse(p.getId(), p.getWorkspaceId(), p.getReferenceCode(), p.getName(),
                p.getDescription(), p.getUnitPriceHt(), p.getVatRate(), p.isActive(), categoryId, categoryLabel,
                p.getBillingType(), p.effectiveUnitLabel());
    }
}
