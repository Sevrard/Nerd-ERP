package com.nerd.erp.infrastructure.persistence.invoice;

import com.nerd.erp.domain.invoice.Invoice;
import com.nerd.erp.domain.invoice.InvoiceStatus;
import com.nerd.erp.domain.invoice.InvoiceType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InvoiceRepository extends JpaRepository<Invoice, UUID> {

    boolean existsByTenantIdAndWorkspaceId(UUID tenantId, UUID workspaceId);

    Optional<Invoice> findByIdAndTenantId(UUID id, UUID tenantId);

    boolean existsByTenantIdAndCustomerId(UUID tenantId, UUID customerId);

    /** Avoirs émis sur une facture, plus ancien en premier. */
    List<Invoice> findByTenantIdAndCreditedInvoiceIdOrderByIssueDateAscCreatedAtAsc(UUID tenantId, UUID creditedInvoiceId);

    /** Export comptable : documents émis (hors brouillons) sur une période, par date puis numéro. */
    @Query("""
            SELECT i FROM Invoice i
            WHERE i.tenantId = :tenantId AND i.workspaceId = :workspaceId
            AND i.status <> 'DRAFT'
            AND i.issueDate BETWEEN :from AND :to
            ORDER BY i.issueDate ASC, i.number ASC
            """)
    List<Invoice> findForExport(@Param("tenantId") UUID tenantId,
                                @Param("workspaceId") UUID workspaceId,
                                @Param("from") LocalDate from,
                                @Param("to") LocalDate to);

    /**
     * Factures échues à relancer (toutes locataires confondus, filtrées ensuite sur les workspaces opt-in) :
     * émises ou partiellement payées, standard, échéance ≤ dueBefore.
     */
    @Query("""
            SELECT i FROM Invoice i
            WHERE i.status IN ('ISSUED', 'PARTIALLY_PAID')
            AND i.invoiceType <> 'CREDIT_NOTE'
            AND i.dueDate <= :dueBefore
            AND i.reminderCount < :maxReminders
            """)
    List<Invoice> findOverdueForReminders(@Param("dueBefore") LocalDate dueBefore,
                                          @Param("maxReminders") int maxReminders);

    /** Facture d'un type donné issue d'un devis (au plus une facture de solde et une d'acompte par devis). */
    Optional<Invoice> findFirstByTenantIdAndSourceQuotationIdAndInvoiceTypeOrderByCreatedAtAsc(
            UUID tenantId, UUID quotationId, InvoiceType invoiceType);

    /** Facture de solde ayant déduit une facture d'acompte. */
    Optional<Invoice> findFirstByTenantIdAndDepositInvoiceId(UUID tenantId, UUID depositInvoiceId);

    Page<Invoice> findByTenantIdAndWorkspaceIdOrderByIssueDateDesc(UUID tenantId, UUID workspaceId, Pageable pageable);

    /**
     * Liste filtrée : client optionnel, « ouvertes » = émises avec un reste à payer, recherche sur le numéro.
     * Le tri vient du {@link Pageable}.
     */
    @Query("""
            SELECT i FROM Invoice i
            WHERE i.tenantId = :tenantId AND i.workspaceId = :workspaceId
            AND (:customerId IS NULL OR i.customerId = :customerId)
            AND (:openOnly = false OR (i.status IN ('ISSUED', 'PARTIALLY_PAID') AND i.amountPaid < i.totalTtc))
            AND (:qPattern IS NULL OR LOWER(i.number) LIKE :qPattern)
            """)
    Page<Invoice> search(@Param("tenantId") UUID tenantId,
                         @Param("workspaceId") UUID workspaceId,
                         @Param("customerId") UUID customerId,
                         @Param("openOnly") boolean openOnly,
                         @Param("qPattern") String qPattern,
                         Pageable pageable);

    @Query("""
            SELECT COALESCE(SUM(i.totalTtc), 0) FROM Invoice i
            WHERE i.tenantId = :tenantId
            AND (:workspaceId IS NULL OR i.workspaceId = :workspaceId)
            AND i.status IN ('ISSUED', 'PARTIALLY_PAID', 'PAID')
            AND i.issueDate BETWEEN :from AND :to
            AND i.invoiceType <> 'CREDIT_NOTE'
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
            AND i.invoiceType <> 'CREDIT_NOTE'
            """)
    BigDecimal sumInvoicedRevenueAllTime(@Param("tenantId") UUID tenantId,
                                         @Param("workspaceId") UUID workspaceId);

    @Query("""
            SELECT COUNT(i) FROM Invoice i
            WHERE i.tenantId = :tenantId
            AND (:workspaceId IS NULL OR i.workspaceId = :workspaceId)
            AND i.status IN ('ISSUED', 'PARTIALLY_PAID', 'PAID')
            AND i.issueDate BETWEEN :from AND :to
            AND i.invoiceType <> 'CREDIT_NOTE'
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
            AND i.invoiceType <> 'CREDIT_NOTE'
            """)
    long countInvoicedInvoicesAllTime(@Param("tenantId") UUID tenantId,
                                      @Param("workspaceId") UUID workspaceId);

    @Query("""
            SELECT COUNT(i) FROM Invoice i
            WHERE i.tenantId = :tenantId
            AND (:workspaceId IS NULL OR i.workspaceId = :workspaceId)
            AND i.status IN ('ISSUED', 'PARTIALLY_PAID')
            AND i.invoiceType <> 'CREDIT_NOTE'
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
