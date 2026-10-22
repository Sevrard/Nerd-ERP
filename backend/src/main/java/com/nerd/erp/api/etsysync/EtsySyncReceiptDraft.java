package com.nerd.erp.api.etsysync;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record EtsySyncReceiptDraft(
        long receiptId,
        long receiptNumber,
        LocalDate orderDate,
        long buyerUserId,
        String buyerName,
        String buyerEmail,
        String addressLine1,
        String addressLine2,
        String postalCode,
        String city,
        String countryCode,
        BigDecimal totalTtc,
        boolean paid,
        boolean alreadyImported,
        UUID existingInvoiceId,
        List<EtsySyncTransactionDraft> transactions) {
}
