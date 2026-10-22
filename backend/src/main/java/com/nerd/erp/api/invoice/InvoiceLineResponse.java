package com.nerd.erp.api.invoice;

import java.math.BigDecimal;
import java.util.UUID;

public record InvoiceLineResponse(
        UUID id,
        int lineOrder,
        String description,
        BigDecimal quantity,
        BigDecimal unitPriceHt,
        BigDecimal vatRate,
        BigDecimal lineTotalHt,
        BigDecimal lineTotalVat,
        BigDecimal lineTotalTtc,
        UUID productId,
        Long etsyListingId,
        Long etsyTransactionId
) {
}
