package com.nerd.erp.application.document.pdf;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Représentation neutre (devis ou facture) consommée par {@link PdfDocumentRenderer}.
 * Les services métier construisent ce modèle ; le renderer ne connaît ni JPA ni les DTOs.
 */
public record PdfDocumentModel(
        /** Libellé du type : "DEVIS", "FACTURE", "FACTURE D'ACOMPTE", "AVOIR". */
        String kind,
        String number,
        String statusLabel,
        LocalDate issueDate,
        String secondaryDateLabel,
        LocalDate secondaryDate,
        Party issuer,
        Party recipient,
        String subject,
        String summary,
        List<Line> lines,
        Totals totals,
        List<String> paymentTerms,
        BankDetails bank,
        /** Zone « Bon pour accord » (devis uniquement). */
        boolean signatureBlock,
        List<String> footerLines,
        String currencyCode
) {

    public record Party(String title, List<String> lines) {
    }

    public record Line(String sku, String label, String detail, BigDecimal quantity, String unit,
                       BigDecimal unitPriceHt, BigDecimal vatRate, BigDecimal lineTotalHt,
                       /** Remise de ligne en % (null ou 0 = aucune). */
                       BigDecimal discountPercent) {

        public Line(String sku, String label, String detail, BigDecimal quantity, String unit,
                    BigDecimal unitPriceHt, BigDecimal vatRate, BigDecimal lineTotalHt) {
            this(sku, label, detail, quantity, unit, unitPriceHt, vatRate, lineTotalHt, null);
        }

        public boolean hasDiscount() {
            return discountPercent != null && discountPercent.signum() > 0;
        }
    }

    /** Ventilation de la TVA par taux. */
    public record VatLine(BigDecimal rate, BigDecimal baseHt, BigDecimal vatAmount) {
    }

    public record Totals(BigDecimal subtotalHt,
                         BigDecimal discountPercent,
                         BigDecimal discountAmountHt,
                         BigDecimal totalHt,
                         BigDecimal totalVat,
                         BigDecimal totalTtc,
                         /** Acompte (devis) ; null si non applicable. */
                         BigDecimal depositPercent,
                         BigDecimal depositAmountTtc,
                         /** Déjà réglé (facture) ; null si non applicable. */
                         BigDecimal amountPaid,
                         /** Récapitulatif TVA par taux ; vide = ligne « TVA » unique. */
                         List<VatLine> vatLines) {

        public Totals(BigDecimal subtotalHt, BigDecimal discountPercent, BigDecimal discountAmountHt,
                      BigDecimal totalHt, BigDecimal totalVat, BigDecimal totalTtc, BigDecimal depositPercent,
                      BigDecimal depositAmountTtc, BigDecimal amountPaid) {
            this(subtotalHt, discountPercent, discountAmountHt, totalHt, totalVat, totalTtc, depositPercent,
                    depositAmountTtc, amountPaid, List.of());
        }

        public boolean hasDiscount() {
            return discountAmountHt != null && discountAmountHt.signum() > 0;
        }

        public boolean hasDeposit() {
            return depositPercent != null && depositPercent.signum() > 0;
        }

        public boolean hasAmountPaid() {
            return amountPaid != null && amountPaid.signum() > 0;
        }
    }

    public record BankDetails(String holder, String iban, String bic) {
        public boolean isEmpty() {
            return (iban == null || iban.isBlank()) && (bic == null || bic.isBlank());
        }
    }
}
