package com.nerd.erp.application.document.pdf;

import com.nerd.erp.domain.workspace.Workspace;

import java.util.ArrayList;
import java.util.List;

/** Éléments communs devis / facture : pied de page légal, coordonnées bancaires, objet. */
final class DocumentPdfSupport {

    static final String LATE_PAYMENT_NOTICE =
            "Escompte pour paiement anticipé : néant. En cas de retard de paiement, pénalités au taux annuel de 12 % "
                    + "et indemnité forfaitaire pour frais de recouvrement de 40 €.";

    private DocumentPdfSupport() {
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
        lines.add(LATE_PAYMENT_NOTICE);
        return lines;
    }

    static PdfDocumentModel.BankDetails bank(Workspace w) {
        return new PdfDocumentModel.BankDetails(w.getName(), w.getIban(), w.getBic());
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
