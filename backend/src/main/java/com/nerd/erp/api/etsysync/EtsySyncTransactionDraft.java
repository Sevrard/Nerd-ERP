package com.nerd.erp.api.etsysync;

import java.math.BigDecimal;

public record EtsySyncTransactionDraft(
        long transactionId, long listingId, String title,
        BigDecimal quantity, BigDecimal unitPriceHt, BigDecimal vatRate, BigDecimal lineTotalTtc) {
}
