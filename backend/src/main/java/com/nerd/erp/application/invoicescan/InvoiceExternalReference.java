package com.nerd.erp.application.invoicescan;

public final class InvoiceExternalReference {

    private InvoiceExternalReference() {
    }

    public static String fromExtraction(ExtractedInvoiceData data) {
        if (data == null) {
            return null;
        }
        if (data.invoiceNumber() != null && !data.invoiceNumber().isBlank()) {
            return data.invoiceNumber().trim();
        }
        if (data.orderNumber() != null && !data.orderNumber().isBlank()) {
            return data.orderNumber().trim();
        }
        return null;
    }
}
