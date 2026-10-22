package com.nerd.erp.application.invoice;

import com.nerd.erp.domain.invoice.Invoice;
import com.nerd.erp.domain.invoice.InvoiceLine;
import com.nerd.erp.api.invoice.InvoiceLineResponse;
import com.nerd.erp.api.invoice.InvoiceResponse;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class InvoiceMapper {

    public InvoiceResponse toResponse(Invoice invoice) {
        List<InvoiceLineResponse> lines = invoice.getLines().stream().map(this::toLine).toList();
        return new InvoiceResponse(
                invoice.getId(),
                invoice.getWorkspaceId(),
                invoice.getCustomerId(),
                invoice.getSourceQuotationId(),
                invoice.getInvoiceType(),
                invoice.getNumber(),
                invoice.getStatus(),
                invoice.getIssueDate(),
                invoice.getDueDate(),
                invoice.getTotalHt(),
                invoice.getTotalVat(),
                invoice.getTotalTtc(),
                invoice.getAmountPaid(),
                invoice.getSnapshotCustomer(),
                invoice.getNotes(),
                invoice.getExternalReference(),
                invoice.getMerchant(),
                invoice.getOrderNumber(),
                invoice.getIssuedAt(),
                lines);
    }

    private InvoiceLineResponse toLine(InvoiceLine line) {
        return new InvoiceLineResponse(
                line.getId(),
                line.getLineOrder(),
                line.getDescription(),
                line.getQuantity(),
                line.getUnitPriceHt(),
                line.getVatRate(),
                line.getLineTotalHt(),
                line.getLineTotalVat(),
                line.getLineTotalTtc(),
                line.getProductId(),
                line.getEtsyListingId(),
                line.getEtsyTransactionId());
    }
}
