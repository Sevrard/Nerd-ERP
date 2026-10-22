package com.nerd.erp.application.document;

import com.nerd.erp.api.error.ApiException;
import com.nerd.erp.domain.invoice.Invoice;
import com.nerd.erp.domain.invoice.InvoiceStatus;
import com.nerd.erp.application.invoice.InvoiceService;
import com.nerd.erp.infrastructure.storage.InvoiceDocumentStorage;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class InvoicePdfService {

    private final InvoiceService invoiceService;
    private final InvoiceDocumentStorage documentStorage;
    private final SimpleInvoicePdfGenerator pdfGenerator;

    public InvoicePdfService(InvoiceService invoiceService,
                             InvoiceDocumentStorage documentStorage,
                             SimpleInvoicePdfGenerator pdfGenerator) {
        this.invoiceService = invoiceService;
        this.documentStorage = documentStorage;
        this.pdfGenerator = pdfGenerator;
    }

    public InvoiceDocument downloadInvoiceDocument(UUID invoiceId) {
        Invoice invoice = invoiceService.requireInvoice(invoiceId);
        if (invoice.getStatus() == InvoiceStatus.DRAFT && invoice.getIssuedAt() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Facture non émise");
        }
        byte[] stored = documentStorage.readOrNull(invoice.getSourceDocumentPath());
        if (stored != null && stored.length > 0) {
            return new InvoiceDocument(stored, "facture-" + sanitizeFilename(invoice.getNumber()) + ".pdf");
        }
        byte[] generated = pdfGenerator.generate(invoice);
        return new InvoiceDocument(generated, "facture-" + sanitizeFilename(invoice.getNumber()) + ".pdf");
    }

    private static String sanitizeFilename(String number) {
        return number.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    public record InvoiceDocument(byte[] content, String filename) {
    }
}
