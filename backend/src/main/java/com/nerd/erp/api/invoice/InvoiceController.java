package com.nerd.erp.api.invoice;

import com.nerd.erp.api.document.DocumentEmailRequest;
import com.nerd.erp.api.document.DocumentEmailResponse;
import com.nerd.erp.api.shared.PageResponse;
import com.nerd.erp.application.document.DocumentMailService;
import com.nerd.erp.application.invoice.CreditNoteService;
import com.nerd.erp.application.invoice.InvoiceExportService;
import com.nerd.erp.application.invoice.InvoiceService;
import com.nerd.erp.infrastructure.security.core.RequireWriteAccess;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/invoices")
public class InvoiceController {

    private final InvoiceService invoiceService;
    private final CreditNoteService creditNoteService;
    private final DocumentMailService documentMailService;
    private final InvoiceExportService exportService;

    public InvoiceController(InvoiceService invoiceService,
                             CreditNoteService creditNoteService,
                             DocumentMailService documentMailService,
                             InvoiceExportService exportService) {
        this.invoiceService = invoiceService;
        this.creditNoteService = creditNoteService;
        this.documentMailService = documentMailService;
        this.exportService = exportService;
    }

    @GetMapping
    public PageResponse<InvoiceResponse> list(
            @RequestParam UUID workspaceId,
            @RequestParam(required = false) UUID customerId,
            @RequestParam(defaultValue = "false") boolean openOnly,
            @RequestParam(required = false) String q,
            @PageableDefault(size = 20, sort = "issueDate", direction = Sort.Direction.DESC) Pageable pageable) {
        return invoiceService.list(workspaceId, customerId, openOnly, q, pageable);
    }

    /** Export comptable CSV (séparateur « ; », UTF-8 avec BOM pour Excel) des documents émis sur la période. */
    @GetMapping(value = "/export", produces = "text/csv")
    public ResponseEntity<byte[]> export(
            @RequestParam UUID workspaceId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        InvoiceExportService.CsvExport export = exportService.exportCsv(workspaceId, from, to);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + export.filename() + "\"")
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(export.content());
    }

    @GetMapping("/{id}")
    public InvoiceResponse get(@PathVariable UUID id) {
        return invoiceService.get(id);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @RequireWriteAccess
    public void delete(@PathVariable UUID id) {
        invoiceService.delete(id);
    }

    /** Avoir (total par défaut, partiel si des lignes sont fournies) sur une facture émise. */
    @PostMapping("/{id}/credit-notes")
    @ResponseStatus(HttpStatus.CREATED)
    @RequireWriteAccess
    public InvoiceResponse createCreditNote(@PathVariable UUID id, @Valid @RequestBody CreditNoteRequest request) {
        return creditNoteService.create(id, request);
    }

    @PostMapping("/{id}/email")
    @RequireWriteAccess
    public DocumentEmailResponse email(@PathVariable UUID id,
                                       @Valid @RequestBody(required = false) DocumentEmailRequest request) {
        return documentMailService.sendInvoice(id, request);
    }

    @PostMapping("/{id}/reminders")
    @RequireWriteAccess
    public DocumentEmailResponse remind(@PathVariable UUID id,
                                        @Valid @RequestBody(required = false) DocumentEmailRequest request) {
        return documentMailService.sendReminder(id, request);
    }
}
