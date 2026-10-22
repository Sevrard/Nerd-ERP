package com.nerd.erp.application.invoicescan;

import java.math.BigDecimal;
import java.util.List;

public record ExtractedInvoiceData(
        String invoiceNumber,
        String orderNumber,
        String merchant,
        String issueDate,
        ExtractedCustomer customer,
        List<ExtractedLine> lines,
        BigDecimal shippingAmountHt,
        BigDecimal shippingVatRate,
        BigDecimal totalHt,
        BigDecimal totalVat,
        BigDecimal totalTtc,
        String currency
) {
    public record ExtractedCustomer(
            String customerType,
            String companyName,
            String firstName,
            String lastName,
            String email,
            String phone,
            String addressLine1,
            String addressLine2,
            String postalCode,
            String city,
            String countryCode
    ) {
    }

    public record ExtractedLine(
            String description,
            BigDecimal quantity,
            BigDecimal unitPriceHt,
            BigDecimal vatRate
    ) {
    }
}
