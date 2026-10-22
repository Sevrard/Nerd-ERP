package com.nerd.erp.api.payment;

import com.nerd.erp.domain.payment.PaymentMethod;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record PaymentResponse(
        UUID id,
        UUID workspaceId,
        UUID customerId,
        BigDecimal amount,
        LocalDate paymentDate,
        PaymentMethod method,
        String reference,
        String notes,
        List<AllocationResponse> allocations
) {
    public record AllocationResponse(UUID invoiceId, BigDecimal amount) {
    }
}
