package com.nerd.erp.integration.invoice;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

import java.io.IOException;

/** Extraction texte d'un PDF généré, pour vérifier les mentions dans les tests. */
final class PdfText {

    private PdfText() {
    }

    static String extract(byte[] pdf) throws IOException {
        try (PDDocument doc = Loader.loadPDF(pdf)) {
            return new PDFTextStripper().getText(doc);
        }
    }
}
