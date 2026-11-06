package com.nerd.erp.application.document.pdf;

import com.nerd.erp.application.money.MoneyCalculator;
import com.nerd.erp.domain.workspace.Workspace;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/** Éléments communs devis / facture : pied de page légal, coordonnées bancaires, objet. */
final class DocumentPdfSupport {

    private DocumentPdfSupport() {
    }

    /**
     * Mentions obligatoires (art. L441-10 C. com.) construites depuis les paramètres du workspace :
     * escompte, pénalités de retard, indemnité forfaitaire de recouvrement, régime de TVA, texte libre.
     */
    static String legalNotice(Workspace w) {
        List<String> parts = new ArrayList<>();
        String discount = w.getEarlyPaymentDiscount();
        parts.add("Escompte pour paiement anticipé : " + (discount == null || discount.isBlank() ? "néant" : discount.trim()) + ".");
        BigDecimal rate = w.getLatePenaltyRate() == null ? new BigDecimal("12") : w.getLatePenaltyRate();
        BigDecimal fee = w.getRecoveryFeeAmount() == null ? new BigDecimal("40") : w.getRecoveryFeeAmount();
        parts.add("En cas de retard de paiement, pénalités au taux annuel de " + plain(rate)
                + " % et indemnité forfaitaire pour frais de recouvrement de " + plain(fee) + " €.");
        String vat = w.getVatRegime() == null ? null : w.getVatRegime().legalMention();
        if (vat != null) {
            parts.add(vat);
        }
        if (w.getLegalFooterText() != null && !w.getLegalFooterText().isBlank()) {
            parts.add(w.getLegalFooterText().trim());
        }
        return String.join(" ", parts);
    }

    private static String plain(BigDecimal value) {
        return value.stripTrailingZeros().scale() <= 0
                ? value.stripTrailingZeros().toPlainString()
                : value.setScale(2, RoundingMode.HALF_UP).toPlainString().replace('.', ',');
    }

    static List<String> footerLines(Workspace w) {
        List<String> parts = new ArrayList<>();
        parts.add(w.getName());
        String address = PdfPartyFactory.addressOneLine(w);
        if (!address.isBlank()) {
            parts.add(address);
        }
        String legal = PdfPartyFactory.legalLine(w);
        if (legal != null) {
            parts.add(legal);
        }
        if (w.getContactEmail() != null && !w.getContactEmail().isBlank()) {
            parts.add(w.getContactEmail());
        }
        if (w.getWebsiteUrl() != null && !w.getWebsiteUrl().isBlank()) {
            parts.add(w.getWebsiteUrl());
        }
        List<String> lines = new ArrayList<>();
        lines.add(String.join(" - ", parts));
        lines.add(legalNotice(w));
        return lines;
    }

    static PdfDocumentModel.BankDetails bank(Workspace w) {
        return new PdfDocumentModel.BankDetails(w.getName(), w.getIban(), w.getBic());
    }

    static List<PdfDocumentModel.VatLine> vatLines(List<MoneyCalculator.VatLine> breakdown) {
        return breakdown.stream()
                .map(v -> new PdfDocumentModel.VatLine(v.rate(), v.baseHt(), v.vatAmount()))
                .toList();
    }

    /** 1re ligne des notes = objet, le reste = résumé (même convention que l'aperçu frontend). */
    static String[] subjectAndSummary(String notes, String defaultSubject) {
        if (notes == null || notes.isBlank()) {
            return new String[]{defaultSubject, null};
        }
        List<String> parts = new ArrayList<>();
        for (String l : notes.split("\\r?\\n")) {
            if (!l.isBlank()) {
                parts.add(l.trim());
            }
        }
        if (parts.isEmpty()) {
            return new String[]{defaultSubject, null};
        }
        String subject = parts.getFirst();
        String summary = parts.size() > 1 ? String.join(" ", parts.subList(1, parts.size())) : null;
        return new String[]{subject, summary};
    }
}
