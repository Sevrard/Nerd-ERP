package com.nerd.erp.infrastructure.persistence.catalog;

import com.nerd.erp.domain.catalog.ProductCategory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProductCategoryRepository extends JpaRepository<ProductCategory, UUID> {

    Optional<ProductCategory> findByIdAndTenantId(UUID id, UUID tenantId);

    List<ProductCategory> findByTenantIdAndWorkspaceIdOrderBySortOrderAscLabelAsc(
            UUID tenantId, UUID workspaceId);

    boolean existsByTenantIdAndWorkspaceIdAndCodeIgnoreCase(UUID tenantId, UUID workspaceId, String code);

    boolean existsByTenantIdAndWorkspaceIdAndCodeIgnoreCaseAndIdNot(
            UUID tenantId, UUID workspaceId, String code, UUID id);
}
