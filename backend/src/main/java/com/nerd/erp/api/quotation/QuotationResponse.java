package com.nerd.erp.api.quotation;

import com.nerd.erp.domain.quotation.QuotationStatus;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Construit via builder nommé ; voir {@code QuotationMapper}. */
@Builder
public record QuotationResponse(
        UUID id,
        UUID workspaceId,
        UUID customerId,
        String number,
        QuotationStatus status,
        LocalDate issueDate,
        LocalDate validUntil,
        /** Sous-total HT brut avant remise. */
        BigDecimal subtotalHt,
        BigDecimal discountPercent,
        BigDecimal discountAmountHt,
        /** Totaux nets après remise. */
        BigDecimal totalHt,
        BigDecimal totalVat,
        BigDecimal totalTtc,
        BigDecimal depositPercent,
        /** Acompte TTC = totalTtc × depositPercent / 100. */
        BigDecimal depositAmountTtc,
        String notes,
        UUID convertedInvoiceId,
        List<QuotationLineResponse> lines
) {
}
