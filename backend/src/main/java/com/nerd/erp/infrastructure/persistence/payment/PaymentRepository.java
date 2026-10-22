package com.nerd.erp.infrastructure.persistence.payment;

import com.nerd.erp.domain.payment.Payment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    Optional<Payment> findByIdAndTenantId(UUID id, UUID tenantId);

    boolean existsByTenantIdAndCustomerId(UUID tenantId, UUID customerId);

    Page<Payment> findByTenantIdAndWorkspaceIdOrderByPaymentDateDesc(UUID tenantId, UUID workspaceId, Pageable pageable);

    @Query("""
            SELECT COALESCE(SUM(p.amount), 0) FROM Payment p
            WHERE p.tenantId = :tenantId
            AND (:workspaceId IS NULL OR p.workspaceId = :workspaceId)
            AND p.paymentDate BETWEEN :from AND :to
            """)
    BigDecimal sumCollected(@Param("tenantId") UUID tenantId,
                            @Param("workspaceId") UUID workspaceId,
                            @Param("from") LocalDate from,
                            @Param("to") LocalDate to);

    @Query("""
            SELECT COALESCE(SUM(p.amount), 0) FROM Payment p
            WHERE p.tenantId = :tenantId
            AND (:workspaceId IS NULL OR p.workspaceId = :workspaceId)
            """)
    BigDecimal sumCollectedAllTime(@Param("tenantId") UUID tenantId,
                                   @Param("workspaceId") UUID workspaceId);
}
