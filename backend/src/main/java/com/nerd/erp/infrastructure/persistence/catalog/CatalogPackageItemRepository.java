package com.nerd.erp.infrastructure.persistence.catalog;

import com.nerd.erp.domain.catalog.CatalogPackageItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface CatalogPackageItemRepository extends JpaRepository<CatalogPackageItem, UUID> {

    boolean existsByTenantIdAndProductId(UUID tenantId, UUID productId);
}
