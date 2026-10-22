package com.nerd.erp.api.invoicescan;

import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record InvoiceScanCommitRequest(
        @NotNull UUID scanId,
        @NotNull UUID workspaceId,
        boolean createCustomerConfirmed,
        boolean invoiceConfirmed,
        boolean recordDuplicateAsPayment,
        boolean duplicatePaymentConfirmed,
        String notes,
        String merchant,
        UUID customerId,
        String orderNumber,
        String invoiceNumber,
        LocalDate issueDate,
        BigDecimal totalTtc
) {
}
