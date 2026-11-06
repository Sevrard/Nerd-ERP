package com.nerd.erp.application.catalog;

import com.nerd.erp.api.catalog.CatalogPackageItemRequest;
import com.nerd.erp.api.catalog.CatalogPackageItemResponse;
import com.nerd.erp.api.catalog.CatalogPackageRequest;
import com.nerd.erp.api.catalog.CatalogPackageResponse;
import com.nerd.erp.api.error.ApiException;
import com.nerd.erp.domain.catalog.CatalogPackage;
import com.nerd.erp.domain.catalog.CatalogPackageItem;
import com.nerd.erp.domain.catalog.PackageDiscountType;
import com.nerd.erp.domain.catalog.Product;
import com.nerd.erp.infrastructure.persistence.catalog.CatalogPackageRepository;
import com.nerd.erp.infrastructure.security.core.SecurityContextHelper;
import com.nerd.erp.application.workspace.WorkspaceService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class CatalogPackageService {

    private final CatalogPackageRepository packageRepository;
    private final ProductService productService;
    private final WorkspaceService workspaceService;

    public CatalogPackageService(CatalogPackageRepository packageRepository,
                                 ProductService productService,
                                 WorkspaceService workspaceService) {
        this.packageRepository = packageRepository;
        this.productService = productService;
        this.workspaceService = workspaceService;
    }

    @Transactional(readOnly = true)
    public List<CatalogPackageResponse> listByWorkspace(UUID workspaceId) {
        UUID tenantId = SecurityContextHelper.requireTenantId();
        workspaceService.requireInTenant(workspaceId, tenantId);
        Map<UUID, Product> products = loadProductsMap(tenantId, workspaceId);
        return packageRepository.findByTenantIdAndWorkspaceIdOrderByTitleAsc(tenantId, workspaceId).stream()
                .map(pkg -> toResponse(pkg, products))
                .toList();
    }

    @Transactional(readOnly = true)
    public CatalogPackageResponse get(UUID id) {
        UUID tenantId = SecurityContextHelper.requireTenantId();
        CatalogPackage pkg = requirePackage(id, tenantId);
        workspaceService.requireInTenant(pkg.getWorkspaceId(), tenantId);
        Map<UUID, Product> products = loadProductsMap(tenantId, pkg.getWorkspaceId());
        return toResponse(pkg, products);
    }

    @Transactional
    public CatalogPackageResponse create(CatalogPackageRequest request) {
        SecurityContextHelper.requireWriteAccess();
        UUID tenantId = SecurityContextHelper.requireTenantId();
        workspaceService.requireInTenant(request.workspaceId(), tenantId);
        assertUniqueReference(tenantId, request.workspaceId(), request.referenceCode(), null);
        CatalogPackage pkg = new CatalogPackage();
        pkg.setTenantId(tenantId);
        applyHeader(pkg, request);
        replaceItems(pkg, request, tenantId);
        CatalogPackage saved = packageRepository.save(pkg);
        Map<UUID, Product> products = loadProductsMap(tenantId, saved.getWorkspaceId());
        return toResponse(saved, products);
    }

    @Transactional
    public CatalogPackageResponse update(UUID id, CatalogPackageRequest request) {
        SecurityContextHelper.requireWriteAccess();
        UUID tenantId = SecurityContextHelper.requireTenantId();
        CatalogPackage pkg = requirePackage(id, tenantId);
        if (!pkg.getWorkspaceId().equals(request.workspaceId())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Cannot move package to another workspace");
        }
        assertUniqueReference(tenantId, request.workspaceId(), request.referenceCode(), id);
        applyHeader(pkg, request);
        replaceItems(pkg, request, tenantId);
        CatalogPackage saved = packageRepository.save(pkg);
        Map<UUID, Product> products = loadProductsMap(tenantId, saved.getWorkspaceId());
        return toResponse(saved, products);
    }

    @Transactional
    public void delete(UUID id) {
        SecurityContextHelper.requireWriteAccess();
        UUID tenantId = SecurityContextHelper.requireTenantId();
        CatalogPackage pkg = requirePackage(id, tenantId);
        workspaceService.requireInTenant(pkg.getWorkspaceId(), tenantId);
        packageRepository.delete(pkg);
    }

    private CatalogPackage requirePackage(UUID id, UUID tenantId) {
        return packageRepository.findByIdAndTenantIdWithItems(id, tenantId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Package introuvable"));
    }

    private void assertUniqueReference(UUID tenantId, UUID workspaceId, String referenceCode, UUID excludeId) {
        boolean exists = excludeId == null
                ? packageRepository.existsByTenantIdAndWorkspaceIdAndReferenceCodeIgnoreCase(
                tenantId, workspaceId, referenceCode.trim())
                : packageRepository.existsByTenantIdAndWorkspaceIdAndReferenceCodeIgnoreCaseAndIdNot(
                tenantId, workspaceId, referenceCode.trim(), excludeId);
        if (exists) {
            throw new ApiException(HttpStatus.CONFLICT, "Un package avec cette référence existe déjà");
        }
    }

    private void applyHeader(CatalogPackage pkg, CatalogPackageRequest request) {
        pkg.setWorkspaceId(request.workspaceId());
        pkg.setReferenceCode(request.referenceCode().trim());
        pkg.setTitle(request.title().trim());
        pkg.setDescription(request.description());
        pkg.setDiscountType(request.discountType());
        pkg.setDiscountValue(request.discountValue());
        pkg.setBadgeTheme(request.badgeTheme() != null && !request.badgeTheme().isBlank()
                ? request.badgeTheme().trim() : "purple");
        if (request.active() != null) {
            pkg.setActive(request.active());
        }
    }

    private void replaceItems(CatalogPackage pkg, CatalogPackageRequest request, UUID tenantId) {
        pkg.getItems().clear();
        int order = 0;
        for (CatalogPackageItemRequest itemReq : request.items()) {
            Product product = productService.requireProduct(itemReq.productId(), tenantId);
            if (!product.getWorkspaceId().equals(request.workspaceId())) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "Produit hors workspace : " + product.getReferenceCode());
            }
            CatalogPackageItem line = new CatalogPackageItem();
            line.setTenantId(tenantId);
            line.setCatalogPackage(pkg);
            line.setProductId(itemReq.productId());
            line.setQuantity(itemReq.quantity());
            line.setLineOrder(order++);
            pkg.getItems().add(line);
        }
    }

    private Map<UUID, Product> loadProductsMap(UUID tenantId, UUID workspaceId) {
        // Produits chargés à la demande via requireProduct dans toResponse si manquant — pour list, batch serait mieux.
        // Ici on remplit au fil de l'eau dans toResponse via productService.
        return new HashMap<>();
    }

    private CatalogPackageResponse toResponse(CatalogPackage pkg, Map<UUID, Product> cache) {
        UUID tenantId = pkg.getTenantId();
        List<CatalogPackageItemResponse> itemResponses = new ArrayList<>();
        BigDecimal gross = BigDecimal.ZERO;

        for (CatalogPackageItem item : pkg.getItems()) {
            Product product = cache.computeIfAbsent(item.getProductId(),
                    id -> productService.requireProduct(id, tenantId));
            BigDecimal lineTotal = product.getUnitPriceHt()
                    .multiply(item.getQuantity())
                    .setScale(4, RoundingMode.HALF_UP);
            gross = gross.add(lineTotal);
            itemResponses.add(new CatalogPackageItemResponse(
                    product.getId(),
                    product.getReferenceCode(),
                    product.getName(),
                    product.getUnitPriceHt(),
                    product.effectiveUnitLabel(),
                    item.getQuantity(),
                    lineTotal));
        }

        BigDecimal discountAmount = computeDiscount(gross, pkg.getDiscountType(), pkg.getDiscountValue());
        BigDecimal net = gross.subtract(discountAmount).max(BigDecimal.ZERO).setScale(4, RoundingMode.HALF_UP);
        discountAmount = discountAmount.setScale(4, RoundingMode.HALF_UP);
        gross = gross.setScale(4, RoundingMode.HALF_UP);

        return new CatalogPackageResponse(
                pkg.getId(),
                pkg.getWorkspaceId(),
                pkg.getReferenceCode(),
                pkg.getTitle(),
                pkg.getDescription(),
                pkg.getDiscountType(),
                pkg.getDiscountValue(),
                pkg.getBadgeTheme(),
                pkg.isActive(),
                itemResponses,
                gross,
                discountAmount,
                net);
    }

    static BigDecimal computeDiscount(BigDecimal gross, PackageDiscountType type, BigDecimal discountValue) {
        if (discountValue == null || discountValue.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        if (type == PackageDiscountType.PERCENT) {
            return gross.multiply(discountValue)
                    .divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP);
        }
        return discountValue.min(gross);
    }
}
