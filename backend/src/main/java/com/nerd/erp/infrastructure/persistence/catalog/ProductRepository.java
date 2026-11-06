package com.nerd.erp.infrastructure.persistence.catalog;

import com.nerd.erp.domain.catalog.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProductRepository extends JpaRepository<Product, UUID> {

    boolean existsByTenantIdAndWorkspaceId(UUID tenantId, UUID workspaceId);

    Optional<Product> findByIdAndTenantId(UUID id, UUID tenantId);

    Optional<Product> findByTenantIdAndWorkspaceIdAndNameIgnoreCase(
            UUID tenantId, UUID workspaceId, String name);

    boolean existsByTenantIdAndWorkspaceIdAndReferenceCodeIgnoreCase(
            UUID tenantId, UUID workspaceId, String referenceCode);

    boolean existsByTenantIdAndCategoryId(UUID tenantId, UUID categoryId);

    long countByTenantIdAndWorkspaceIdAndActiveTrue(UUID tenantId, UUID workspaceId);

    /** Nombre de produits (actifs ou non) par catégorie : [categoryId, count]. */
    @Query("""
            SELECT p.categoryId, COUNT(p) FROM Product p
            WHERE p.tenantId = :tenantId AND p.workspaceId = :workspaceId AND p.categoryId IS NOT NULL
            GROUP BY p.categoryId
            """)
    List<Object[]> countByCategory(@Param("tenantId") UUID tenantId, @Param("workspaceId") UUID workspaceId);

    /** Somme des prix HT des produits actifs (valeur catalogue). */
    @Query("""
            SELECT COALESCE(SUM(p.unitPriceHt), 0) FROM Product p
            WHERE p.tenantId = :tenantId AND p.workspaceId = :workspaceId AND p.active = true
            """)
    BigDecimal sumActiveUnitPriceHt(@Param("tenantId") UUID tenantId, @Param("workspaceId") UUID workspaceId);

    /** TJM moyen des produits actifs facturés à la journée. */
    @Query("""
            SELECT AVG(p.unitPriceHt) FROM Product p
            WHERE p.tenantId = :tenantId AND p.workspaceId = :workspaceId AND p.active = true
            AND p.billingType = com.nerd.erp.domain.catalog.BillingType.DAYS
            """)
    Double averageActiveDayRate(@Param("tenantId") UUID tenantId, @Param("workspaceId") UUID workspaceId);

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
