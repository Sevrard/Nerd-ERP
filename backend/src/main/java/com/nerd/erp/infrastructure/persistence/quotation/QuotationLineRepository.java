package com.nerd.erp.infrastructure.persistence.quotation;

import com.nerd.erp.domain.quotation.QuotationLine;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface QuotationLineRepository extends JpaRepository<QuotationLine, UUID> {

    boolean existsByTenantIdAndProductId(UUID tenantId, UUID productId);
}
