package com.nerd.erp.application.document.pdf;

import com.nerd.erp.application.document.DocumentLineBuilder;
import com.nerd.erp.domain.invoice.Invoice;
import com.nerd.erp.domain.invoice.InvoiceLine;
import com.nerd.erp.domain.invoice.InvoiceStatus;
import com.nerd.erp.domain.invoice.InvoiceType;
import com.nerd.erp.domain.workspace.Workspace;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Facture générée à la volée quand aucun PDF source n'est stocké (factures internes / converties). */
@Component
public class InvoicePdfGenerator {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final PdfDocumentRenderer renderer;

    public InvoicePdfGenerator(PdfDocumentRenderer renderer) {
        this.renderer = renderer;
    }

    public byte[] generate(Invoice invoice, Workspace workspace, Map<UUID, String> skuByProductId) {
        return generate(invoice, workspace, skuByProductId, null);
    }

    /** @param creditedInvoiceNumber numéro de la facture d'origine pour un avoir (null sinon). */
    public byte[] generate(Invoice invoice, Workspace workspace, Map<UUID, String> skuByProductId,
                           String creditedInvoiceNumber) {
        return generate(invoice, workspace, skuByProductId, creditedInvoiceNumber, null);
    }

    public byte[] generate(Invoice invoice, Workspace workspace, Map<UUID, String> skuByProductId,
                           String creditedInvoiceNumber, String depositInvoiceNumber) {
        return renderer.render(toModel(invoice, workspace, skuByProductId, creditedInvoiceNumber, depositInvoiceNumber));
    }

    PdfDocumentModel toModel(Invoice invoice, Workspace workspace, Map<UUID, String> skuByProductId) {
        return toModel(invoice, workspace, skuByProductId, null);
    }

    PdfDocumentModel toModel(Invoice invoice, Workspace workspace, Map<UUID, String> skuByProductId,
                             String creditedInvoiceNumber) {
        return toModel(invoice, workspace, skuByProductId, creditedInvoiceNumber, null);
    }

    /**
     * @param creditedInvoiceNumber numéro de la facture d'origine (avoir)
     * @param depositInvoiceNumber  numéro de la facture d'acompte déduite (facture de solde)
     */
    PdfDocumentModel toModel(Invoice invoice, Workspace workspace, Map<UUID, String> skuByProductId,
                             String creditedInvoiceNumber, String depositInvoiceNumber) {
        List<PdfDocumentModel.Line> lines = new ArrayList<>();
        for (InvoiceLine l : invoice.getLines()) {
            String sku = l.getProductId() == null ? null : skuByProductId.get(l.getProductId());
            lines.add(new PdfDocumentModel.Line(sku, l.getDescription(), null, l.getQuantity(), null,
                    l.getUnitPriceHt(), l.getVatRate(), l.getLineTotalHt(), l.getDiscountPercent()));
        }

        BigDecimal discountAmount = nz(invoice.getDiscountAmountHt());
        BigDecimal totalHt = nz(invoice.getTotalHt());
        PdfDocumentModel.Totals totals = new PdfDocumentModel.Totals(
                totalHt.add(discountAmount),
                nz(invoice.getDiscountPercent()),
                discountAmount,
                totalHt,
                nz(invoice.getTotalVat()),
                nz(invoice.getTotalTtc()),
                null,
                null,
                invoice.getAmountPaid(),
                DocumentPdfSupport.vatLines(
                        DocumentLineBuilder.vatBreakdownOfInvoice(invoice.getLines(), invoice.getDiscountPercent())));

        boolean creditNote = invoice.getInvoiceType() == InvoiceType.CREDIT_NOTE;
        boolean deposit = invoice.getInvoiceType() == InvoiceType.DEPOSIT;
        List<String> terms = new ArrayList<>();
        if (creditNote) {
            if (creditedInvoiceNumber != null) {
                terms.add("Avoir sur la facture n° " + creditedInvoiceNumber + ".");
            }
            if (invoice.getCreditReason() != null && !invoice.getCreditReason().isBlank()) {
                terms.add("Motif : " + invoice.getCreditReason().trim());
            }
            terms.add("Montant à déduire de votre prochain règlement ou remboursé par virement.");
        } else {
            if (deposit) {
                terms.add("Facture d'acompte : payable à réception. Le solde fera l'objet d'une facture définitive "
                        + "sur laquelle cet acompte sera déduit.");
            } else if (depositInvoiceNumber != null) {
                terms.add("Facture de solde : l'acompte déjà facturé (facture n° " + depositInvoiceNumber
                        + ") est déduit ci-dessus.");
            }
            if (invoice.getDueDate() != null) {
                terms.add("Échéance : " + DATE_FMT.format(invoice.getDueDate()) + ".");
            }
            if (workspace.getPaymentTermsText() != null && !workspace.getPaymentTermsText().isBlank()) {
                terms.add(workspace.getPaymentTermsText().trim());
            }
            terms.add("Mode de paiement : virement bancaire.");
        }
        if (invoice.getExternalReference() != null && !invoice.getExternalReference().isBlank()) {
            terms.add("Référence externe : " + invoice.getExternalReference());
        }

        String[] subject = DocumentPdfSupport.subjectAndSummary(invoice.getNotes(), null);
        String kind = creditNote ? "AVOIR" : deposit ? "FACTURE D'ACOMPTE" : "FACTURE";

        return new PdfDocumentModel(
                kind,
                invoice.getNumber(),
                statusLabel(invoice.getStatus()),
                invoice.getIssueDate(),
                creditNote ? null : "Échéance",
                creditNote ? null : invoice.getDueDate(),
                PdfPartyFactory.issuer(workspace),
                PdfPartyFactory.recipient(invoice.getSnapshotCustomer()),
                subject[0],
                subject[1],
                lines,
                totals,
                terms,
                DocumentPdfSupport.bank(workspace),
                false,
                DocumentPdfSupport.footerLines(workspace),
                workspace.getCurrencyCode());
    }

    private static String statusLabel(InvoiceStatus status) {
        return switch (status) {
            case DRAFT -> "Brouillon";
            case ISSUED -> "Émise";
            case PARTIALLY_PAID -> "Partiellement payée";
            case PAID -> "Payée";
            case CANCELLED -> "Annulée";
        };
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
