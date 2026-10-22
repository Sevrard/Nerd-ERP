package com.nerd.erp.api.payment;

import com.nerd.erp.domain.payment.PaymentMethod;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record PaymentRequest(
        @NotNull UUID workspaceId,
        @NotNull UUID customerId,
        @NotNull @DecimalMin("0.0001") BigDecimal amount,
        @NotNull LocalDate paymentDate,
        PaymentMethod method,
        String reference,
        String notes,
        @NotEmpty @Valid List<AllocationRequest> allocations
) {
    public record AllocationRequest(
            @NotNull UUID invoiceId,
            @NotNull @DecimalMin("0.0001") BigDecimal amount
    ) {
    }
}
