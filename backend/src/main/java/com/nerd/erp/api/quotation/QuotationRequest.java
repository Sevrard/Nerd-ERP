package com.nerd.erp.api.quotation;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record QuotationRequest(
        @NotNull UUID workspaceId,
        @NotNull UUID customerId,
        LocalDate issueDate,
        LocalDate validUntil,
        String notes,
        @NotEmpty @Valid List<DocumentLineRequest> lines
) {
}
