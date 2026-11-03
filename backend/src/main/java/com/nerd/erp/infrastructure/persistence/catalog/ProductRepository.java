package com.nerd.erp.infrastructure.persistence.catalog;

import com.nerd.erp.domain.catalog.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface ProductRepository extends JpaRepository<Product, UUID> {

    Optional<Product> findByIdAndTenantId(UUID id, UUID tenantId);

    Optional<Product> findByTenantIdAndWorkspaceIdAndNameIgnoreCase(
            UUID tenantId, UUID workspaceId, String name);

    boolean existsByTenantIdAndWorkspaceIdAndReferenceCodeIgnoreCase(
            UUID tenantId, UUID workspaceId, String referenceCode);

    boolean existsByTenantIdAndCategoryId(UUID tenantId, UUID categoryId);

    Optional<Product> findByTenantIdAndWorkspaceIdAndEtsyListingId(
            UUID tenantId, UUID workspaceId, Long etsyListingId);

    @Query("""
            SELECT p FROM Product p
            WHERE p.tenantId = :tenantId AND p.workspaceId = :workspaceId
            AND (:activeOnly = false OR p.active = true)
            AND (:categoryId IS NULL OR p.categoryId = :categoryId)
            """)
    Page<Product> listByWorkspace(@Param("tenantId") UUID tenantId,
                                  @Param("workspaceId") UUID workspaceId,
                                  @Param("activeOnly") boolean activeOnly,
                                  @Param("categoryId") UUID categoryId,
                                  Pageable pageable);

    @Query("""
            SELECT p FROM Product p
            WHERE p.tenantId = :tenantId AND p.workspaceId = :workspaceId
            AND (:activeOnly = false OR p.active = true)
            AND (:categoryId IS NULL OR p.categoryId = :categoryId)
            AND (LOWER(p.name) LIKE :qPattern OR LOWER(p.referenceCode) LIKE :qPattern)
            """)
    Page<Product> searchByWorkspace(@Param("tenantId") UUID tenantId,
                                    @Param("workspaceId") UUID workspaceId,
                                    @Param("activeOnly") boolean activeOnly,
                                    @Param("categoryId") UUID categoryId,
                                    @Param("qPattern") String qPattern,
                                    Pageable pageable);
}
