package com.nerd.erp.api.quotation;

import com.nerd.erp.domain.quotation.QuotationStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record QuotationResponse(
        UUID id,
        UUID workspaceId,
        UUID customerId,
        String number,
        QuotationStatus status,
        LocalDate issueDate,
        LocalDate validUntil,
        BigDecimal totalHt,
        BigDecimal totalVat,
        BigDecimal totalTtc,
        String notes,
        UUID convertedInvoiceId,
        List<QuotationLineResponse> lines
) {
}
