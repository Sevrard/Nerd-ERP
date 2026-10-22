package com.nerd.erp.infrastructure.persistence.payment;

import com.nerd.erp.domain.payment.PaymentAllocation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface PaymentAllocationRepository extends JpaRepository<PaymentAllocation, UUID> {

    boolean existsByTenantIdAndInvoiceId(UUID tenantId, UUID invoiceId);

    void deleteByTenantIdAndInvoiceId(UUID tenantId, UUID invoiceId);
}
