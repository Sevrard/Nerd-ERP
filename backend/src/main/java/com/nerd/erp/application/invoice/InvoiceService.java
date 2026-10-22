package com.nerd.erp.application.invoice;

import com.nerd.erp.api.error.ApiException;
import com.nerd.erp.api.invoice.InvoiceResponse;
import com.nerd.erp.api.shared.PageResponse;
import com.nerd.erp.application.audit.AuditService;
import com.nerd.erp.application.workspace.WorkspaceService;
import com.nerd.erp.domain.invoice.Invoice;
import com.nerd.erp.infrastructure.persistence.invoice.InvoiceRepository;
import com.nerd.erp.infrastructure.persistence.payment.PaymentAllocationRepository;
import com.nerd.erp.infrastructure.persistence.quotation.QuotationRepository;
import com.nerd.erp.infrastructure.security.core.SecurityContextHelper;
import com.nerd.erp.infrastructure.storage.InvoiceDocumentStorage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

@Service
public class InvoiceService {

    private final InvoiceRepository invoiceRepository;
    private final InvoiceMapper invoiceMapper;
    private final WorkspaceService workspaceService;
    private final PaymentAllocationRepository paymentAllocationRepository;
    private final QuotationRepository quotationRepository;
    private final AuditService auditService;
    private final InvoiceDocumentStorage invoiceDocumentStorage;

    public InvoiceService(InvoiceRepository invoiceRepository,
                          InvoiceMapper invoiceMapper,
                          WorkspaceService workspaceService,
                          PaymentAllocationRepository paymentAllocationRepository,
                          QuotationRepository quotationRepository,
                          AuditService auditService,
                          InvoiceDocumentStorage invoiceDocumentStorage) {
        this.invoiceRepository = invoiceRepository;
        this.invoiceMapper = invoiceMapper;
        this.workspaceService = workspaceService;
        this.paymentAllocationRepository = paymentAllocationRepository;
        this.quotationRepository = quotationRepository;
        this.auditService = auditService;
        this.invoiceDocumentStorage = invoiceDocumentStorage;
    }

    @Transactional(readOnly = true)
    public PageResponse<InvoiceResponse> list(UUID workspaceId, Pageable pageable) {
        UUID tenantId = SecurityContextHelper.requireTenantId();
        workspaceService.requireInTenant(workspaceId, tenantId);
        Page<Invoice> page = invoiceRepository.findByTenantIdAndWorkspaceIdOrderByIssueDateDesc(
                tenantId, workspaceId, pageable);
        return PageResponse.from(page.map(invoiceMapper::toResponse));
    }

    @Transactional(readOnly = true)
    public InvoiceResponse get(UUID id) {
        return invoiceMapper.toResponse(requireInvoice(id));
    }

    @Transactional
    public void delete(UUID id) {
        SecurityContextHelper.requireWriteAccess();
        Invoice invoice = requireInvoice(id);
        UUID tenantId = invoice.getTenantId();

        paymentAllocationRepository.deleteByTenantIdAndInvoiceId(tenantId, id);
        if (quotationRepository.existsByConvertedInvoiceIdAndTenantId(id, tenantId)) {
            throw new ApiException(HttpStatus.CONFLICT,
                    "Impossible de supprimer une facture issue d’un devis converti");
        }

        String docPath = invoice.getSourceDocumentPath();
        invoiceRepository.delete(invoice);
        invoiceDocumentStorage.deleteIfPresent(docPath);
        auditService.record(tenantId, "Invoice", id, "DELETE",
                Map.of(
                        "number", invoice.getNumber(),
                        "status", invoice.getStatus().name(),
                        "amountPaid", invoice.getAmountPaid() != null ? invoice.getAmountPaid() : BigDecimal.ZERO));
    }

    public Invoice requireInvoice(UUID id) {
        UUID tenantId = SecurityContextHelper.requireTenantId();
        Invoice invoice = invoiceRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Invoice not found"));
        workspaceService.requireInTenant(invoice.getWorkspaceId(), tenantId);
        return invoice;
    }
}
