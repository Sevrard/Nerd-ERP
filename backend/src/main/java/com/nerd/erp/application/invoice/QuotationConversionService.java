package com.nerd.erp.application.invoice;

import com.nerd.erp.application.customer.CustomerSnapshotFactory;
import com.nerd.erp.application.customer.CustomerService;
import com.nerd.erp.domain.invoice.Invoice;
import com.nerd.erp.domain.invoice.InvoiceLine;
import com.nerd.erp.domain.invoice.InvoiceStatus;
import com.nerd.erp.domain.invoice.InvoiceType;
import com.nerd.erp.api.invoice.InvoiceResponse;
import com.nerd.erp.infrastructure.persistence.invoice.InvoiceRepository;
import com.nerd.erp.domain.quotation.Quotation;
import com.nerd.erp.domain.quotation.QuotationLine;
import com.nerd.erp.domain.quotation.QuotationStatus;
import com.nerd.erp.infrastructure.persistence.quotation.QuotationRepository;
import com.nerd.erp.infrastructure.security.core.SecurityContextHelper;
import com.nerd.erp.application.audit.AuditService;
import com.nerd.erp.application.document.DocumentLineBuilder;
import com.nerd.erp.application.document.DocumentNumberService;
import com.nerd.erp.domain.document.DocumentSequenceType;
import com.nerd.erp.api.error.ApiException;
import com.nerd.erp.application.money.MoneyCalculator;
import com.nerd.erp.domain.workspace.Workspace;
import com.nerd.erp.application.workspace.WorkspaceService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class QuotationConversionService {

    private final QuotationRepository quotationRepository;
    private final InvoiceRepository invoiceRepository;
    private final CustomerService customerService;
    private final WorkspaceService workspaceService;
    private final DocumentNumberService documentNumberService;
    private final InvoiceMapper invoiceMapper;
    private final AuditService auditService;

    public QuotationConversionService(QuotationRepository quotationRepository,
                                      InvoiceRepository invoiceRepository,
                                      CustomerService customerService,
                                      WorkspaceService workspaceService,
                                      DocumentNumberService documentNumberService,
                                      InvoiceMapper invoiceMapper,
                                      AuditService auditService) {
        this.quotationRepository = quotationRepository;
        this.invoiceRepository = invoiceRepository;
        this.customerService = customerService;
        this.workspaceService = workspaceService;
        this.documentNumberService = documentNumberService;
        this.invoiceMapper = invoiceMapper;
        this.auditService = auditService;
    }

    @Transactional
    public InvoiceResponse convertToInvoice(UUID quotationId) {
        SecurityContextHelper.requireWriteAccess();
        UUID tenantId = SecurityContextHelper.requireTenantId();

        var existingBySource = invoiceRepository.findBySourceQuotationIdAndTenantId(quotationId, tenantId);
        if (existingBySource.isPresent()) {
            return invoiceMapper.toResponse(existingBySource.get());
        }

        Quotation quotation = quotationRepository.findByIdAndTenantIdForUpdate(quotationId, tenantId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Quotation not found"));

        if (quotation.getConvertedInvoiceId() != null) {
            Invoice existing = invoiceRepository.findByIdAndTenantId(quotation.getConvertedInvoiceId(), tenantId)
                    .orElseThrow(() -> new ApiException(HttpStatus.CONFLICT, "Converted invoice reference broken"));
            return invoiceMapper.toResponse(existing);
        }

        if (quotation.getStatus() != QuotationStatus.ACCEPTED) {
            throw new ApiException(HttpStatus.CONFLICT, "Quotation must be accepted before conversion");
        }
        if (quotation.getLines().isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Quotation has no lines");
        }

        Workspace workspace = workspaceService.requireInTenant(quotation.getWorkspaceId(), tenantId);
        var customer = customerService.requireCustomer(quotation.getCustomerId());

        Invoice invoice = new Invoice();
        invoice.setTenantId(tenantId);
        invoice.setWorkspaceId(quotation.getWorkspaceId());
        invoice.setCustomerId(quotation.getCustomerId());
        invoice.setSourceQuotationId(quotation.getId());
        invoice.setInvoiceType(InvoiceType.STANDARD);
        invoice.setIssueDate(LocalDate.now());
        invoice.setDueDate(LocalDate.now().plusDays(30));
        invoice.setSnapshotCustomer(CustomerSnapshotFactory.fromCustomer(customer));
        invoice.setNotes(quotation.getNotes());

        List<DocumentLineBuilder.LineInput> inputs = quotation.getLines().stream()
                .map(this::fromQuotationLine)
                .toList();
        List<InvoiceLine> lines = DocumentLineBuilder.buildInvoiceLines(tenantId, inputs);
        invoice.replaceLines(lines);
        MoneyCalculator.DocumentTotals totals = DocumentLineBuilder.totalsFromInvoiceLines(lines);
        invoice.setTotalHt(totals.totalHt());
        invoice.setTotalVat(totals.totalVat());
        invoice.setTotalTtc(totals.totalTtc());

        String number = documentNumberService.nextNumber(tenantId, workspace.getId(),
                DocumentSequenceType.INVOICE, workspace.getInvoicePrefix());
        invoice.setNumber(number);
        invoice.setStatus(InvoiceStatus.ISSUED);
        invoice.setIssuedAt(Instant.now());

        invoice = invoiceRepository.save(invoice);

        quotation.setConvertedInvoiceId(invoice.getId());
        quotation.setStatus(QuotationStatus.CONVERTED);
        quotationRepository.save(quotation);

        auditService.record(tenantId, "Invoice", invoice.getId(), "CONVERT_FROM_QUOTATION",
                Map.of("quotationId", quotationId.toString()));

        return invoiceMapper.toResponse(invoice);
    }

    private DocumentLineBuilder.LineInput fromQuotationLine(QuotationLine line) {
        return new DocumentLineBuilder.LineInput(
                line.getProductId(), line.getDescription(), line.getQuantity(),
                line.getUnitPriceHt(), line.getVatRate());
    }
}
