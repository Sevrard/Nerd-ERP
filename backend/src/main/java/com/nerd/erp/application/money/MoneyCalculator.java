package com.nerd.erp.application.money;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class MoneyCalculator {

    private static final int MONEY_SCALE = 4;
    private static final RoundingMode ROUNDING = RoundingMode.HALF_UP;

    private MoneyCalculator() {
    }

    public static BigDecimal lineTotalHt(BigDecimal quantity, BigDecimal unitPriceHt) {
        return quantity.multiply(unitPriceHt).setScale(MONEY_SCALE, ROUNDING);
    }

    public static BigDecimal lineTotalVat(BigDecimal lineTotalHt, BigDecimal vatRate) {
        return lineTotalHt.multiply(vatRate).divide(BigDecimal.valueOf(100), MONEY_SCALE, ROUNDING);
    }

    public static BigDecimal lineTotalTtc(BigDecimal lineTotalHt, BigDecimal lineTotalVat) {
        return lineTotalHt.add(lineTotalVat).setScale(MONEY_SCALE, ROUNDING);
    }

    public static LineTotals computeLine(BigDecimal quantity, BigDecimal unitPriceHt, BigDecimal vatRate) {
        BigDecimal ht = lineTotalHt(quantity, unitPriceHt);
        BigDecimal vat = lineTotalVat(ht, vatRate);
        BigDecimal ttc = lineTotalTtc(ht, vat);
        return new LineTotals(ht, vat, ttc);
    }

    public record LineTotals(BigDecimal totalHt, BigDecimal totalVat, BigDecimal totalTtc) {
    }

    public record DocumentTotals(BigDecimal totalHt, BigDecimal totalVat, BigDecimal totalTtc) {
    }

    public static DocumentTotals sumLines(java.util.Collection<LineTotals> lines) {
        BigDecimal ht = BigDecimal.ZERO;
        BigDecimal vat = BigDecimal.ZERO;
        BigDecimal ttc = BigDecimal.ZERO;
        for (LineTotals line : lines) {
            ht = ht.add(line.totalHt());
            vat = vat.add(line.totalVat());
            ttc = ttc.add(line.totalTtc());
        }
        return new DocumentTotals(
                ht.setScale(MONEY_SCALE, ROUNDING),
                vat.setScale(MONEY_SCALE, ROUNDING),
                ttc.setScale(MONEY_SCALE, ROUNDING));
    }
}
