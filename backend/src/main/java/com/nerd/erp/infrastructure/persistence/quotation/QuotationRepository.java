package com.nerd.erp.infrastructure.persistence.quotation;

import com.nerd.erp.domain.quotation.Quotation;
import com.nerd.erp.domain.quotation.QuotationStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface QuotationRepository extends JpaRepository<Quotation, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT q FROM Quotation q WHERE q.id = :id AND q.tenantId = :tenantId")
    Optional<Quotation> findByIdAndTenantIdForUpdate(@Param("id") UUID id, @Param("tenantId") UUID tenantId);

    Optional<Quotation> findByIdAndTenantId(UUID id, UUID tenantId);

    Page<Quotation> findByTenantIdAndWorkspaceIdOrderByCreatedAtDesc(UUID tenantId, UUID workspaceId, Pageable pageable);

    long countByTenantIdAndWorkspaceIdAndStatus(UUID tenantId, UUID workspaceId, QuotationStatus status);

    boolean existsByConvertedInvoiceIdAndTenantId(UUID convertedInvoiceId, UUID tenantId);

    boolean existsByTenantIdAndCustomerId(UUID tenantId, UUID customerId);
}

