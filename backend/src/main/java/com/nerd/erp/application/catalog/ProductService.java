package com.nerd.erp.application.catalog;

import com.nerd.erp.domain.catalog.Product;
import com.nerd.erp.api.catalog.ProductRequest;
import com.nerd.erp.api.catalog.ProductResponse;
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

import java.util.UUID;

@Service
public class ProductService {

    private final ProductRepository productRepository;
    private final WorkspaceService workspaceService;
    private final QuotationLineRepository quotationLineRepository;
    private final MaintenanceContractRepository maintenanceContractRepository;

    public ProductService(ProductRepository productRepository,
                          WorkspaceService workspaceService,
                          QuotationLineRepository quotationLineRepository,
                          MaintenanceContractRepository maintenanceContractRepository) {
        this.productRepository = productRepository;
        this.workspaceService = workspaceService;
        this.quotationLineRepository = quotationLineRepository;
        this.maintenanceContractRepository = maintenanceContractRepository;
    }

    @Transactional(readOnly = true)
    public PageResponse<ProductResponse> search(UUID workspaceId, boolean activeOnly, String q, Pageable pageable) {
        UUID tenantId = SecurityContextHelper.requireTenantId();
        workspaceService.requireInTenant(workspaceId, tenantId);
        String term = q == null || q.isBlank() ? null : q.trim();
        Page<Product> page = term == null
                ? productRepository.listByWorkspace(tenantId, workspaceId, activeOnly, pageable)
                : productRepository.searchByWorkspace(tenantId, workspaceId, activeOnly,
                "%" + term.toLowerCase() + "%", pageable);
        return PageResponse.from(page.map(this::toResponse));
    }

    @Transactional(readOnly = true)
    public ProductResponse get(UUID id) {
        return toResponse(requireProduct(id));
    }

    @Transactional
    public ProductResponse create(ProductRequest request) {
        SecurityContextHelper.requireWriteAccess();
        UUID tenantId = SecurityContextHelper.requireTenantId();
        workspaceService.requireInTenant(request.workspaceId(), tenantId);
        Product product = new Product();
        product.setTenantId(tenantId);
        apply(product, request);
        return toResponse(productRepository.save(product));
    }

    @Transactional
    public ProductResponse update(UUID id, ProductRequest request) {
        SecurityContextHelper.requireWriteAccess();
        Product product = requireProduct(id);
        if (!product.getWorkspaceId().equals(request.workspaceId())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Cannot move product to another workspace");
        }
        apply(product, request);
        return toResponse(productRepository.save(product));
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
    }

    private ProductResponse toResponse(Product p) {
        return new ProductResponse(p.getId(), p.getWorkspaceId(), p.getReferenceCode(), p.getName(),
                p.getDescription(), p.getUnitPriceHt(), p.getVatRate(), p.isActive());
    }
}
