package com.nerd.erp.infrastructure.persistence.invoice;

import com.nerd.erp.domain.invoice.InvoiceLine;
import com.nerd.erp.infrastructure.persistence.invoice.projection.EtsyListingSalesRow;
import com.nerd.erp.infrastructure.persistence.invoice.projection.ProductSalesRow;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface InvoiceLineRepository extends JpaRepository<InvoiceLine, UUID> {

    boolean existsByTenantIdAndProductId(UUID tenantId, UUID productId);

    @Query("""
            SELECT il.etsyListingId AS etsyListingId,
                   il.productId AS productId,
                   p.name AS productName,
                   p.referenceCode AS productReferenceCode,
                   SUM(il.quantity) AS quantitySold,
                   SUM(il.lineTotalHt) AS revenueHt,
                   SUM(il.lineTotalTtc) AS revenueTtc,
                   COUNT(DISTINCT il.invoice.id) AS orderCount,
                   COUNT(il.id) AS lineCount
            FROM InvoiceLine il
            JOIN il.invoice i
            LEFT JOIN com.nerd.erp.domain.catalog.Product p ON p.id = il.productId AND p.tenantId = il.tenantId
            WHERE il.tenantId = :tenantId AND i.workspaceId = :workspaceId
            AND il.etsyListingId IS NOT NULL
            AND i.status IN ('ISSUED', 'PARTIALLY_PAID', 'PAID')
            AND i.issueDate BETWEEN :from AND :to
            GROUP BY il.etsyListingId, il.productId, p.name, p.referenceCode
            ORDER BY SUM(il.lineTotalTtc) DESC
            """)
    List<EtsyListingSalesRow> aggregateSalesByListingInPeriod(
            @Param("tenantId") UUID tenantId,
            @Param("workspaceId") UUID workspaceId,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to);

    @Query("""
            SELECT il.etsyListingId AS etsyListingId,
                   il.productId AS productId,
                   p.name AS productName,
                   p.referenceCode AS productReferenceCode,
                   SUM(il.quantity) AS quantitySold,
                   SUM(il.lineTotalHt) AS revenueHt,
                   SUM(il.lineTotalTtc) AS revenueTtc,
                   COUNT(DISTINCT il.invoice.id) AS orderCount,
                   COUNT(il.id) AS lineCount
            FROM InvoiceLine il
            JOIN il.invoice i
            LEFT JOIN com.nerd.erp.domain.catalog.Product p ON p.id = il.productId AND p.tenantId = il.tenantId
            WHERE il.tenantId = :tenantId AND i.workspaceId = :workspaceId
            AND il.etsyListingId IS NOT NULL
            AND i.status IN ('ISSUED', 'PARTIALLY_PAID', 'PAID')
            GROUP BY il.etsyListingId, il.productId, p.name, p.referenceCode
            ORDER BY SUM(il.lineTotalTtc) DESC
            """)
    List<EtsyListingSalesRow> aggregateSalesByListingAllTime(
            @Param("tenantId") UUID tenantId,
            @Param("workspaceId") UUID workspaceId);

    @Query("""
            SELECT il.productId AS productId,
                   COALESCE(p.name, 'Sans produit lié') AS productName,
                   COALESCE(p.referenceCode, '') AS productReferenceCode,
                   SUM(il.quantity) AS quantitySold,
                   SUM(il.lineTotalHt) AS revenueHt,
                   SUM(il.lineTotalTtc) AS revenueTtc,
                   COUNT(DISTINCT il.invoice.id) AS orderCount,
                   COUNT(il.id) AS lineCount
            FROM InvoiceLine il
            JOIN il.invoice i
            LEFT JOIN com.nerd.erp.domain.catalog.Product p ON p.id = il.productId AND p.tenantId = il.tenantId
            WHERE il.tenantId = :tenantId AND i.workspaceId = :workspaceId
            AND i.status IN ('ISSUED', 'PARTIALLY_PAID', 'PAID')
            AND i.issueDate BETWEEN :from AND :to
            GROUP BY il.productId, p.name, p.referenceCode
            ORDER BY SUM(il.lineTotalTtc) DESC
            """)
    List<ProductSalesRow> aggregateSalesByProductInPeriod(
            @Param("tenantId") UUID tenantId,
            @Param("workspaceId") UUID workspaceId,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to);

    @Query("""
            SELECT il.productId AS productId,
                   COALESCE(p.name, 'Sans produit lié') AS productName,
                   COALESCE(p.referenceCode, '') AS productReferenceCode,
                   SUM(il.quantity) AS quantitySold,
                   SUM(il.lineTotalHt) AS revenueHt,
                   SUM(il.lineTotalTtc) AS revenueTtc,
                   COUNT(DISTINCT il.invoice.id) AS orderCount,
                   COUNT(il.id) AS lineCount
            FROM InvoiceLine il
            JOIN il.invoice i
            LEFT JOIN com.nerd.erp.domain.catalog.Product p ON p.id = il.productId AND p.tenantId = il.tenantId
            WHERE il.tenantId = :tenantId AND i.workspaceId = :workspaceId
            AND i.status IN ('ISSUED', 'PARTIALLY_PAID', 'PAID')
            GROUP BY il.productId, p.name, p.referenceCode
            ORDER BY SUM(il.lineTotalTtc) DESC
            """)
    List<ProductSalesRow> aggregateSalesByProductAllTime(
            @Param("tenantId") UUID tenantId,
            @Param("workspaceId") UUID workspaceId);

    @Query("""
            SELECT COALESCE(SUM(il.quantity), 0) FROM InvoiceLine il
            JOIN il.invoice i
            WHERE il.tenantId = :tenantId AND i.workspaceId = :workspaceId
            AND i.status IN ('ISSUED', 'PARTIALLY_PAID', 'PAID')
            AND i.issueDate BETWEEN :from AND :to
            """)
    BigDecimal sumUnitsSoldInPeriod(
            @Param("tenantId") UUID tenantId,
            @Param("workspaceId") UUID workspaceId,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to);

    @Query("""
            SELECT COALESCE(SUM(il.quantity), 0) FROM InvoiceLine il
            JOIN il.invoice i
            WHERE il.tenantId = :tenantId AND i.workspaceId = :workspaceId
            AND i.status IN ('ISSUED', 'PARTIALLY_PAID', 'PAID')
            """)
    BigDecimal sumUnitsSoldAllTime(
            @Param("tenantId") UUID tenantId,
            @Param("workspaceId") UUID workspaceId);
}
