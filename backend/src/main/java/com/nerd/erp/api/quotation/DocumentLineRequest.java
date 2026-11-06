package com.nerd.erp.api.quotation;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

public record DocumentLineRequest(
        UUID productId,
        @NotBlank String description,
        @NotNull @DecimalMin("0.0001") BigDecimal quantity,
        @NotNull @DecimalMin("0.0") BigDecimal unitPriceHt,
        @NotNull BigDecimal vatRate,
        /** Remise sur la ligne en % du HT ligne (null = 0). */
        @DecimalMin("0") @DecimalMax("100") @Digits(integer = 3, fraction = 2) BigDecimal discountPercent
) {
}
