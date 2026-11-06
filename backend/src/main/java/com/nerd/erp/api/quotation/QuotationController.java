package com.nerd.erp.api.quotation;

import com.nerd.erp.api.document.DocumentEmailRequest;
import com.nerd.erp.api.document.DocumentEmailResponse;
import com.nerd.erp.api.invoice.InvoiceResponse;
import com.nerd.erp.application.document.DocumentMailService;
import com.nerd.erp.application.invoice.QuotationConversionService;
import com.nerd.erp.application.quotation.QuotationService;
import com.nerd.erp.api.shared.PageResponse;
import com.nerd.erp.infrastructure.security.core.RequireWriteAccess;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/quotations")
public class QuotationController {

    private final QuotationService quotationService;
    private final QuotationConversionService conversionService;
    private final DocumentMailService documentMailService;

    public QuotationController(QuotationService quotationService,
                               QuotationConversionService conversionService,
                               DocumentMailService documentMailService) {
        this.quotationService = quotationService;
        this.conversionService = conversionService;
        this.documentMailService = documentMailService;
    }

    @GetMapping
    public PageResponse<QuotationResponse> list(
            @RequestParam UUID workspaceId,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return quotationService.list(workspaceId, pageable);
    }

    @GetMapping("/{id}")
    public QuotationResponse get(@PathVariable UUID id) {
        return quotationService.get(id);
    }

    @PostMapping
    @RequireWriteAccess
    public QuotationResponse create(@Valid @RequestBody QuotationRequest request) {
        return quotationService.createDraft(request);
    }

    @PutMapping("/{id}")
    @RequireWriteAccess
    public QuotationResponse update(@PathVariable UUID id, @Valid @RequestBody QuotationRequest request) {
        return quotationService.updateDraft(id, request);
    }

    @PostMapping("/{id}/send")
    @RequireWriteAccess
    public QuotationResponse send(@PathVariable UUID id) {
        return quotationService.send(id);
    }

    @PostMapping("/{id}/accept")
    @RequireWriteAccess
    public QuotationResponse accept(@PathVariable UUID id) {
        return quotationService.accept(id);
    }

    /** Refus saisi manuellement (le client a répondu hors ligne). */
    @PostMapping("/{id}/reject")
    @RequireWriteAccess
    public QuotationResponse reject(@PathVariable UUID id, @RequestBody(required = false) RejectQuotationRequest request) {
        return quotationService.reject(id, request == null ? null : request.reason());
    }

    /** Envoi (ou renvoi) au client par e-mail, PDF joint et lien d'acceptation en ligne. */
    @PostMapping("/{id}/email")
    @RequireWriteAccess
    public DocumentEmailResponse email(@PathVariable UUID id,
                                       @Valid @RequestBody(required = false) DocumentEmailRequest request) {
        return documentMailService.sendQuotation(id, request);
    }

    @PostMapping("/{id}/convert-to-invoice")
    @RequireWriteAccess
    public InvoiceResponse convertToInvoice(@PathVariable UUID id) {
        return conversionService.convertToInvoice(id);
    }

    /** Facture d'acompte (devis accepté avec acompte) ; la facture de solde la déduira. */
    @PostMapping("/{id}/deposit-invoice")
    @RequireWriteAccess
    public InvoiceResponse createDepositInvoice(@PathVariable UUID id) {
        return conversionService.createDepositInvoice(id);
    }

    /** Copie en nouveau brouillon. */
    @PostMapping("/{id}/duplicate")
    @ResponseStatus(HttpStatus.CREATED)
    @RequireWriteAccess
    public QuotationResponse duplicate(@PathVariable UUID id) {
        return quotationService.duplicate(id);
    }

    public record RejectQuotationRequest(@Size(max = 1024) String reason) {
    }
}
