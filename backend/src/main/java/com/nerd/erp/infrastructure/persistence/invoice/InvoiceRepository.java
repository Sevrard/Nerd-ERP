package com.nerd.erp.infrastructure.persistence.invoice;

import com.nerd.erp.domain.invoice.Invoice;
import com.nerd.erp.domain.invoice.InvoiceStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

public interface InvoiceRepository extends JpaRepository<Invoice, UUID> {

    Optional<Invoice> findByIdAndTenantId(UUID id, UUID tenantId);

    boolean existsByTenantIdAndCustomerId(UUID tenantId, UUID customerId);

    Optional<Invoice> findBySourceQuotationIdAndTenantId(UUID quotationId, UUID tenantId);

    Page<Invoice> findByTenantIdAndWorkspaceIdOrderByIssueDateDesc(UUID tenantId, UUID workspaceId, Pageable pageable);

    @Query("""
            SELECT COALESCE(SUM(i.totalTtc), 0) FROM Invoice i
            WHERE i.tenantId = :tenantId
            AND (:workspaceId IS NULL OR i.workspaceId = :workspaceId)
            AND i.status IN ('ISSUED', 'PARTIALLY_PAID', 'PAID')
            AND i.issueDate BETWEEN :from AND :to
            AND i.invoiceType = 'STANDARD'
            """)
    BigDecimal sumInvoicedRevenue(@Param("tenantId") UUID tenantId,
                                  @Param("workspaceId") UUID workspaceId,
                                  @Param("from") LocalDate from,
                                  @Param("to") LocalDate to);

    @Query("""
            SELECT COALESCE(SUM(i.totalTtc), 0) FROM Invoice i
            WHERE i.tenantId = :tenantId
            AND (:workspaceId IS NULL OR i.workspaceId = :workspaceId)
            AND i.status IN ('ISSUED', 'PARTIALLY_PAID', 'PAID')
            AND i.invoiceType = 'STANDARD'
            """)
    BigDecimal sumInvoicedRevenueAllTime(@Param("tenantId") UUID tenantId,
                                         @Param("workspaceId") UUID workspaceId);

    @Query("""
            SELECT COUNT(i) FROM Invoice i
            WHERE i.tenantId = :tenantId
            AND (:workspaceId IS NULL OR i.workspaceId = :workspaceId)
            AND i.status IN ('ISSUED', 'PARTIALLY_PAID', 'PAID')
            AND i.issueDate BETWEEN :from AND :to
            AND i.invoiceType = 'STANDARD'
            """)
    long countInvoicedInvoices(@Param("tenantId") UUID tenantId,
                               @Param("workspaceId") UUID workspaceId,
                               @Param("from") LocalDate from,
                               @Param("to") LocalDate to);

    @Query("""
            SELECT COUNT(i) FROM Invoice i
            WHERE i.tenantId = :tenantId
            AND (:workspaceId IS NULL OR i.workspaceId = :workspaceId)
            AND i.status IN ('ISSUED', 'PARTIALLY_PAID', 'PAID')
            AND i.invoiceType = 'STANDARD'
            """)
    long countInvoicedInvoicesAllTime(@Param("tenantId") UUID tenantId,
                                      @Param("workspaceId") UUID workspaceId);

    @Query("""
            SELECT COUNT(i) FROM Invoice i
            WHERE i.tenantId = :tenantId
            AND (:workspaceId IS NULL OR i.workspaceId = :workspaceId)
            AND i.status IN ('ISSUED', 'PARTIALLY_PAID')
            AND i.dueDate < :today
            """)
    long countOverdue(@Param("tenantId") UUID tenantId,
                      @Param("workspaceId") UUID workspaceId,
                      @Param("today") LocalDate today);

    @Query("""
            SELECT COALESCE(SUM(i.totalTtc - i.amountPaid), 0) FROM Invoice i
            WHERE i.tenantId = :tenantId
            AND (:workspaceId IS NULL OR i.workspaceId = :workspaceId)
            AND i.status IN ('ISSUED', 'PARTIALLY_PAID')
            """)
    BigDecimal sumOutstanding(@Param("tenantId") UUID tenantId, @Param("workspaceId") UUID workspaceId);

    /** Encaissements issus d’imports scan (amountPaid sans écriture payment_allocation). */
    @Query("""
            SELECT COALESCE(SUM(i.amountPaid), 0) FROM Invoice i
            WHERE i.tenantId = :tenantId
            AND (:workspaceId IS NULL OR i.workspaceId = :workspaceId)
            AND i.issueDate BETWEEN :from AND :to
            AND i.amountPaid > 0
            AND NOT EXISTS (
                SELECT 1 FROM PaymentAllocation pa
                WHERE pa.tenantId = i.tenantId AND pa.invoiceId = i.id
            )
            """)
    BigDecimal sumDirectAmountPaidByIssueDate(@Param("tenantId") UUID tenantId,
                                              @Param("workspaceId") UUID workspaceId,
                                              @Param("from") LocalDate from,
                                              @Param("to") LocalDate to);

    @Query("""
            SELECT COALESCE(SUM(i.amountPaid), 0) FROM Invoice i
            WHERE i.tenantId = :tenantId
            AND (:workspaceId IS NULL OR i.workspaceId = :workspaceId)
            AND i.amountPaid > 0
            AND NOT EXISTS (
                SELECT 1 FROM PaymentAllocation pa
                WHERE pa.tenantId = i.tenantId AND pa.invoiceId = i.id
            )
            """)
    BigDecimal sumDirectAmountPaidAllTime(@Param("tenantId") UUID tenantId,
                                          @Param("workspaceId") UUID workspaceId);

    Optional<Invoice> findByTenantIdAndWorkspaceIdAndExternalReferenceIgnoreCase(
            UUID tenantId, UUID workspaceId, String externalReference);

    Optional<Invoice> findByTenantIdAndWorkspaceIdAndOrderNumberIgnoreCase(
            UUID tenantId, UUID workspaceId, String orderNumber);

    Optional<Invoice> findByTenantIdAndWorkspaceIdAndMerchantIgnoreCaseAndOrderNumberIgnoreCase(
            UUID tenantId, UUID workspaceId, String merchant, String orderNumber);

    Optional<Invoice> findByTenantIdAndWorkspaceIdAndEtsyReceiptId(
            UUID tenantId, UUID workspaceId, Long etsyReceiptId);

    @Query("""
            SELECT i FROM Invoice i
            WHERE i.tenantId = :tenantId AND i.workspaceId = :workspaceId
            AND (i.notes LIKE CONCAT('%Réf. ', :ref, '%') OR i.notes LIKE CONCAT('%Commande ', :ref, '%'))
            ORDER BY i.createdAt DESC
            """)
    java.util.List<Invoice> findAllByTenantIdAndWorkspaceIdAndNotesContainingRef(
            @Param("tenantId") UUID tenantId,
            @Param("workspaceId") UUID workspaceId,
            @Param("ref") String ref,
            Pageable pageable);
}
