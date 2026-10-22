package com.nerd.erp.application.catalog;

import com.nerd.erp.application.document.DocumentLineBuilder;
import com.nerd.erp.domain.catalog.Product;
import com.nerd.erp.infrastructure.persistence.catalog.ProductRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class ImportCatalogProductService {

    private static final int MAX_NAME_LENGTH = 500;
    private static final int MAX_REFERENCE_LENGTH = 64;

    private final ProductRepository productRepository;

    public ImportCatalogProductService(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    /**
     * Crée ou retrouve les produits catalogue pour les lignes scan PDF et renvoie des inputs avec {@code productId}.
     */
    @Transactional
    public List<DocumentLineBuilder.LineInput> attachCatalogProducts(UUID tenantId, UUID workspaceId,
                                                                     List<DocumentLineBuilder.LineInput> inputs) {
        return inputs.stream()
                .map(input -> attachProduct(tenantId, workspaceId, input))
                .toList();
    }

    private DocumentLineBuilder.LineInput attachProduct(UUID tenantId, UUID workspaceId,
                                                        DocumentLineBuilder.LineInput input) {
        if (input.productId() != null) {
            return input;
        }
        if (input.description() == null || input.description().isBlank()) {
            return input;
        }
        if (isNonCatalogLine(input.description())) {
            return input;
        }
        Product product = findOrCreate(tenantId, workspaceId, input.description().trim(),
                input.unitPriceHt(), input.vatRate());
        return new DocumentLineBuilder.LineInput(
                product.getId(),
                input.description(),
                input.quantity(),
                input.unitPriceHt(),
                input.vatRate());
    }

    private Product findOrCreate(UUID tenantId,
                                 UUID workspaceId,
                                 String description,
                                 BigDecimal unitPriceHt,
                                 BigDecimal vatRate) {
        return productRepository.findByTenantIdAndWorkspaceIdAndNameIgnoreCase(tenantId, workspaceId, description)
                .orElseGet(() -> createProduct(tenantId, workspaceId, description, unitPriceHt, vatRate));
    }

    private Product createProduct(UUID tenantId,
                                  UUID workspaceId,
                                  String description,
                                  BigDecimal unitPriceHt,
                                  BigDecimal vatRate) {
        Product product = new Product();
        product.setTenantId(tenantId);
        product.setWorkspaceId(workspaceId);
        product.setName(truncate(description, MAX_NAME_LENGTH));
        product.setDescription(description);
        product.setReferenceCode(uniqueReferenceCode(tenantId, workspaceId, description));
        product.setUnitPriceHt(unitPriceHt != null && unitPriceHt.signum() >= 0 ? unitPriceHt : BigDecimal.ZERO);
        product.setVatRate(vatRate != null ? vatRate : new BigDecimal("20.00"));
        product.setActive(true);
        return productRepository.save(product);
    }

    private String uniqueReferenceCode(UUID tenantId, UUID workspaceId, String description) {
        String slug = toReferenceSlug(description);
        String candidate = truncate("SCAN-" + slug, MAX_REFERENCE_LENGTH);
        if (!productRepository.existsByTenantIdAndWorkspaceIdAndReferenceCodeIgnoreCase(
                tenantId, workspaceId, candidate)) {
            return candidate;
        }
        for (int i = 2; i < 1000; i++) {
            String suffix = "-" + i;
            String base = truncate("SCAN-" + slug, MAX_REFERENCE_LENGTH - suffix.length());
            candidate = base + suffix;
            if (!productRepository.existsByTenantIdAndWorkspaceIdAndReferenceCodeIgnoreCase(
                    tenantId, workspaceId, candidate)) {
                return candidate;
            }
        }
        return truncate("SCAN-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12), MAX_REFERENCE_LENGTH);
    }

    private static String toReferenceSlug(String description) {
        String slug = description.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-+|-+$", "");
        if (slug.isBlank()) {
            return "article";
        }
        return slug;
    }

    private static boolean isNonCatalogLine(String description) {
        String d = description.trim().toLowerCase(Locale.ROOT);
        return d.equals("frais de port")
                || d.startsWith("réduction boutique")
                || d.startsWith("reduction boutique");
    }

    private static String truncate(String value, int max) {
        if (value.length() <= max) {
            return value;
        }
        return value.substring(0, max);
    }
}
