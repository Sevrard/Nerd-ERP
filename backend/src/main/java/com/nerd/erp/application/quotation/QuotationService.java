package com.nerd.erp.application.quotation;

import com.nerd.erp.application.catalog.ProductService;
import com.nerd.erp.application.customer.CustomerService;
import com.nerd.erp.application.customer.CustomerSnapshotFactory;
import com.nerd.erp.domain.quotation.Quotation;
import com.nerd.erp.domain.quotation.QuotationDecisionSource;
import com.nerd.erp.domain.quotation.QuotationLine;
import com.nerd.erp.domain.quotation.QuotationStatus;
import com.nerd.erp.api.quotation.DocumentLineRequest;
import com.nerd.erp.api.quotation.QuotationRequest;
import com.nerd.erp.api.quotation.QuotationResponse;
import com.nerd.erp.domain.invoice.Invoice;
import com.nerd.erp.domain.invoice.InvoiceType;
import com.nerd.erp.infrastructure.persistence.invoice.InvoiceRepository;
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

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
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
    private final QuotationMapper quotationMapper;
    private final InvoiceRepository invoiceRepository;

    public QuotationService(QuotationRepository quotationRepository,
                            CustomerService customerService,
                            ProductService productService,
                            WorkspaceService workspaceService,
                            DocumentNumberService documentNumberService,
                            AuditService auditService,
                            QuotationMapper quotationMapper,
                            InvoiceRepository invoiceRepository) {
        this.quotationRepository = quotationRepository;
        this.customerService = customerService;
        this.productService = productService;
        this.workspaceService = workspaceService;
        this.documentNumberService = documentNumberService;
        this.auditService = auditService;
        this.quotationMapper = quotationMapper;
        this.invoiceRepository = invoiceRepository;
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

    /**
     * Copie d'un devis (quel que soit son statut) en nouveau brouillon : même client, lignes, remises, acompte
     * et notes ; dates réinitialisées (émission aujourd'hui, validité recalculée sur la durée d'origine).
     */
    @Transactional
    public QuotationResponse duplicate(UUID id) {
        SecurityContextHelper.requireWriteAccess();
        Quotation source = requireQuotation(id);
        Quotation copy = new Quotation();
        copy.setTenantId(source.getTenantId());
        copy.setWorkspaceId(source.getWorkspaceId());
        copy.setCustomerId(source.getCustomerId());
        copy.setStatus(QuotationStatus.DRAFT);
        LocalDate today = LocalDate.now();
        copy.setIssueDate(today);
        long validity = source.getIssueDate() != null && source.getValidUntil() != null
                && !source.getValidUntil().isBefore(source.getIssueDate())
                ? java.time.temporal.ChronoUnit.DAYS.between(source.getIssueDate(), source.getValidUntil())
                : 30;
        copy.setValidUntil(today.plusDays(validity));
        copy.setNotes(source.getNotes());
        copy.setDiscountPercent(nz(source.getDiscountPercent()));
        copy.setDiscountAmountHt(nz(source.getDiscountAmountHt()));
        copy.setDepositPercent(nz(source.getDepositPercent()));
        copy.setTotalHt(nz(source.getTotalHt()));
        copy.setTotalVat(nz(source.getTotalVat()));
        copy.setTotalTtc(nz(source.getTotalTtc()));
        List<DocumentLineBuilder.LineInput> inputs = source.getLines().stream()
                .map(l -> new DocumentLineBuilder.LineInput(l.getProductId(), l.getDescription(), l.getQuantity(),
                        l.getUnitPriceHt(), l.getVatRate(), l.getDiscountPercent()))
                .toList();
        copy.replaceLines(DocumentLineBuilder.buildQuotationLines(source.getTenantId(), inputs));
        copy = quotationRepository.save(copy);
        auditService.record(copy.getTenantId(), "Quotation", copy.getId(), "DUPLICATE",
                Map.of("sourceId", source.getId().toString(), "sourceNumber", source.getNumber() == null ? "" : source.getNumber()));
        return toResponse(copy);
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
            quotation.setNumber(documentNumberService.nextNumber(workspace, DocumentSequenceType.QUOTATION));
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
        recordDecision(quotation, QuotationStatus.ACCEPTED, SecurityContextHelper.requireUser().email(),
                null, QuotationDecisionSource.MANUAL);
        quotation = quotationRepository.save(quotation);
        auditService.record(quotation.getTenantId(), "Quotation", quotation.getId(), "ACCEPT",
                Map.of("source", "MANUAL"));
        return toResponse(quotation);
    }

    @Transactional
    public QuotationResponse reject(UUID id, String reason) {
        SecurityContextHelper.requireWriteAccess();
        Quotation quotation = requireQuotation(id);
        if (quotation.getStatus() != QuotationStatus.SENT) {
            throw new ApiException(HttpStatus.CONFLICT, "Quotation must be sent before rejection");
        }
        recordDecision(quotation, QuotationStatus.REJECTED, SecurityContextHelper.requireUser().email(),
                reason, QuotationDecisionSource.MANUAL);
        quotation = quotationRepository.save(quotation);
        auditService.record(quotation.getTenantId(), "Quotation", quotation.getId(), "REJECT",
                Map.of("source", "MANUAL", "reason", reason == null ? "" : reason));
        return toResponse(quotation);
    }

    /**
     * Applique une décision client (acceptation / refus). Le lien public reste consultable
     * (le client retrouve son devis) mais plus aucune action n'y est possible.
     */
    public static void recordDecision(Quotation quotation, QuotationStatus status, String decidedBy,
                                      String comment, QuotationDecisionSource source) {
        quotation.setStatus(status);
        quotation.setDecidedAt(Instant.now());
        quotation.setDecidedBy(decidedBy);
        quotation.setDecisionComment(comment == null || comment.isBlank() ? null : comment.trim());
        quotation.setDecisionSource(source);
    }

    public Quotation requireQuotation(UUID id) {
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

        BigDecimal discountPercent = percentOrZero(request.discountPercent());
        BigDecimal depositPercent = percentOrZero(request.depositPercent());
        MoneyCalculator.DocumentTotals gross = DocumentLineBuilder.totalsFromQuotationLines(lines);
        MoneyCalculator.DiscountedTotals discounted = MoneyCalculator.applyDiscount(gross, discountPercent);

        quotation.setDiscountPercent(discountPercent);
        quotation.setDiscountAmountHt(discounted.discountAmountHt());
        quotation.setDepositPercent(depositPercent);
        quotation.setTotalHt(discounted.net().totalHt());
        quotation.setTotalVat(discounted.net().totalVat());
        quotation.setTotalTtc(discounted.net().totalTtc());
    }

    private static BigDecimal percentOrZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value.setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal nz(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
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
                line.productId(), line.description(), line.quantity(), line.unitPriceHt(), line.vatRate(),
                line.discountPercent() == null ? null : line.discountPercent().setScale(2, RoundingMode.HALF_UP));
    }

    private void assertSameWorkspace(UUID a, UUID b) {
        if (!a.equals(b)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Customer workspace mismatch");
        }
    }

    private QuotationResponse toResponse(Quotation q) {
        Invoice deposit = q.getStatus() == QuotationStatus.ACCEPTED || q.getStatus() == QuotationStatus.CONVERTED
                ? invoiceRepository.findFirstByTenantIdAndSourceQuotationIdAndInvoiceTypeOrderByCreatedAtAsc(
                        q.getTenantId(), q.getId(), InvoiceType.DEPOSIT).orElse(null)
                : null;
        return quotationMapper.toResponse(q, deposit, customerName(q));
    }

    /** Libellé client pour les listes ; {@code null} si le client n'est plus lisible (jamais bloquant). */
    private String customerName(Quotation q) {
        if (q.getCustomerId() == null) {
            return null;
        }
        try {
            return CustomerSnapshotFactory.displayName(customerService.requireCustomer(q.getCustomerId()));
        } catch (ApiException e) {
            return null;
        }
    }
}
