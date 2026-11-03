package com.nerd.erp.infrastructure.persistence.catalog;

import com.nerd.erp.domain.catalog.CatalogPackage;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CatalogPackageRepository extends JpaRepository<CatalogPackage, UUID> {

    @EntityGraph(attributePaths = "items")
    List<CatalogPackage> findByTenantIdAndWorkspaceIdOrderByTitleAsc(UUID tenantId, UUID workspaceId);

    @Query("""
            SELECT p FROM CatalogPackage p
            LEFT JOIN FETCH p.items
            WHERE p.id = :id AND p.tenantId = :tenantId
            """)
    Optional<CatalogPackage> findByIdAndTenantIdWithItems(@Param("id") UUID id, @Param("tenantId") UUID tenantId);

    boolean existsByTenantIdAndWorkspaceIdAndReferenceCodeIgnoreCase(UUID tenantId, UUID workspaceId, String referenceCode);

    boolean existsByTenantIdAndWorkspaceIdAndReferenceCodeIgnoreCaseAndIdNot(
            UUID tenantId, UUID workspaceId, String referenceCode, UUID id);
}
