package com.nerd.erp.api.invoice;

import com.nerd.erp.domain.invoice.InvoiceStatus;
import com.nerd.erp.domain.invoice.InvoiceType;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record InvoiceResponse(
        UUID id,
        UUID workspaceId,
        UUID customerId,
        UUID sourceQuotationId,
        InvoiceType invoiceType,
        String number,
        InvoiceStatus status,
        LocalDate issueDate,
        LocalDate dueDate,
        BigDecimal totalHt,
        BigDecimal totalVat,
        BigDecimal totalTtc,
        BigDecimal amountPaid,
        Map<String, Object> snapshotCustomer,
        String notes,
        String externalReference,
        String merchant,
        String orderNumber,
        Instant issuedAt,
        List<InvoiceLineResponse> lines
) {
}
