package com.nerd.erp.application.document.pdf;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.font.encoding.GlyphList;
import org.apache.pdfbox.pdmodel.font.encoding.WinAnsiEncoding;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Rendu PDF A4 (PDFBox, polices standard) d'un {@link PdfDocumentModel}.
 * Mise en page alignée sur l'aperçu « Papier / PDF » du frontend : en-tête émetteur + cartouche
 * document, blocs émetteur / client, objet, tableau des lignes (pagination automatique),
 * conditions de règlement + coordonnées bancaires, totaux (remise, acompte), bon pour accord,
 * pied de page légal numéroté.
 */
@Component
public class PdfDocumentRenderer {

    private static final float PAGE_W = PDRectangle.A4.getWidth();
    private static final float PAGE_H = PDRectangle.A4.getHeight();
    private static final float MARGIN = 40f;
    private static final float CONTENT_W = PAGE_W - 2 * MARGIN;
    private static final float FOOTER_H = 48f;

    private static final float[] INK = rgb(15, 23, 42);
    private static final float[] MUTED = rgb(71, 85, 105);
    private static final float[] ACCENT = rgb(14, 116, 144);
    private static final float[] PANEL = rgb(241, 245, 249);
    private static final float[] PANEL_BORDER = rgb(203, 213, 225);
    private static final float[] TABLE_HEAD = rgb(226, 232, 240);
    private static final float[] ZEBRA = rgb(248, 250, 252);
    private static final float[] SUCCESS = rgb(4, 120, 87);
    private static final float[] WHITE = rgb(255, 255, 255);

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    public byte[] render(PdfDocumentModel model) {
        try (PDDocument doc = new PDDocument()) {
            Canvas c = new Canvas(doc, model);
            c.drawHeader();
            c.drawParties();
            c.drawSubject();
            c.drawTable();
            c.drawTotalsAndTerms();
            if (model.signatureBlock()) {
                c.drawSignatureBlock();
            }
            c.finish();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.save(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("Génération PDF impossible", e);
        }
    }

    // ---------------------------------------------------------------------------------------------

    private static final class Canvas {
        private final PDDocument doc;
        private final PdfDocumentModel model;
        private final PDFont regular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
        private final PDFont bold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
        private final PDFont mono = new PDType1Font(Standard14Fonts.FontName.COURIER);
        private final DecimalFormat money;
        private final DecimalFormat qty;
        private final List<PDPage> pages = new ArrayList<>();
        private PDPageContentStream cs;
        private float y;

        Canvas(PDDocument doc, PdfDocumentModel model) throws IOException {
            this.doc = doc;
            this.model = model;
            DecimalFormatSymbols symbols = DecimalFormatSymbols.getInstance(Locale.FRANCE);
            symbols.setGroupingSeparator(' ');
            symbols.setDecimalSeparator(',');
            this.money = new DecimalFormat("#,##0.00", symbols);
            this.qty = new DecimalFormat("#,##0.##", symbols);
            newPage();
        }

        // ----- pages -----------------------------------------------------------------------------

        private void newPage() throws IOException {
            if (cs != null) {
                cs.close();
            }
            PDPage page = new PDPage(PDRectangle.A4);
            doc.addPage(page);
            pages.add(page);
            cs = new PDPageContentStream(doc, page);
            y = PAGE_H - MARGIN;
            if (pages.size() > 1) {
                drawContinuationHeader();
            }
        }

        private void ensureSpace(float needed) throws IOException {
            if (y - needed < MARGIN + FOOTER_H) {
                newPage();
            }
        }

        private void drawContinuationHeader() throws IOException {
            text(bold, 10, INK, MARGIN, y - 10, model.issuer().title());
            String ref = model.kind() + " " + model.number();
            textRight(regular, 9, MUTED, PAGE_W - MARGIN, y - 10, ref);
            y -= 22;
            hr(MARGIN, y, PAGE_W - MARGIN, PANEL_BORDER, 0.6f);
            y -= 14;
        }

        void finish() throws IOException {
            cs.close();
            cs = null;
            int total = pages.size();
            for (int i = 0; i < total; i++) {
                try (PDPageContentStream footer = new PDPageContentStream(doc, pages.get(i),
                        PDPageContentStream.AppendMode.APPEND, true, true)) {
                    float fy = MARGIN + FOOTER_H - 10;
                    line(footer, MARGIN, fy + 6, PAGE_W - MARGIN, fy + 6, PANEL_BORDER, 0.6f);
                    float ly = fy - 6;
                    for (String lineText : model.footerLines()) {
                        for (String wrapped : wrap(lineText, regular, 7, CONTENT_W - 60)) {
                            textAt(footer, regular, 7, MUTED, MARGIN, ly, wrapped);
                            ly -= 9;
                        }
                    }
                    String pageLabel = "Page " + (i + 1) + " / " + total;
                    textRightAt(footer, regular, 7, MUTED, PAGE_W - MARGIN, fy - 6, pageLabel);
                }
            }
        }

        // ----- sections --------------------------------------------------------------------------

        void drawHeader() throws IOException {
            float top = y;
            float leftW = CONTENT_W * 0.58f;

            // Logo texte « NERD GR </> UP » si l'émetteur est Nerd Group, sinon raison sociale.
            String title = model.issuer().title();
            text(bold, 20, INK, MARGIN, top - 18, title.toUpperCase(Locale.ROOT));
            float ly = top - 34;
            for (String l : model.issuer().lines()) {
                for (String w : wrap(l, regular, 8.5f, leftW - 10)) {
                    text(regular, 8.5f, MUTED, MARGIN, ly, w);
                    ly -= 11;
                }
            }

            // Cartouche document à droite
            float boxW = CONTENT_W - leftW;
            float boxX = MARGIN + leftW;
            float boxH = 86;
            panel(boxX, top - boxH, boxW, boxH);
            text(bold, model.kind().length() > 10 ? 12 : 16, ACCENT, boxX + 12, top - 22, model.kind());
            textRight(bold, 8, ACCENT, boxX + boxW - 12, top - 20, model.statusLabel().toUpperCase(Locale.ROOT));
            float ky = top - 42;
            keyValue(boxX + 12, boxX + boxW - 12, ky, "N° " + model.kind().toLowerCase(Locale.ROOT) + " :",
                    model.number(), true);
            ky -= 13;
            keyValue(boxX + 12, boxX + boxW - 12, ky, "Date d'émission :", date(model.issueDate()), false);
            ky -= 13;
            if (model.secondaryDateLabel() != null) {
                keyValue(boxX + 12, boxX + boxW - 12, ky, model.secondaryDateLabel() + " :",
                        date(model.secondaryDate()), false);
            }

            y = Math.min(ly, top - boxH) - 14;
            hr(MARGIN, y, PAGE_W - MARGIN, PANEL_BORDER, 0.8f);
            y -= 16;
        }

        void drawParties() throws IOException {
            float gap = 12;
            float colW = (CONTENT_W - gap) / 2;
            float leftH = partyHeight(model.issuer(), colW);
            float rightH = partyHeight(model.recipient(), colW);
            float h = Math.max(leftH, rightH);
            ensureSpace(h + 16);
            drawParty(MARGIN, y, colW, h, "PRESTATAIRE / ÉMETTEUR", model.issuer());
            drawParty(MARGIN + colW + gap, y, colW, h, "DESTINATAIRE / CLIENT", model.recipient());
            y -= h + 16;
        }

        private float partyHeight(PdfDocumentModel.Party p, float w) throws IOException {
            float h = 10 + 12 + 14; // padding + label + titre
            for (String l : p.lines()) {
                h += 11 * wrap(l, regular, 8.5f, w - 20).size();
            }
            return h + 10;
        }

        private void drawParty(float x, float top, float w, float h, String label, PdfDocumentModel.Party p)
                throws IOException {
            panel(x, top - h, w, h);
            text(bold, 7, ACCENT, x + 10, top - 14, label);
            text(bold, 10.5f, INK, x + 10, top - 28, p.title());
            float ly = top - 41;
            for (String l : p.lines()) {
                for (String wline : wrap(l, regular, 8.5f, w - 20)) {
                    text(regular, 8.5f, MUTED, x + 10, ly, wline);
                    ly -= 11;
                }
            }
        }

        void drawSubject() throws IOException {
            if (isBlank(model.subject()) && isBlank(model.summary())) {
                return;
            }
            List<String> summaryLines = isBlank(model.summary())
                    ? List.of()
                    : wrap(model.summary(), regular, 8.5f, CONTENT_W - 20);
            float h = 10 + 12 + 11 * summaryLines.size() + 8;
            ensureSpace(h + 14);
            panel(MARGIN, y - h, CONTENT_W, h);
            float ly = y - 15;
            text(bold, 7, ACCENT, MARGIN + 10, ly, "OBJET :");
            if (!isBlank(model.subject())) {
                text(bold, 9, INK, MARGIN + 52, ly, truncate(model.subject(), regular, 9, CONTENT_W - 70));
            }
            ly -= 13;
            for (String l : summaryLines) {
                text(regular, 8.5f, MUTED, MARGIN + 10, ly, l);
                ly -= 11;
            }
            y -= h + 14;
        }

        // colonnes : SKU | désignation | qté | PU HT | TVA | total HT
        private static final float[] COL_W = {62, 0, 48, 72, 40, 78};

        void drawTable() throws IOException {
            float[] cols = COL_W.clone();
            float fixed = 0;
            for (int i = 0; i < cols.length; i++) {
                if (i != 1) {
                    fixed += cols[i];
                }
            }
            cols[1] = CONTENT_W - fixed;
            float[] x = new float[cols.length + 1];
            x[0] = MARGIN;
            for (int i = 0; i < cols.length; i++) {
                x[i + 1] = x[i] + cols[i];
            }

            drawTableHead(x);
            boolean zebra = false;
            if (model.lines().isEmpty()) {
                ensureSpace(24);
                text(regular, 8.5f, MUTED, MARGIN + 8, y - 14, "Aucune ligne.");
                y -= 24;
            }
            for (PdfDocumentModel.Line line : model.lines()) {
                List<String> label = wrap(nvl(line.label()), bold, 8.5f, cols[1] - 12);
                List<String> detail = isBlank(line.detail()) ? List.of() : wrap(line.detail(), regular, 7.5f, cols[1] - 12);
                List<String> sku = wrap(nvl(line.sku(), "-"), mono, 7, cols[0] - 10);
                float rowH = 8 + 11 * label.size() + 9.5f * detail.size() + 6;
                rowH = Math.max(rowH, 8 + 10 * sku.size() + 6);
                rowH = Math.max(rowH, line.hasDiscount() ? 30 : 26);
                if (y - rowH < MARGIN + FOOTER_H) {
                    newPage();
                    drawTableHead(x);
                }
                if (zebra) {
                    fillRect(MARGIN, y - rowH, CONTENT_W, rowH, ZEBRA);
                }
                zebra = !zebra;
                float ty = y - 14;
                float sy = ty;
                for (String s : sku) {
                    text(mono, 7, MUTED, x[0] + 6, sy, s);
                    sy -= 10;
                }
                float ly = ty;
                for (String l : label) {
                    text(bold, 8.5f, INK, x[1] + 6, ly, l);
                    ly -= 11;
                }
                for (String d : detail) {
                    text(regular, 7.5f, MUTED, x[1] + 6, ly, d);
                    ly -= 9.5f;
                }
                String q = qty.format(nz(line.quantity()));
                textCenter(bold, 8.5f, INK, (x[2] + x[3]) / 2, ty, q);
                if (!isBlank(line.unit())) {
                    textCenter(regular, 6.5f, MUTED, (x[2] + x[3]) / 2, ty - 9, line.unit());
                }
                textRight(regular, 8.5f, INK, x[4] - 6, ty, money(line.unitPriceHt()));
                if (line.hasDiscount()) {
                    textRight(regular, 6.5f, MUTED, x[4] - 6, ty - 9, "remise -" + qty.format(line.discountPercent()) + " %");
                }
                textCenter(regular, 8, MUTED, (x[4] + x[5]) / 2, ty, qty.format(nz(line.vatRate())) + " %");
                textRight(bold, 8.5f, INK, x[6] - 6, ty, money(line.lineTotalHt()));
                y -= rowH;
                hr(MARGIN, y, PAGE_W - MARGIN, PANEL_BORDER, 0.4f);
            }
            y -= 16;
        }

        private void drawTableHead(float[] x) throws IOException {
            ensureSpace(40);
            float h = 20;
            fillRect(MARGIN, y - h, CONTENT_W, h, TABLE_HEAD);
            float ty = y - 13;
            text(bold, 7, INK, x[0] + 6, ty, "RÉF / SKU");
            text(bold, 7, INK, x[1] + 6, ty, "PRESTATION & DESCRIPTION");
            textCenter(bold, 7, INK, (x[2] + x[3]) / 2, ty, "QTÉ");
            textRight(bold, 7, INK, x[4] - 6, ty, "P.U. HT");
            textCenter(bold, 7, INK, (x[4] + x[5]) / 2, ty, "TVA");
            textRight(bold, 7, INK, x[6] - 6, ty, "TOTAL HT");
            y -= h;
        }

        void drawTotalsAndTerms() throws IOException {
            float gap = 12;
            float leftW = CONTENT_W * 0.56f;
            float rightW = CONTENT_W - leftW - gap;

            // --- hauteur des deux blocs ---
            List<String[]> rows = totalsRows();
            float rightH = 14 + rows.size() * 14 + 10;

            List<String> terms = new ArrayList<>();
            for (String t : model.paymentTerms()) {
                terms.addAll(wrap("• " + t, regular, 8, leftW - 20));
            }
            float leftH = 10 + 12 + 11 * terms.size() + 8;
            PdfDocumentModel.BankDetails bank = model.bank();
            boolean hasBank = bank != null;
            if (hasBank) {
                leftH += 8 + 11 * (bank.isEmpty() ? 2 : (1 + (isBlank(bank.iban()) ? 0 : 1) + (isBlank(bank.bic()) ? 0 : 1)));
            }
            float h = Math.max(leftH, rightH);
            ensureSpace(h + 16);

            // --- conditions (gauche) ---
            float lx = MARGIN;
            panel(lx, y - h, leftW, h);
            float ly = y - 15;
            text(bold, 7, ACCENT, lx + 10, ly, "CONDITIONS DE RÈGLEMENT");
            ly -= 13;
            for (String t : terms) {
                text(regular, 8, MUTED, lx + 10, ly, t);
                ly -= 11;
            }
            if (hasBank) {
                ly -= 4;
                hr(lx + 10, ly + 4, lx + leftW - 10, PANEL_BORDER, 0.5f);
                ly -= 6;
                text(bold, 8, INK, lx + 10, ly, "Titulaire : " + nvl(bank.holder(), model.issuer().title()));
                ly -= 11;
                if (bank.isEmpty()) {
                    text(regular, 7.5f, MUTED, lx + 10, ly,
                            "IBAN / BIC : à renseigner dans les paramètres du workspace.");
                } else {
                    if (!isBlank(bank.iban())) {
                        text(mono, 8, INK, lx + 10, ly, "IBAN : " + groupIban(bank.iban()));
                        ly -= 11;
                    }
                    if (!isBlank(bank.bic())) {
                        text(mono, 8, INK, lx + 10, ly, "BIC  : " + bank.bic());
                    }
                }
            }

            // --- totaux (droite) ---
            float rx = MARGIN + leftW + gap;
            panel(rx, y - h, rightW, h);
            float ry = y - 16;
            for (String[] row : rows) {
                boolean emphasis = row.length > 2 && "TTC".equals(row[2]);
                boolean success = row.length > 2 && "OK".equals(row[2]);
                PDFont f = emphasis ? bold : regular;
                float size = emphasis ? 10.5f : 8.5f;
                if (!emphasis && row[0].startsWith("TVA ") && row[0].contains("(base")) {
                    size = 7.5f;
                }
                float[] color = emphasis ? INK : success ? SUCCESS : MUTED;
                if (emphasis) {
                    hr(rx + 10, ry + 9, rx + rightW - 10, PANEL_BORDER, 0.6f);
                }
                text(f, size, color, rx + 10, ry, row[0]);
                textRight(emphasis ? bold : mono, size, emphasis ? INK : success ? SUCCESS : INK,
                        rx + rightW - 10, ry, row[1]);
                ry -= 14;
            }
            y -= h + 16;
        }

        private List<String[]> totalsRows() {
            PdfDocumentModel.Totals t = model.totals();
            List<String[]> rows = new ArrayList<>();
            if (t.hasDiscount()) {
                rows.add(new String[]{"Sous-total HT :", money(t.subtotalHt())});
                rows.add(new String[]{"Remise (" + qty.format(nz(t.discountPercent())) + " %) :",
                        "-" + money(t.discountAmountHt())});
            }
            rows.add(new String[]{"Total HT :", money(t.totalHt())});
            if (t.vatLines() == null || t.vatLines().isEmpty()) {
                rows.add(new String[]{"TVA :", money(t.totalVat())});
            } else {
                for (PdfDocumentModel.VatLine v : t.vatLines()) {
                    rows.add(new String[]{"TVA " + qty.format(nz(v.rate())) + " % (base " + money(v.baseHt()) + ") :",
                            money(v.vatAmount())});
                }
            }
            rows.add(new String[]{"TOTAL TTC :", money(t.totalTtc()), "TTC"});
            if (t.hasDeposit()) {
                rows.add(new String[]{"Acompte à la commande (" + qty.format(t.depositPercent()) + " %) :",
                        money(t.depositAmountTtc()), "OK"});
                rows.add(new String[]{"Solde restant à payer :",
                        money(nz(t.totalTtc()).subtract(nz(t.depositAmountTtc())))});
            }
            if (t.hasAmountPaid()) {
                rows.add(new String[]{"Déjà réglé :", money(t.amountPaid()), "OK"});
                rows.add(new String[]{"Reste à payer :", money(nz(t.totalTtc()).subtract(nz(t.amountPaid())))});
            }
            return rows;
        }

        void drawSignatureBlock() throws IOException {
            float h = 84;
            ensureSpace(h + 4);
            panel(MARGIN, y - h, CONTENT_W, h);
            float textW = CONTENT_W * 0.55f;
            text(bold, 9.5f, INK, MARGIN + 12, y - 18, "Bon pour accord & validation");
            float ly = y - 31;
            for (String l : wrap("En signant ce devis, le client reconnaît avoir pris connaissance et accepté "
                    + "l'intégralité des prestations et des conditions de règlement ci-dessus.", regular, 8, textW - 20)) {
                text(regular, 8, MUTED, MARGIN + 12, ly, l);
                ly -= 10.5f;
            }
            ly -= 6;
            text(regular, 8, MUTED, MARGIN + 12, ly, "Date d'approbation : ____ / ____ / ________");

            float sx = MARGIN + textW + 10;
            float sw = CONTENT_W - textW - 22;
            text(regular, 8, MUTED, sx, y - 18, "Signature du client (précédée de « Bon pour accord ») :");
            dashedRect(sx, y - h + 8, sw, h - 34);
            y -= h + 8;
        }

        // ----- primitives ------------------------------------------------------------------------

        private void keyValue(float x1, float x2, float ky, String key, String value, boolean accent)
                throws IOException {
            text(regular, 8, MUTED, x1, ky, key);
            textRight(bold, 8, accent ? ACCENT : INK, x2, ky, nvl(value, "-"));
        }

        private void panel(float x, float yBottom, float w, float h) throws IOException {
            fillRect(x, yBottom, w, h, PANEL);
            cs.setStrokingColor(PANEL_BORDER[0], PANEL_BORDER[1], PANEL_BORDER[2]);
            cs.setLineWidth(0.6f);
            cs.addRect(x, yBottom, w, h);
            cs.stroke();
        }

        private void dashedRect(float x, float yBottom, float w, float h) throws IOException {
            cs.setStrokingColor(MUTED[0], MUTED[1], MUTED[2]);
            cs.setLineWidth(0.8f);
            cs.setLineDashPattern(new float[]{3, 3}, 0);
            cs.addRect(x, yBottom, w, h);
            cs.stroke();
            cs.setLineDashPattern(new float[]{}, 0);
        }

        private void fillRect(float x, float yBottom, float w, float h, float[] color) throws IOException {
            cs.setNonStrokingColor(color[0], color[1], color[2]);
            cs.addRect(x, yBottom, w, h);
            cs.fill();
        }

        private void hr(float x1, float yy, float x2, float[] color, float width) throws IOException {
            line(cs, x1, yy, x2, yy, color, width);
        }

        private static void line(PDPageContentStream s, float x1, float y1, float x2, float y2, float[] color,
                                 float width) throws IOException {
            s.setStrokingColor(color[0], color[1], color[2]);
            s.setLineWidth(width);
            s.moveTo(x1, y1);
            s.lineTo(x2, y2);
            s.stroke();
        }

        private void text(PDFont font, float size, float[] color, float x, float yy, String value)
                throws IOException {
            textAt(cs, font, size, color, x, yy, value);
        }

        private void textRight(PDFont font, float size, float[] color, float xRight, float yy, String value)
                throws IOException {
            textRightAt(cs, font, size, color, xRight, yy, value);
        }

        private void textCenter(PDFont font, float size, float[] color, float xCenter, float yy, String value)
                throws IOException {
            String s = sanitize(value);
            float w = width(font, size, s);
            textAt(cs, font, size, color, xCenter - w / 2, yy, s);
        }

        private static void textRightAt(PDPageContentStream s, PDFont font, float size, float[] color,
                                        float xRight, float yy, String value) throws IOException {
            String t = sanitize(value);
            float w = width(font, size, t);
            textAt(s, font, size, color, xRight - w, yy, t);
        }

        private static void textAt(PDPageContentStream s, PDFont font, float size, float[] color, float x,
                                   float yy, String value) throws IOException {
            if (value == null || value.isEmpty()) {
                return;
            }
            s.setNonStrokingColor(color[0], color[1], color[2]);
            s.beginText();
            s.setFont(font, size);
            s.newLineAtOffset(x, yy);
            s.showText(sanitize(value));
            s.endText();
        }

        private static float width(PDFont font, float size, String text) throws IOException {
            return font.getStringWidth(sanitize(text)) / 1000f * size;
        }

        private static List<String> wrap(String text, PDFont font, float size, float maxW) throws IOException {
            List<String> out = new ArrayList<>();
            if (text == null) {
                return out;
            }
            for (String paragraph : sanitize(text).split("\\r?\\n")) {
                StringBuilder current = new StringBuilder();
                for (String word : paragraph.trim().split("\\s+")) {
                    if (word.isEmpty()) {
                        continue;
                    }
                    String candidate = current.isEmpty() ? word : current + " " + word;
                    if (width(font, size, candidate) <= maxW) {
                        current.setLength(0);
                        current.append(candidate);
                    } else {
                        if (!current.isEmpty()) {
                            out.add(current.toString());
                        }
                        // mot plus long que la colonne : coupe brute
                        while (width(font, size, word) > maxW && word.length() > 1) {
                            int cut = word.length() - 1;
                            while (cut > 1 && width(font, size, word.substring(0, cut)) > maxW) {
                                cut--;
                            }
                            out.add(word.substring(0, cut));
                            word = word.substring(cut);
                        }
                        current.setLength(0);
                        current.append(word);
                    }
                }
                if (!current.isEmpty() || out.isEmpty()) {
                    out.add(current.toString());
                }
            }
            return out;
        }

        private String truncate(String text, PDFont font, float size, float maxW) throws IOException {
            String s = sanitize(text);
            if (width(font, size, s) <= maxW) {
                return s;
            }
            while (s.length() > 1 && width(font, size, s + "...") > maxW) {
                s = s.substring(0, s.length() - 1);
            }
            return s + "...";
        }

        private String money(BigDecimal value) {
            String cur = "EUR".equalsIgnoreCase(nvl(model.currencyCode(), "EUR")) ? "€" : model.currencyCode();
            return money.format(nz(value).setScale(2, RoundingMode.HALF_UP)) + " " + cur;
        }

        private static String date(LocalDate d) {
            return d == null ? "-" : DATE_FMT.format(d);
        }

        private static String groupIban(String iban) {
            String compact = iban.replace(" ", "");
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < compact.length(); i += 4) {
                if (i > 0) {
                    sb.append(' ');
                }
                sb.append(compact, i, Math.min(compact.length(), i + 4));
            }
            return sb.toString();
        }
    }

    // ----- helpers statiques ---------------------------------------------------------------------

    /** Les polices standard 14 n'encodent que WinAnsi : remplace les glyphes inconnus. */
    static String sanitize(String value) {
        if (value == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(value.length());
        value.codePoints().forEach(cp -> {
            switch (cp) {
                case '\u202F', '\u00A0', '\u2009' -> sb.append(' ');
                case '\u2019' -> sb.append('\'');
                case '\u2013', '\u2014' -> sb.append('-');
                default -> {
                    if (cp == '\n' || cp == '\r' || cp == '\t') {
                        sb.append((char) cp);
                        return;
                    }
                    String name = GlyphList.getAdobeGlyphList().codePointToName(cp);
                    if (name != null && WinAnsiEncoding.INSTANCE.contains(name)) {
                        sb.appendCodePoint(cp);
                    } else {
                        sb.append('?');
                    }
                }
            }
        });
        return sb.toString();
    }

    private static float[] rgb(int r, int g, int b) {
        return new float[]{r / 255f, g / 255f, b / 255f};
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private static String nvl(String v) {
        return v == null ? "" : v;
    }

    private static String nvl(String v, String fallback) {
        return v == null || v.isBlank() ? fallback : v;
    }

    private static boolean isBlank(String v) {
        return v == null || v.isBlank();
    }
}
