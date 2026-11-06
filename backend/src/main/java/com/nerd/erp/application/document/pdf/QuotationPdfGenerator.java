package com.nerd.erp.application.document.pdf;

import com.nerd.erp.application.money.MoneyCalculator;
import com.nerd.erp.domain.customer.Customer;
import com.nerd.erp.domain.quotation.Quotation;
import com.nerd.erp.domain.quotation.QuotationLine;
import com.nerd.erp.domain.quotation.QuotationStatus;
import com.nerd.erp.domain.workspace.Workspace;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Component
public class QuotationPdfGenerator {

    private static final DecimalFormat MONEY;

    static {
        DecimalFormatSymbols symbols = DecimalFormatSymbols.getInstance(Locale.FRANCE);
        symbols.setGroupingSeparator(' ');
        symbols.setDecimalSeparator(',');
        MONEY = new DecimalFormat("#,##0.00", symbols);
    }

    private final PdfDocumentRenderer renderer;

    public QuotationPdfGenerator(PdfDocumentRenderer renderer) {
        this.renderer = renderer;
    }

    /**
     * @param skuByProductId référence catalogue par produit (peut être vide).
     */
    public byte[] generate(Quotation q, Workspace workspace, Customer customer, Map<UUID, String> skuByProductId) {
        return renderer.render(toModel(q, workspace, customer, skuByProductId));
    }

    PdfDocumentModel toModel(Quotation q, Workspace workspace, Customer customer, Map<UUID, String> skuByProductId) {
        List<PdfDocumentModel.Line> lines = new ArrayList<>();
        for (QuotationLine l : q.getLines()) {
            String sku = l.getProductId() == null ? null : skuByProductId.get(l.getProductId());
            lines.add(new PdfDocumentModel.Line(sku, l.getDescription(), null, l.getQuantity(), null,
                    l.getUnitPriceHt(), l.getVatRate(), l.getLineTotalHt()));
        }

        BigDecimal discountAmount = nz(q.getDiscountAmountHt());
        BigDecimal totalHt = nz(q.getTotalHt());
        BigDecimal deposit = MoneyCalculator.percentOf(q.getTotalTtc(), q.getDepositPercent());
        PdfDocumentModel.Totals totals = new PdfDocumentModel.Totals(
                totalHt.add(discountAmount),
                nz(q.getDiscountPercent()),
                discountAmount,
                totalHt,
                nz(q.getTotalVat()),
                nz(q.getTotalTtc()),
                nz(q.getDepositPercent()),
                deposit,
                null);

        List<String> terms = new ArrayList<>();
        if (totals.hasDeposit()) {
            terms.add("Acompte exigé : " + q.getDepositPercent().stripTrailingZeros().toPlainString()
                    + " % à la commande (" + MONEY.format(deposit.setScale(2, RoundingMode.HALF_UP)) + " € TTC).");
            terms.add("Solde : paiement à 30 jours à compter de la livraison / recette finale.");
        } else {
            terms.add("Paiement à 30 jours à compter de la livraison / recette finale.");
        }
        terms.add("Mode de paiement : virement bancaire.");

        String[] subject = DocumentPdfSupport.subjectAndSummary(q.getNotes(), "Prestations et services");

        return new PdfDocumentModel(
                "DEVIS",
                q.getNumber() == null ? "Brouillon" : q.getNumber(),
                statusLabel(q.getStatus()),
                q.getIssueDate(),
                "Validité",
                q.getValidUntil(),
                PdfPartyFactory.issuer(workspace),
                PdfPartyFactory.recipient(customer),
                subject[0],
                subject[1],
                lines,
                totals,
                terms,
                DocumentPdfSupport.bank(workspace),
                true,
                DocumentPdfSupport.footerLines(workspace),
                workspace.getCurrencyCode());
    }

    private static String statusLabel(QuotationStatus status) {
        return switch (status) {
            case DRAFT -> "Brouillon";
            case SENT -> "En attente d'approbation";
            case ACCEPTED -> "Accepté";
            case REJECTED -> "Refusé";
            case EXPIRED -> "Expiré";
            case CONVERTED -> "Converti en facture";
        };
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
