package com.nerd.erp.application.document;

import com.nerd.erp.domain.invoice.InvoiceLine;
import com.nerd.erp.domain.quotation.QuotationLine;
import com.nerd.erp.application.money.MoneyCalculator;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class DocumentLineBuilder {

    private DocumentLineBuilder() {
    }

    public record LineInput(UUID productId, String description, BigDecimal quantity, BigDecimal unitPriceHt,
                            BigDecimal vatRate, BigDecimal discountPercent) {

        /** Ligne sans remise. */
        public LineInput(UUID productId, String description, BigDecimal quantity, BigDecimal unitPriceHt,
                         BigDecimal vatRate) {
            this(productId, description, quantity, unitPriceHt, vatRate, null);
        }

        public BigDecimal discountOrZero() {
            return discountPercent == null ? BigDecimal.ZERO : discountPercent;
        }
    }

    public static List<QuotationLine> buildQuotationLines(UUID tenantId, List<LineInput> inputs) {
        List<QuotationLine> lines = new ArrayList<>();
        int order = 0;
        for (LineInput input : inputs) {
            MoneyCalculator.LineTotals totals = MoneyCalculator.computeLine(
                    input.quantity(), input.unitPriceHt(), input.vatRate(), input.discountPercent());
            QuotationLine line = new QuotationLine();
            line.setTenantId(tenantId);
            line.setProductId(input.productId());
            line.setLineOrder(order++);
            line.setDescription(input.description());
            line.setQuantity(input.quantity());
            line.setUnitPriceHt(input.unitPriceHt());
            line.setVatRate(input.vatRate());
            line.setDiscountPercent(input.discountOrZero());
            line.setLineTotalHt(totals.totalHt());
            line.setLineTotalVat(totals.totalVat());
            line.setLineTotalTtc(totals.totalTtc());
            lines.add(line);
        }
        return lines;
    }

    public static List<InvoiceLine> buildInvoiceLines(UUID tenantId, List<LineInput> inputs) {
        List<InvoiceLine> lines = new ArrayList<>();
        int order = 0;
        for (LineInput input : inputs) {
            MoneyCalculator.LineTotals totals = MoneyCalculator.computeLine(
                    input.quantity(), input.unitPriceHt(), input.vatRate(), input.discountPercent());
            InvoiceLine line = new InvoiceLine();
            line.setTenantId(tenantId);
            line.setProductId(input.productId());
            line.setLineOrder(order++);
            line.setDescription(input.description());
            line.setQuantity(input.quantity());
            line.setUnitPriceHt(input.unitPriceHt());
            line.setVatRate(input.vatRate());
            line.setDiscountPercent(input.discountOrZero());
            line.setLineTotalHt(totals.totalHt());
            line.setLineTotalVat(totals.totalVat());
            line.setLineTotalTtc(totals.totalTtc());
            lines.add(line);
        }
        return lines;
    }

    public static MoneyCalculator.DocumentTotals totalsFromQuotationLines(List<QuotationLine> lines) {
        return MoneyCalculator.sumLines(lines.stream()
                .map(l -> new MoneyCalculator.LineTotals(l.getLineTotalHt(), l.getLineTotalVat(), l.getLineTotalTtc()))
                .toList());
    }

    public static MoneyCalculator.DocumentTotals totalsFromInvoiceLines(List<InvoiceLine> lines) {
        return MoneyCalculator.sumLines(lines.stream()
                .map(l -> new MoneyCalculator.LineTotals(l.getLineTotalHt(), l.getLineTotalVat(), l.getLineTotalTtc()))
                .toList());
    }

    /** Ventilation TVA d'un devis, nette de sa remise globale. */
    public static List<MoneyCalculator.VatLine> vatBreakdown(List<QuotationLine> lines, BigDecimal discountPercent) {
        return MoneyCalculator.vatBreakdown(lines.stream()
                .map(l -> new MoneyCalculator.RatedLine(l.getVatRate(), l.getLineTotalHt(), l.getLineTotalVat()))
                .toList(), discountPercent);
    }

    /** Ventilation TVA d'une facture, nette de sa remise globale. */
    public static List<MoneyCalculator.VatLine> vatBreakdownOfInvoice(List<InvoiceLine> lines, BigDecimal discountPercent) {
        return MoneyCalculator.vatBreakdown(lines.stream()
                .map(l -> new MoneyCalculator.RatedLine(l.getVatRate(), l.getLineTotalHt(), l.getLineTotalVat()))
                .toList(), discountPercent);
    }
}
