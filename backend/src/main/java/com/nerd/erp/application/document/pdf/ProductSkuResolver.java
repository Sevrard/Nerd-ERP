package com.nerd.erp.application.document.pdf;

import com.nerd.erp.domain.catalog.Product;
import com.nerd.erp.infrastructure.persistence.catalog.ProductRepository;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Résout les références catalogue (SKU) des lignes de document, bornées au tenant. */
@Component
public class ProductSkuResolver {

    private final ProductRepository productRepository;

    public ProductSkuResolver(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    public Map<UUID, String> resolve(UUID tenantId, Collection<UUID> productIds) {
        var ids = productIds.stream().filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<UUID, String> out = new HashMap<>();
        for (Product p : productRepository.findAllById(ids)) {
            if (tenantId.equals(p.getTenantId()) && p.getReferenceCode() != null) {
                out.put(p.getId(), p.getReferenceCode());
            }
        }
        return out;
    }
}
