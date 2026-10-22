package com.nerd.erp.api.quotation;

import com.nerd.erp.api.invoice.InvoiceResponse;
import com.nerd.erp.application.invoice.QuotationConversionService;
import com.nerd.erp.api.quotation.QuotationRequest;
import com.nerd.erp.api.quotation.QuotationResponse;
import com.nerd.erp.application.quotation.QuotationService;
import com.nerd.erp.api.shared.PageResponse;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
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

    public QuotationController(QuotationService quotationService, QuotationConversionService conversionService) {
        this.quotationService = quotationService;
        this.conversionService = conversionService;
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
    public QuotationResponse create(@Valid @RequestBody QuotationRequest request) {
        return quotationService.createDraft(request);
    }

    @PutMapping("/{id}")
    public QuotationResponse update(@PathVariable UUID id, @Valid @RequestBody QuotationRequest request) {
        return quotationService.updateDraft(id, request);
    }

    @PostMapping("/{id}/send")
    public QuotationResponse send(@PathVariable UUID id) {
        return quotationService.send(id);
    }

    @PostMapping("/{id}/accept")
    public QuotationResponse accept(@PathVariable UUID id) {
        return quotationService.accept(id);
    }

    @PostMapping("/{id}/convert-to-invoice")
    public InvoiceResponse convertToInvoice(@PathVariable UUID id) {
        return conversionService.convertToInvoice(id);
    }
}
