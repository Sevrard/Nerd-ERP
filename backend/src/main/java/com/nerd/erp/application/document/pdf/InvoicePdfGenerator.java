package com.nerd.erp.application.document.pdf;

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
        return renderer.render(toModel(invoice, workspace, skuByProductId));
    }

    PdfDocumentModel toModel(Invoice invoice, Workspace workspace, Map<UUID, String> skuByProductId) {
        List<PdfDocumentModel.Line> lines = new ArrayList<>();
        for (InvoiceLine l : invoice.getLines()) {
            String sku = l.getProductId() == null ? null : skuByProductId.get(l.getProductId());
            lines.add(new PdfDocumentModel.Line(sku, l.getDescription(), null, l.getQuantity(), null,
                    l.getUnitPriceHt(), l.getVatRate(), l.getLineTotalHt()));
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
                invoice.getAmountPaid());

        List<String> terms = new ArrayList<>();
        if (invoice.getDueDate() != null) {
            terms.add("Échéance : " + DATE_FMT.format(invoice.getDueDate()) + ".");
        }
        terms.add("Mode de paiement : virement bancaire.");
        if (invoice.getExternalReference() != null && !invoice.getExternalReference().isBlank()) {
            terms.add("Référence externe : " + invoice.getExternalReference());
        }

        String[] subject = DocumentPdfSupport.subjectAndSummary(invoice.getNotes(), null);
        String kind = invoice.getInvoiceType() == InvoiceType.CREDIT_NOTE ? "AVOIR" : "FACTURE";

        return new PdfDocumentModel(
                kind,
                invoice.getNumber(),
                statusLabel(invoice.getStatus()),
                invoice.getIssueDate(),
                "Échéance",
                invoice.getDueDate(),
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
