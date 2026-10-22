package com.nerd.erp.api.quotation;

import java.math.BigDecimal;
import java.util.UUID;

public record QuotationLineResponse(
        UUID id,
        UUID productId,
        int lineOrder,
        String description,
        BigDecimal quantity,
        BigDecimal unitPriceHt,
        BigDecimal vatRate,
        BigDecimal lineTotalHt,
        BigDecimal lineTotalVat,
        BigDecimal lineTotalTtc
) {
}
