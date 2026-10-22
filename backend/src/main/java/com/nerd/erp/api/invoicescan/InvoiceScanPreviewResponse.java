package com.nerd.erp.api.invoicescan;

import com.nerd.erp.application.invoicescan.CustomerMatchStatus;
import com.nerd.erp.application.invoicescan.ExtractedInvoiceData;
import com.nerd.erp.application.invoicescan.InvoiceScanSession;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record InvoiceScanPreviewResponse(
        UUID scanId,
        CustomerMatchStatus customerMatchStatus,
        UUID matchedCustomerId,
        ExtractedInvoicePayload extraction,
        InvoiceScanDuplicateInfo duplicate
) {
    public static InvoiceScanPreviewResponse from(InvoiceScanSession session, InvoiceScanDuplicateInfo duplicate) {
        return new InvoiceScanPreviewResponse(
                session.id(),
                session.customerMatchStatus(),
                session.matchedCustomerId(),
                ExtractedInvoicePayload.from(session.extraction()),
                duplicate != null ? duplicate : InvoiceScanDuplicateInfo.none());
    }

    public record ExtractedInvoicePayload(
            String invoiceNumber,
            String orderNumber,
            String merchant,
            String issueDate,
            ExtractedCustomerPayload customer,
            List<ExtractedLinePayload> lines,
            BigDecimal shippingAmountHt,
            BigDecimal shippingVatRate,
            BigDecimal totalHt,
            BigDecimal totalVat,
            BigDecimal totalTtc,
            String currency
    ) {
        static ExtractedInvoicePayload from(ExtractedInvoiceData data) {
            return new ExtractedInvoicePayload(
                    data.invoiceNumber(),
                    data.orderNumber(),
                    data.merchant(),
                    data.issueDate(),
                    ExtractedCustomerPayload.from(data.customer()),
                    data.lines().stream().map(ExtractedLinePayload::from).toList(),
                    data.shippingAmountHt(),
                    data.shippingVatRate(),
                    data.totalHt(),
                    data.totalVat(),
                    data.totalTtc(),
                    data.currency());
        }
    }

    public record ExtractedCustomerPayload(
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
        static ExtractedCustomerPayload from(ExtractedInvoiceData.ExtractedCustomer c) {
            if (c == null) {
                return new ExtractedCustomerPayload(null, null, null, null, null, null,
                        null, null, null, null, null);
            }
            return new ExtractedCustomerPayload(
                    c.customerType(), c.companyName(), c.firstName(), c.lastName(),
                    c.email(), c.phone(), c.addressLine1(), c.addressLine2(),
                    c.postalCode(), c.city(), c.countryCode());
        }
    }

    public record ExtractedLinePayload(
            String description,
            BigDecimal quantity,
            BigDecimal unitPriceHt,
            BigDecimal vatRate
    ) {
        static ExtractedLinePayload from(ExtractedInvoiceData.ExtractedLine line) {
            return new ExtractedLinePayload(
                    line.description(), line.quantity(), line.unitPriceHt(), line.vatRate());
        }
    }
}
