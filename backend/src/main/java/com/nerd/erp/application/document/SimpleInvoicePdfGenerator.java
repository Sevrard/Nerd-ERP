package com.nerd.erp.application.document;

import com.nerd.erp.domain.invoice.Invoice;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

@Component
public class SimpleInvoicePdfGenerator {

    public byte[] generate(Invoice invoice) {
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage();
            document.addPage(page);
            PDType1Font font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            PDType1Font fontBold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);

            String customer = String.valueOf(invoice.getSnapshotCustomer().getOrDefault("displayName", "Client"));
            try (PDPageContentStream cs = new PDPageContentStream(document, page)) {
                cs.beginText();
                cs.setFont(fontBold, 16);
                cs.newLineAtOffset(50, 750);
                cs.showText("Facture " + invoice.getNumber());
                cs.setFont(font, 12);
                cs.newLineAtOffset(0, -30);
                cs.showText("Client : " + truncate(customer, 80));
                cs.newLineAtOffset(0, -20);
                cs.showText("Date : " + invoice.getIssueDate());
                cs.newLineAtOffset(0, -20);
                cs.showText("Total TTC : " + invoice.getTotalTtc() + " EUR");
                cs.endText();
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.save(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Génération PDF impossible", e);
        }
    }

    private static String truncate(String value, int max) {
        if (value.length() <= max) {
            return value;
        }
        return value.substring(0, max - 1) + "…";
    }
}
