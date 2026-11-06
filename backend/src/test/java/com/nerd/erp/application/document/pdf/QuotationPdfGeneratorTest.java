package com.nerd.erp.application.document.pdf;

import com.nerd.erp.domain.customer.Customer;
import com.nerd.erp.domain.customer.CustomerType;
import com.nerd.erp.domain.quotation.Quotation;
import com.nerd.erp.domain.quotation.QuotationLine;
import com.nerd.erp.domain.quotation.QuotationStatus;
import com.nerd.erp.domain.workspace.Workspace;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuotationPdfGeneratorTest {

    private final QuotationPdfGenerator generator = new QuotationPdfGenerator(new PdfDocumentRenderer());

    @Test
    void rendersSinglePageQuotationWithTotalsAndBankDetails() throws Exception {
        Workspace ws = workspace();
        Customer customer = customer();
        Quotation q = quotation(ws, customer, 3);

        byte[] pdf = generator.generate(q, ws, customer, Map.of());

        try (PDDocument doc = Loader.loadPDF(pdf)) {
            assertEquals(1, doc.getNumberOfPages());
            String text = new PDFTextStripper().getText(doc);
            assertTrue(text.contains("DEVIS"), text);
            assertTrue(text.contains("DEV-2026-0001"), text);
            assertTrue(text.contains("ACME SAS"), text);
            assertTrue(text.contains("SIREN 123456789"), text);
            assertTrue(text.contains("Remise (10 %)"), text);
            assertTrue(text.contains("Acompte"), text);
            assertTrue(text.contains("FR76 1305 1492 0001 2345 6789 012"), text);
            assertTrue(text.contains("Bon pour accord"), text);
            assertTrue(text.contains("Page 1 / 1"), text);
        }
    }

    @Test
    void paginatesLongQuotations() throws Exception {
        Workspace ws = workspace();
        Customer customer = customer();
        Quotation q = quotation(ws, customer, 60);

        byte[] pdf = generator.generate(q, ws, customer, Map.of());

        try (PDDocument doc = Loader.loadPDF(pdf)) {
            assertTrue(doc.getNumberOfPages() >= 2);
            String text = new PDFTextStripper().getText(doc);
            assertTrue(text.contains("Page 1 / " + doc.getNumberOfPages()), text);
            assertTrue(text.contains("TOTAL TTC"), text);
        }
    }

    @Test
    void sanitizesGlyphsOutsideWinAnsi() {
        assertEquals("Cafe ? 12 EUR", PdfDocumentRenderer.sanitize("Cafe \uD83D\uDE00 12\u202FEUR"));
        assertEquals("l'été - œuvre", PdfDocumentRenderer.sanitize("l\u2019été \u2013 œuvre"));
    }

    private static Workspace workspace() {
        Workspace ws = new Workspace();
        ws.setId(UUID.randomUUID());
        ws.setTenantId(UUID.randomUUID());
        ws.setName("Nerd Group");
        ws.setLegalSubtitle("Ingénierie & solutions digitales");
        ws.setAddressLine1("12 rue de la Forge");
        ws.setPostalCode("69001");
        ws.setCity("Lyon");
        ws.setCountryCode("FR");
        ws.setContactEmail("contact@nerd-group.fr");
        ws.setWebsiteUrl("https://nerd-group.fr");
        ws.setSiren("123456789");
        ws.setVatNumber("FR12123456789");
        ws.setIban("FR7613051492000123456789012");
        ws.setBic("NERDFRPPXXX");
        ws.setCurrencyCode("EUR");
        return ws;
    }

    private static Customer customer() {
        Customer c = new Customer();
        c.setId(UUID.randomUUID());
        c.setCustomerType(CustomerType.COMPANY);
        c.setCompanyName("ACME SAS");
        c.setFirstName("Jane");
        c.setLastName("Doe");
        c.setEmail("jane@acme.test");
        c.setAddressLine1("1 avenue des Tests");
        c.setPostalCode("75001");
        c.setCity("Paris");
        c.setCountryCode("FR");
        return c;
    }

    private static Quotation quotation(Workspace ws, Customer customer, int lineCount) {
        Quotation q = new Quotation();
        q.setId(UUID.randomUUID());
        q.setTenantId(ws.getTenantId());
        q.setWorkspaceId(ws.getId());
        q.setCustomerId(customer.getId());
        q.setNumber("DEV-2026-0001");
        q.setStatus(QuotationStatus.SENT);
        q.setIssueDate(LocalDate.of(2026, 11, 6));
        q.setValidUntil(LocalDate.of(2026, 12, 6));
        q.setNotes("Refonte du site vitrine\nLivraison en deux lots avec recette intermédiaire.");
        List<QuotationLine> lines = new ArrayList<>();
        BigDecimal ht = BigDecimal.ZERO;
        for (int i = 0; i < lineCount; i++) {
            QuotationLine l = new QuotationLine();
            l.setTenantId(ws.getTenantId());
            l.setLineOrder(i);
            l.setDescription("Prestation n°" + (i + 1) + " — développement et intégration continue sur environnement cible");
            l.setQuantity(new BigDecimal("2"));
            l.setUnitPriceHt(new BigDecimal("450.0000"));
            l.setVatRate(new BigDecimal("20.00"));
            l.setLineTotalHt(new BigDecimal("900.0000"));
            l.setLineTotalVat(new BigDecimal("180.0000"));
            l.setLineTotalTtc(new BigDecimal("1080.0000"));
            ht = ht.add(l.getLineTotalHt());
            lines.add(l);
        }
        q.replaceLines(lines);
        BigDecimal discount = ht.multiply(new BigDecimal("0.10"));
        BigDecimal netHt = ht.subtract(discount);
        BigDecimal vat = netHt.multiply(new BigDecimal("0.20"));
        q.setDiscountPercent(new BigDecimal("10.00"));
        q.setDiscountAmountHt(discount);
        q.setDepositPercent(new BigDecimal("30.00"));
        q.setTotalHt(netHt);
        q.setTotalVat(vat);
        q.setTotalTtc(netHt.add(vat));
        return q;
    }
}
