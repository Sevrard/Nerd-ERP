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
        return computeLine(quantity, unitPriceHt, vatRate, null);
    }

    /** Totaux d'une ligne après remise de ligne ({@code discountPercent} en % du HT ligne, null = 0). */
    public static LineTotals computeLine(BigDecimal quantity, BigDecimal unitPriceHt, BigDecimal vatRate,
                                         BigDecimal discountPercent) {
        BigDecimal gross = lineTotalHt(quantity, unitPriceHt);
        BigDecimal ht = gross.subtract(percentOf(gross, discountPercent)).setScale(MONEY_SCALE, ROUNDING);
        BigDecimal vat = lineTotalVat(ht, vatRate);
        BigDecimal ttc = lineTotalTtc(ht, vat);
        return new LineTotals(ht, vat, ttc);
    }

    public record LineTotals(BigDecimal totalHt, BigDecimal totalVat, BigDecimal totalTtc) {
    }

    public record DocumentTotals(BigDecimal totalHt, BigDecimal totalVat, BigDecimal totalTtc) {
    }

    /** Ventilation de la TVA par taux : base HT nette et montant de TVA. */
    public record VatLine(BigDecimal rate, BigDecimal baseHt, BigDecimal vatAmount) {
    }

    /** Ligne minimale pour la ventilation TVA (taux, HT ligne, TVA ligne). */
    public record RatedLine(BigDecimal vatRate, BigDecimal lineHt, BigDecimal lineVat) {
    }

    /**
     * Récapitulatif TVA par taux (mention obligatoire dès que plusieurs taux coexistent), net de la remise
     * globale appliquée proportionnellement comme dans {@link #applyDiscount}. Trié par taux croissant.
     */
    public static java.util.List<VatLine> vatBreakdown(java.util.Collection<RatedLine> lines, BigDecimal discountPercent) {
        BigDecimal pct = discountPercent == null || discountPercent.signum() <= 0 ? BigDecimal.ZERO : discountPercent;
        java.util.TreeMap<BigDecimal, BigDecimal[]> byRate = new java.util.TreeMap<>();
        for (RatedLine line : lines) {
            BigDecimal rate = (line.vatRate() == null ? BigDecimal.ZERO : line.vatRate()).setScale(2, ROUNDING);
            BigDecimal[] acc = byRate.computeIfAbsent(rate, r -> new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
            acc[0] = acc[0].add(line.lineHt() == null ? BigDecimal.ZERO : line.lineHt());
            acc[1] = acc[1].add(line.lineVat() == null ? BigDecimal.ZERO : line.lineVat());
        }
        java.util.List<VatLine> out = new java.util.ArrayList<>();
        for (var e : byRate.entrySet()) {
            BigDecimal base = e.getValue()[0];
            BigDecimal vat = e.getValue()[1];
            if (pct.signum() > 0) {
                base = base.subtract(percentOf(base, pct));
                vat = vat.subtract(percentOf(vat, pct));
            }
            out.add(new VatLine(e.getKey(), base.setScale(MONEY_SCALE, ROUNDING), vat.setScale(MONEY_SCALE, ROUNDING)));
        }
        return out;
    }

    /** Totaux nets après remise globale, avec le montant HT de remise appliqué. */
    public record DiscountedTotals(BigDecimal discountAmountHt, DocumentTotals net) {
    }

    /** {@code amount × percent / 100}, arrondi à l'échelle monétaire. */
    public static BigDecimal percentOf(BigDecimal amount, BigDecimal percent) {
        if (amount == null || percent == null) {
            return BigDecimal.ZERO.setScale(MONEY_SCALE, ROUNDING);
        }
        return amount.multiply(percent).divide(BigDecimal.valueOf(100), MONEY_SCALE, ROUNDING);
    }

    /**
     * Applique une remise commerciale globale (en % du HT) à des totaux bruts.
     * La TVA étant linéaire par rapport au HT ligne à ligne, le même coefficient est
     * appliqué à la TVA totale : équivalent à une remise proportionnelle sur chaque ligne.
     */
    public static DiscountedTotals applyDiscount(DocumentTotals gross, BigDecimal discountPercent) {
        BigDecimal pct = discountPercent == null ? BigDecimal.ZERO : discountPercent;
        if (pct.signum() <= 0) {
            return new DiscountedTotals(BigDecimal.ZERO.setScale(MONEY_SCALE, ROUNDING), gross);
        }
        if (pct.compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new IllegalArgumentException("Remise > 100 %");
        }
        BigDecimal discountHt = percentOf(gross.totalHt(), pct);
        BigDecimal discountVat = percentOf(gross.totalVat(), pct);
        BigDecimal ht = gross.totalHt().subtract(discountHt).setScale(MONEY_SCALE, ROUNDING);
        BigDecimal vat = gross.totalVat().subtract(discountVat).setScale(MONEY_SCALE, ROUNDING);
        BigDecimal ttc = ht.add(vat).setScale(MONEY_SCALE, ROUNDING);
        return new DiscountedTotals(discountHt, new DocumentTotals(ht, vat, ttc));
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
