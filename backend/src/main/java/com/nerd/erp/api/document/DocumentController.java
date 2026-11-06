package com.nerd.erp.api.document;

import com.nerd.erp.application.document.GeneratedDocument;
import com.nerd.erp.application.document.InvoicePdfService;
import com.nerd.erp.application.document.QuotationPdfService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/documents")
public class DocumentController {

    private final InvoicePdfService invoicePdfService;
    private final QuotationPdfService quotationPdfService;

    public DocumentController(InvoicePdfService invoicePdfService, QuotationPdfService quotationPdfService) {
        this.invoicePdfService = invoicePdfService;
        this.quotationPdfService = quotationPdfService;
    }

    @GetMapping("/invoices/{id}/download")
    public ResponseEntity<byte[]> downloadInvoice(@PathVariable UUID id) {
        return pdf(invoicePdfService.downloadInvoiceDocument(id));
    }

    @GetMapping("/quotations/{id}/download")
    public ResponseEntity<byte[]> downloadQuotation(@PathVariable UUID id) {
        return pdf(quotationPdfService.downloadQuotationDocument(id));
    }

    private static ResponseEntity<byte[]> pdf(GeneratedDocument doc) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + doc.filename() + "\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(doc.content());
    }
}
