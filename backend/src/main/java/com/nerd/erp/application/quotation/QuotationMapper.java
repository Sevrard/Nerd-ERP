package com.nerd.erp.application.quotation;

import com.nerd.erp.api.quotation.QuotationLineResponse;
import com.nerd.erp.api.quotation.QuotationResponse;
import com.nerd.erp.application.money.MoneyCalculator;
import com.nerd.erp.domain.quotation.Quotation;
import com.nerd.erp.domain.quotation.QuotationLine;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

@Component
public class QuotationMapper {

    public QuotationResponse toResponse(Quotation q) {
        List<QuotationLineResponse> lines = q.getLines().stream().map(this::toLine).toList();
        BigDecimal discountAmount = nz(q.getDiscountAmountHt());
        BigDecimal totalHt = nz(q.getTotalHt());
        return QuotationResponse.builder()
                .id(q.getId())
                .workspaceId(q.getWorkspaceId())
                .customerId(q.getCustomerId())
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
                .notes(q.getNotes())
                .convertedInvoiceId(q.getConvertedInvoiceId())
                .lines(lines)
                .build();
    }

    private QuotationLineResponse toLine(QuotationLine l) {
        return new QuotationLineResponse(l.getId(), l.getProductId(), l.getLineOrder(),
                l.getDescription(), l.getQuantity(), l.getUnitPriceHt(), l.getVatRate(),
                l.getLineTotalHt(), l.getLineTotalVat(), l.getLineTotalTtc());
    }

    private static BigDecimal nz(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
