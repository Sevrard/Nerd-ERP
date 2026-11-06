package com.nerd.erp.application.invoice;

import com.nerd.erp.domain.invoice.Invoice;
import com.nerd.erp.domain.invoice.InvoiceLine;
import com.nerd.erp.api.invoice.InvoiceLineResponse;
import com.nerd.erp.api.invoice.InvoiceResponse;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

@Component
public class InvoiceMapper {

    public InvoiceResponse toResponse(Invoice invoice) {
        List<InvoiceLineResponse> lines = invoice.getLines().stream().map(this::toLine).toList();
        BigDecimal discountAmount = nz(invoice.getDiscountAmountHt());
        BigDecimal totalHt = nz(invoice.getTotalHt());
        return InvoiceResponse.builder()
                .id(invoice.getId())
                .workspaceId(invoice.getWorkspaceId())
                .customerId(invoice.getCustomerId())
                .sourceQuotationId(invoice.getSourceQuotationId())
                .invoiceType(invoice.getInvoiceType())
                .number(invoice.getNumber())
                .status(invoice.getStatus())
                .issueDate(invoice.getIssueDate())
                .dueDate(invoice.getDueDate())
                .subtotalHt(totalHt.add(discountAmount))
                .discountPercent(nz(invoice.getDiscountPercent()))
                .discountAmountHt(discountAmount)
                .totalHt(totalHt)
                .totalVat(invoice.getTotalVat())
                .totalTtc(invoice.getTotalTtc())
                .amountPaid(invoice.getAmountPaid())
                .snapshotCustomer(invoice.getSnapshotCustomer())
                .notes(invoice.getNotes())
                .externalReference(invoice.getExternalReference())
                .merchant(invoice.getMerchant())
                .orderNumber(invoice.getOrderNumber())
                .issuedAt(invoice.getIssuedAt())
                .lines(lines)
                .build();
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
                line.getProductId());
    }

    private static BigDecimal nz(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
