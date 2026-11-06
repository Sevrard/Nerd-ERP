package com.nerd.erp.application.quotation;

import com.nerd.erp.api.document.VatLineResponse;
import com.nerd.erp.api.quotation.QuotationLineResponse;
import com.nerd.erp.api.quotation.QuotationResponse;
import com.nerd.erp.application.document.DocumentLineBuilder;
import com.nerd.erp.application.money.MoneyCalculator;
import com.nerd.erp.domain.invoice.Invoice;
import com.nerd.erp.domain.quotation.Quotation;
import com.nerd.erp.domain.quotation.QuotationLine;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

@Component
public class QuotationMapper {

    public QuotationResponse toResponse(Quotation q) {
        return toResponse(q, null);
    }

    /** @param depositInvoice facture d'acompte émise sur ce devis, ou {@code null}. */
    public QuotationResponse toResponse(Quotation q, Invoice depositInvoice) {
        return toResponse(q, depositInvoice, null);
    }

    /** @param customerName libellé client résolu par l'appelant (peut être {@code null}). */
    public QuotationResponse toResponse(Quotation q, Invoice depositInvoice, String customerName) {
        List<QuotationLineResponse> lines = q.getLines().stream().map(this::toLine).toList();
        BigDecimal discountAmount = nz(q.getDiscountAmountHt());
        BigDecimal totalHt = nz(q.getTotalHt());
        return QuotationResponse.builder()
                .id(q.getId())
                .workspaceId(q.getWorkspaceId())
                .customerId(q.getCustomerId())
                .customerName(customerName)
                .number(q.getNumber())
                .status(q.getStatus())
                .issueDate(q.getIssueDate())
                .validUntil(q.getValidUntil())
                .subtotalHt(totalHt.add(discountAmount))
                .discountPercent(nz(q.getDiscountPercent()))
                .discountAmountHt(discountAmount)
                .totalHt(totalHt)
                .totalVat(nz(q.getTotalVat()))
                .totalTtc(nz(q.getTotalTtc()))
                .depositPercent(nz(q.getDepositPercent()))
                .depositAmountTtc(MoneyCalculator.percentOf(q.getTotalTtc(), q.getDepositPercent()))
                .vatBreakdown(VatLineResponse.of(DocumentLineBuilder.vatBreakdown(q.getLines(), q.getDiscountPercent())))
                .notes(q.getNotes())
                .convertedInvoiceId(q.getConvertedInvoiceId())
                .depositInvoiceId(depositInvoice == null ? null : depositInvoice.getId())
                .depositInvoiceNumber(depositInvoice == null ? null : depositInvoice.getNumber())
                .sentAt(q.getSentAt())
                .publicLinkActive(q.getPublicTokenHash() != null)
                .decidedAt(q.getDecidedAt())
                .decidedBy(q.getDecidedBy())
                .decisionComment(q.getDecisionComment())
                .decisionSource(q.getDecisionSource())
                .lines(lines)
                .build();
    }

    private QuotationLineResponse toLine(QuotationLine l) {
        return new QuotationLineResponse(l.getId(), l.getProductId(), l.getLineOrder(),
                l.getDescription(), l.getQuantity(), l.getUnitPriceHt(), l.getVatRate(), nz(l.getDiscountPercent()),
                l.getLineTotalHt(), l.getLineTotalVat(), l.getLineTotalTtc());
    }

    private static BigDecimal nz(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
