package com.nerd.erp.application.quotation;

import com.nerd.erp.application.catalog.ProductService;
import com.nerd.erp.application.customer.CustomerService;
import com.nerd.erp.domain.quotation.Quotation;
import com.nerd.erp.domain.quotation.QuotationLine;
import com.nerd.erp.domain.quotation.QuotationStatus;
import com.nerd.erp.api.quotation.DocumentLineRequest;
import com.nerd.erp.api.quotation.QuotationLineResponse;
import com.nerd.erp.api.quotation.QuotationRequest;
import com.nerd.erp.api.quotation.QuotationResponse;
import com.nerd.erp.infrastructure.persistence.quotation.QuotationRepository;
import com.nerd.erp.infrastructure.security.core.SecurityContextHelper;
import com.nerd.erp.application.audit.AuditService;
import com.nerd.erp.application.document.DocumentLineBuilder;
import com.nerd.erp.application.document.DocumentNumberService;
import com.nerd.erp.domain.document.DocumentSequenceType;
import com.nerd.erp.api.error.ApiException;
import com.nerd.erp.application.money.MoneyCalculator;
import com.nerd.erp.api.shared.PageResponse;
import com.nerd.erp.domain.workspace.Workspace;
import com.nerd.erp.application.workspace.WorkspaceService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class QuotationService {

    private final QuotationRepository quotationRepository;
    private final CustomerService customerService;
    private final ProductService productService;
    private final WorkspaceService workspaceService;
    private final DocumentNumberService documentNumberService;
    private final AuditService auditService;

    public QuotationService(QuotationRepository quotationRepository,
                            CustomerService customerService,
                            ProductService productService,
                            WorkspaceService workspaceService,
                            DocumentNumberService documentNumberService,
                            AuditService auditService) {
        this.quotationRepository = quotationRepository;
        this.customerService = customerService;
        this.productService = productService;
        this.workspaceService = workspaceService;
        this.documentNumberService = documentNumberService;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public PageResponse<QuotationResponse> list(UUID workspaceId, Pageable pageable) {
        UUID tenantId = SecurityContextHelper.requireTenantId();
        workspaceService.requireInTenant(workspaceId, tenantId);
        Page<Quotation> page = quotationRepository.findByTenantIdAndWorkspaceIdOrderByCreatedAtDesc(
                tenantId, workspaceId, pageable);
        return PageResponse.from(page.map(this::toResponse));
    }

    @Transactional(readOnly = true)
    public QuotationResponse get(UUID id) {
        return toResponse(requireQuotation(id));
    }

    @Transactional
    public QuotationResponse createDraft(QuotationRequest request) {
        SecurityContextHelper.requireWriteAccess();
        UUID tenantId = SecurityContextHelper.requireTenantId();
        Workspace workspace = workspaceService.requireInTenant(request.workspaceId(), tenantId);
        var customer = customerService.requireCustomer(request.customerId());
        assertSameWorkspace(customer.getWorkspaceId(), request.workspaceId());
        Quotation quotation = new Quotation();
        quotation.setTenantId(tenantId);
        quotation.setWorkspaceId(request.workspaceId());
        quotation.setCustomerId(request.customerId());
        quotation.setStatus(QuotationStatus.DRAFT);
        applyDraftFields(quotation, request, tenantId);
        quotation = quotationRepository.save(quotation);
        auditService.record(tenantId, "Quotation", quotation.getId(), "CREATE_DRAFT", Map.of());
        return toResponse(quotation);
    }

    @Transactional
    public QuotationResponse updateDraft(UUID id, QuotationRequest request) {
        SecurityContextHelper.requireWriteAccess();
        Quotation quotation = requireQuotation(id);
        if (quotation.getStatus() != QuotationStatus.DRAFT) {
            throw new ApiException(HttpStatus.CONFLICT, "Only draft quotations can be edited");
        }
        var customer = customerService.requireCustomer(request.customerId());
        assertSameWorkspace(customer.getWorkspaceId(), quotation.getWorkspaceId());
        applyDraftFields(quotation, request, quotation.getTenantId());
        quotation = quotationRepository.save(quotation);
        return toResponse(quotation);
    }

    @Transactional
    public QuotationResponse send(UUID id) {
        SecurityContextHelper.requireWriteAccess();
        Quotation quotation = requireQuotation(id);
        if (quotation.getStatus() != QuotationStatus.DRAFT) {
            throw new ApiException(HttpStatus.CONFLICT, "Quotation is not a draft");
        }
        if (quotation.getLines().isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Quotation has no lines");
        }
        Workspace workspace = workspaceService.requireInTenant(quotation.getWorkspaceId(), quotation.getTenantId());
        if (quotation.getNumber() == null) {
            quotation.setNumber(documentNumberService.nextNumber(
                    quotation.getTenantId(), quotation.getWorkspaceId(),
                    DocumentSequenceType.QUOTATION, workspace.getQuotationPrefix()));
        }
        if (quotation.getIssueDate() == null) {
            quotation.setIssueDate(LocalDate.now());
        }
        quotation.setStatus(QuotationStatus.SENT);
        quotation = quotationRepository.save(quotation);
        auditService.record(quotation.getTenantId(), "Quotation", quotation.getId(), "SEND", Map.of());
        return toResponse(quotation);
    }

    @Transactional
    public QuotationResponse accept(UUID id) {
        SecurityContextHelper.requireWriteAccess();
        Quotation quotation = requireQuotation(id);
        if (quotation.getStatus() != QuotationStatus.SENT) {
            throw new ApiException(HttpStatus.CONFLICT, "Quotation must be sent before acceptance");
        }
        quotation.setStatus(QuotationStatus.ACCEPTED);
        quotation = quotationRepository.save(quotation);
        auditService.record(quotation.getTenantId(), "Quotation", quotation.getId(), "ACCEPT", Map.of());
        return toResponse(quotation);
    }

    Quotation requireQuotation(UUID id) {
        UUID tenantId = SecurityContextHelper.requireTenantId();
        Quotation quotation = quotationRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Quotation not found"));
        workspaceService.requireInTenant(quotation.getWorkspaceId(), tenantId);
        return quotation;
    }

    private void applyDraftFields(Quotation quotation, QuotationRequest request, UUID tenantId) {
        quotation.setIssueDate(request.issueDate());
        quotation.setValidUntil(request.validUntil());
        quotation.setNotes(request.notes());
        List<DocumentLineBuilder.LineInput> inputs = request.lines().stream()
                .map(this::toLineInput)
                .toList();
        validateProducts(tenantId, inputs);
        List<QuotationLine> lines = DocumentLineBuilder.buildQuotationLines(tenantId, inputs);
        quotation.replaceLines(lines);
        MoneyCalculator.DocumentTotals totals = DocumentLineBuilder.totalsFromQuotationLines(lines);
        quotation.setTotalHt(totals.totalHt());
        quotation.setTotalVat(totals.totalVat());
        quotation.setTotalTtc(totals.totalTtc());
    }

    private void validateProducts(UUID tenantId, List<DocumentLineBuilder.LineInput> inputs) {
        for (DocumentLineBuilder.LineInput input : inputs) {
            if (input.productId() != null) {
                productService.requireProduct(input.productId(), tenantId);
            }
        }
    }

    private DocumentLineBuilder.LineInput toLineInput(DocumentLineRequest line) {
        return new DocumentLineBuilder.LineInput(
                line.productId(), line.description(), line.quantity(), line.unitPriceHt(), line.vatRate());
    }

    private void assertSameWorkspace(UUID a, UUID b) {
        if (!a.equals(b)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Customer workspace mismatch");
        }
    }

    private QuotationResponse toResponse(Quotation q) {
        List<QuotationLineResponse> lines = q.getLines().stream()
                .map(l -> new QuotationLineResponse(l.getId(), l.getProductId(), l.getLineOrder(),
                        l.getDescription(), l.getQuantity(), l.getUnitPriceHt(), l.getVatRate(),
                        l.getLineTotalHt(), l.getLineTotalVat(), l.getLineTotalTtc()))
                .toList();
        return new QuotationResponse(q.getId(), q.getWorkspaceId(), q.getCustomerId(), q.getNumber(),
                q.getStatus(), q.getIssueDate(), q.getValidUntil(), q.getTotalHt(), q.getTotalVat(),
                q.getTotalTtc(), q.getNotes(), q.getConvertedInvoiceId(), lines);
    }
}
