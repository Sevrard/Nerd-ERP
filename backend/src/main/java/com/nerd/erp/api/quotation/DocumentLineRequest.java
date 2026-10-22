package com.nerd.erp.api.quotation;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

public record DocumentLineRequest(
        UUID productId,
        @NotBlank String description,
        @NotNull @DecimalMin("0.0001") BigDecimal quantity,
        @NotNull @DecimalMin("0.0") BigDecimal unitPriceHt,
        @NotNull BigDecimal vatRate
) {
}
