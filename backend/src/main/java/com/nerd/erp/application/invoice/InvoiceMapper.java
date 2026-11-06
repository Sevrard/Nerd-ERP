package com.nerd.erp.application.invoice;

import com.nerd.erp.api.document.VatLineResponse;
import com.nerd.erp.application.document.DocumentLineBuilder;
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
        return toResponse(invoice, new Links(null, List.of(), null, null));
    }

    /**
     * Documents liés résolus par le service (le mapper n'accède pas aux repositories).
     *
     * @param creditedInvoiceNumber numéro de la facture d'origine (avoir uniquement)
     * @param creditNotes avoirs émis sur cette facture
     * @param depositInvoice acompte déduit (facture de solde)
     * @param balanceInvoice facture de solde (facture d'acompte)
     */
    public record Links(String creditedInvoiceNumber,
                        List<InvoiceResponse.LinkedDocument> creditNotes,
                        InvoiceResponse.LinkedDocument depositInvoice,
                        InvoiceResponse.LinkedDocument balanceInvoice) {
    }

    public InvoiceResponse toResponse(Invoice invoice, Links links) {
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
                .creditedInvoiceId(invoice.getCreditedInvoiceId())
                .creditedInvoiceNumber(links.creditedInvoiceNumber())
                .creditReason(invoice.getCreditReason())
                .creditNotes(links.creditNotes() == null ? List.of() : links.creditNotes())
                .depositInvoice(links.depositInvoice())
                .balanceInvoice(links.balanceInvoice())
                .vatBreakdown(VatLineResponse.of(
                        DocumentLineBuilder.vatBreakdownOfInvoice(invoice.getLines(), invoice.getDiscountPercent())))
                .sentAt(invoice.getSentAt())
                .reminderCount(invoice.getReminderCount())
                .lastReminderAt(invoice.getLastReminderAt())
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
                nz(line.getDiscountPercent()),
                line.getLineTotalHt(),
                line.getLineTotalVat(),
                line.getLineTotalTtc(),
                line.getProductId());
    }

    private static BigDecimal nz(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
