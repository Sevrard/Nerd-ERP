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
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class InvoiceService {

    public static final String INVOICE_LOCKED = "INVOICE_LOCKED";

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
    public PageResponse<InvoiceResponse> list(UUID workspaceId, UUID customerId, boolean openOnly, String q,
                                              Pageable pageable) {
        UUID tenantId = SecurityContextHelper.requireTenantId();
        workspaceService.requireInTenant(workspaceId, tenantId);
        String qPattern = q == null || q.isBlank() ? null : "%" + q.trim().toLowerCase() + "%";
        Page<Invoice> page = invoiceRepository.search(tenantId, workspaceId, customerId, openOnly, qPattern, pageable);
        return PageResponse.from(page.map(invoiceMapper::toResponse));
    }

    @Transactional(readOnly = true)
    public InvoiceResponse get(UUID id) {
        return toDetailedResponse(requireInvoice(id));
    }

    /**
     * Réponse détaillée : facture d'origine (avoir), avoirs émis (facture), acompte déduit (solde)
     * ou facture de solde (acompte).
     */
    public InvoiceResponse toDetailedResponse(Invoice invoice) {
        UUID tenantId = invoice.getTenantId();
        String creditedNumber = creditedInvoiceNumber(invoice);
        List<InvoiceResponse.LinkedDocument> creditNotes = invoice.isCreditNote()
                ? List.of()
                : invoiceRepository.findByTenantIdAndCreditedInvoiceIdOrderByIssueDateAscCreatedAtAsc(tenantId, invoice.getId())
                .stream().map(InvoiceService::link).toList();
        InvoiceResponse.LinkedDocument deposit = invoice.getDepositInvoiceId() == null ? null
                : invoiceRepository.findByIdAndTenantId(invoice.getDepositInvoiceId(), tenantId).map(InvoiceService::link).orElse(null);
        InvoiceResponse.LinkedDocument balance = !invoice.isDeposit() ? null
                : invoiceRepository.findFirstByTenantIdAndDepositInvoiceId(tenantId, invoice.getId()).map(InvoiceService::link).orElse(null);
        return invoiceMapper.toResponse(invoice, new InvoiceMapper.Links(creditedNumber, creditNotes, deposit, balance));
    }

    private static InvoiceResponse.LinkedDocument link(Invoice i) {
        return new InvoiceResponse.LinkedDocument(i.getId(), i.getNumber(), i.getTotalTtc());
    }

    /** Numéro de la facture d'acompte déduite par cette facture de solde, ou {@code null}. */
    public String depositInvoiceNumber(Invoice invoice) {
        if (invoice.getDepositInvoiceId() == null) {
            return null;
        }
        return invoiceRepository.findByIdAndTenantId(invoice.getDepositInvoiceId(), invoice.getTenantId())
                .map(Invoice::getNumber)
                .orElse(null);
    }

    /** Numéro de la facture créditée par cet avoir, ou {@code null}. */
    public String creditedInvoiceNumber(Invoice invoice) {
        if (invoice.getCreditedInvoiceId() == null) {
            return null;
        }
        return invoiceRepository.findByIdAndTenantId(invoice.getCreditedInvoiceId(), invoice.getTenantId())
                .map(Invoice::getNumber)
                .orElse(null);
    }

    /**
     * Suppression réservée aux brouillons et aux imports de scan (documents produits par un tiers).
     * Une facture numérotée et émise par l'ERP est inaltérable : elle se corrige par un avoir.
     */
    @Transactional
    public void delete(UUID id) {
        SecurityContextHelper.requireWriteAccess();
        Invoice invoice = requireInvoice(id);
        UUID tenantId = invoice.getTenantId();

        if (invoice.getIssuedAt() != null && invoice.getSourceDocumentPath() == null) {
            throw new ApiException(HttpStatus.CONFLICT, INVOICE_LOCKED,
                    "Une facture émise ne peut pas être supprimée : créez un avoir pour l'annuler ou la corriger.");
        }
        if (quotationRepository.existsByConvertedInvoiceIdAndTenantId(id, tenantId)) {
            throw new ApiException(HttpStatus.CONFLICT, INVOICE_LOCKED,
                    "Impossible de supprimer une facture issue d’un devis converti");
        }
        if (!invoiceRepository.findByTenantIdAndCreditedInvoiceIdOrderByIssueDateAscCreatedAtAsc(tenantId, id).isEmpty()) {
            throw new ApiException(HttpStatus.CONFLICT, INVOICE_LOCKED,
                    "Impossible de supprimer une facture sur laquelle un avoir a été émis");
        }
        if (invoiceRepository.findFirstByTenantIdAndDepositInvoiceId(tenantId, id).isPresent()) {
            throw new ApiException(HttpStatus.CONFLICT, INVOICE_LOCKED,
                    "Impossible de supprimer une facture d'acompte déduite sur une facture de solde");
        }
        paymentAllocationRepository.deleteByTenantIdAndInvoiceId(tenantId, id);

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
