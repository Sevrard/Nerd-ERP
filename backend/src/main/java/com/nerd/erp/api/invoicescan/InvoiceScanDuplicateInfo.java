package com.nerd.erp.api.invoicescan;

import com.nerd.erp.domain.invoice.Invoice;

import java.math.BigDecimal;
import java.util.UUID;

public record InvoiceScanDuplicateInfo(
        boolean detected,
        UUID existingInvoiceId,
        String existingInvoiceNumber,
        String externalReference,
        BigDecimal existingTotalTtc,
        BigDecimal existingAmountPaid,
        BigDecimal scannedAmountTtc
) {
    public static InvoiceScanDuplicateInfo none() {
        return new InvoiceScanDuplicateInfo(false, null, null, null, null, null, null);
    }

    public static InvoiceScanDuplicateInfo of(Invoice existing, String externalReference, BigDecimal scannedAmountTtc) {
        return new InvoiceScanDuplicateInfo(
                true,
                existing.getId(),
                existing.getNumber(),
                externalReference,
                existing.getTotalTtc(),
                existing.getAmountPaid(),
                scannedAmountTtc);
    }
}
