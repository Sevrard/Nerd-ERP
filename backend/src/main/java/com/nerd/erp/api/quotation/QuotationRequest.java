package com.nerd.erp.api.quotation;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record QuotationRequest(
        @NotNull UUID workspaceId,
        @NotNull UUID customerId,
        LocalDate issueDate,
        LocalDate validUntil,
        String notes,
        /** Remise commerciale globale en % du HT (null = 0). */
        @DecimalMin("0") @DecimalMax("100") @Digits(integer = 3, fraction = 2) BigDecimal discountPercent,
        /** Acompte à la commande en % du TTC (null = 0). */
        @DecimalMin("0") @DecimalMax("100") @Digits(integer = 3, fraction = 2) BigDecimal depositPercent,
        @NotEmpty @Valid List<DocumentLineRequest> lines
) {
}
