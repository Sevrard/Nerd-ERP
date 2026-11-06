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

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Devis accepté → facture(s). Avec un acompte : une facture d'acompte ({@link InvoiceType#DEPOSIT}) peut être
 * émise d'abord ; la facture de solde reprend toutes les lignes du devis et déduit l'acompte (lignes négatives,
 * une par taux de TVA) afin que TVA et TTC restants soient exacts.
 */
@Service
public class QuotationConversionService {

    public static final String DEPOSIT_NOT_ALLOWED = "DEPOSIT_NOT_ALLOWED";

    private final QuotationRepository quotationRepository;
    private final InvoiceRepository invoiceRepository;
    private final CustomerService customerService;
    private final WorkspaceService workspaceService;
    private final DocumentNumberService documentNumberService;
    private final InvoiceService invoiceService;
    private final AuditService auditService;

    public QuotationConversionService(QuotationRepository quotationRepository,
                                      InvoiceRepository invoiceRepository,
                                      CustomerService customerService,
                                      WorkspaceService workspaceService,
                                      DocumentNumberService documentNumberService,
                                      InvoiceService invoiceService,
                                      AuditService auditService) {
        this.quotationRepository = quotationRepository;
        this.invoiceRepository = invoiceRepository;
        this.customerService = customerService;
        this.workspaceService = workspaceService;
        this.documentNumberService = documentNumberService;
        this.invoiceService = invoiceService;
        this.auditService = auditService;
    }

    /** Facture d'acompte sur un devis accepté (une seule par devis ; idempotent). */
    @Transactional
    public InvoiceResponse createDepositInvoice(UUID quotationId) {
        SecurityContextHelper.requireWriteAccess();
        UUID tenantId = SecurityContextHelper.requireTenantId();

        Optional<Invoice> existing = invoiceRepository
                .findFirstByTenantIdAndSourceQuotationIdAndInvoiceTypeOrderByCreatedAtAsc(tenantId, quotationId, InvoiceType.DEPOSIT);
        if (existing.isPresent()) {
            return invoiceService.toDetailedResponse(existing.get());
        }

        Quotation quotation = quotationRepository.findByIdAndTenantIdForUpdate(quotationId, tenantId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Quotation not found"));
        if (quotation.getStatus() != QuotationStatus.ACCEPTED) {
            throw new ApiException(HttpStatus.CONFLICT, DEPOSIT_NOT_ALLOWED,
                    "Une facture d'acompte ne peut être émise que sur un devis accepté et non encore facturé");
        }
        BigDecimal pct = nz(quotation.getDepositPercent());
        if (pct.signum() <= 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, DEPOSIT_NOT_ALLOWED, "Ce devis ne prévoit pas d'acompte");
        }
        if (quotation.getLines().isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Quotation has no lines");
        }

        Workspace workspace = workspaceService.requireInTenant(quotation.getWorkspaceId(), tenantId);
        var customer = customerService.requireCustomer(quotation.getCustomerId());

        Invoice deposit = newInvoice(tenantId, quotation, InvoiceType.DEPOSIT, customer);
        deposit.setDueDate(LocalDate.now());
        deposit.setNotes("Acompte de " + plain(pct) + " % sur le devis n° " + quotation.getNumber());

        List<DocumentLineBuilder.LineInput> inputs = new ArrayList<>();
        String label = "Acompte " + plain(pct) + " % — devis n° " + quotation.getNumber();
        for (MoneyCalculator.VatLine vat : DocumentLineBuilder.vatBreakdown(quotation.getLines(), quotation.getDiscountPercent())) {
            BigDecimal amountHt = MoneyCalculator.percentOf(vat.baseHt(), pct);
            if (amountHt.signum() == 0) {
                continue;
            }
            inputs.add(new DocumentLineBuilder.LineInput(null, label + " (TVA " + plain(vat.rate()) + " %)",
                    BigDecimal.ONE, amountHt, vat.rate()));
        }
        List<InvoiceLine> lines = DocumentLineBuilder.buildInvoiceLines(tenantId, inputs);
        deposit.replaceLines(lines);
        MoneyCalculator.DocumentTotals totals = DocumentLineBuilder.totalsFromInvoiceLines(lines);
        deposit.setDiscountPercent(BigDecimal.ZERO);
        deposit.setDiscountAmountHt(BigDecimal.ZERO);
        deposit.setTotalHt(totals.totalHt());
        deposit.setTotalVat(totals.totalVat());
        deposit.setTotalTtc(totals.totalTtc());

        issue(deposit, workspace);
        deposit = invoiceRepository.save(deposit);

        auditService.record(tenantId, "Invoice", deposit.getId(), "CREATE_DEPOSIT_INVOICE",
                Map.of("quotationId", quotationId.toString(), "depositPercent", pct, "totalTtc", deposit.getTotalTtc()));
        auditService.record(tenantId, "Invoice", deposit.getId(), "ISSUE",
                Map.of("number", deposit.getNumber(), "totalTtc", deposit.getTotalTtc()));
        return invoiceService.toDetailedResponse(deposit);
    }

    /** Facture (de solde si un acompte a été facturé) ; idempotent. */
    @Transactional
    public InvoiceResponse convertToInvoice(UUID quotationId) {
        SecurityContextHelper.requireWriteAccess();
        UUID tenantId = SecurityContextHelper.requireTenantId();

        var existingBySource = invoiceRepository
                .findFirstByTenantIdAndSourceQuotationIdAndInvoiceTypeOrderByCreatedAtAsc(tenantId, quotationId, InvoiceType.STANDARD);
        if (existingBySource.isPresent()) {
            return invoiceService.toDetailedResponse(existingBySource.get());
        }

        Quotation quotation = quotationRepository.findByIdAndTenantIdForUpdate(quotationId, tenantId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Quotation not found"));

        if (quotation.getConvertedInvoiceId() != null) {
            Invoice existing = invoiceRepository.findByIdAndTenantId(quotation.getConvertedInvoiceId(), tenantId)
                    .orElseThrow(() -> new ApiException(HttpStatus.CONFLICT, "Converted invoice reference broken"));
            return invoiceService.toDetailedResponse(existing);
        }

        if (quotation.getStatus() != QuotationStatus.ACCEPTED) {
            throw new ApiException(HttpStatus.CONFLICT, "Quotation must be accepted before conversion");
        }
        if (quotation.getLines().isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Quotation has no lines");
        }

        Workspace workspace = workspaceService.requireInTenant(quotation.getWorkspaceId(), tenantId);
        var customer = customerService.requireCustomer(quotation.getCustomerId());
        Invoice deposit = invoiceRepository
                .findFirstByTenantIdAndSourceQuotationIdAndInvoiceTypeOrderByCreatedAtAsc(tenantId, quotationId, InvoiceType.DEPOSIT)
                .orElse(null);

        Invoice invoice = newInvoice(tenantId, quotation, InvoiceType.STANDARD, customer);
        invoice.setDueDate(LocalDate.now().plusDays(workspace.getPaymentTermsDays()));
        invoice.setNotes(quotation.getNotes());

        List<DocumentLineBuilder.LineInput> inputs = new ArrayList<>(
                quotation.getLines().stream().map(this::fromQuotationLine).toList());
        List<DocumentLineBuilder.LineInput> deductions = new ArrayList<>();
        if (deposit != null) {
            invoice.setDepositInvoiceId(deposit.getId());
            for (InvoiceLine dl : deposit.getLines()) {
                deductions.add(new DocumentLineBuilder.LineInput(null,
                        "Déduction acompte — facture n° " + deposit.getNumber() + " (TVA " + plain(dl.getVatRate()) + " %)",
                        BigDecimal.ONE.negate(), dl.getLineTotalHt(), dl.getVatRate()));
            }
        }

        // Les prestations subissent la remise globale du devis ; la déduction d'acompte, déjà nette, non.
        List<InvoiceLine> serviceLines = DocumentLineBuilder.buildInvoiceLines(tenantId, inputs);
        List<InvoiceLine> deductionLines = DocumentLineBuilder.buildInvoiceLines(tenantId, deductions);
        MoneyCalculator.DocumentTotals gross = DocumentLineBuilder.totalsFromInvoiceLines(serviceLines);
        MoneyCalculator.DiscountedTotals discounted = MoneyCalculator.applyDiscount(gross, quotation.getDiscountPercent());
        MoneyCalculator.DocumentTotals deducted = DocumentLineBuilder.totalsFromInvoiceLines(deductionLines);

        List<InvoiceLine> all = new ArrayList<>(serviceLines);
        int order = all.size();
        for (InvoiceLine dl : deductionLines) {
            dl.setLineOrder(order++);
            all.add(dl);
        }
        invoice.replaceLines(all);
        invoice.setDiscountPercent(nz(quotation.getDiscountPercent()));
        invoice.setDiscountAmountHt(discounted.discountAmountHt());
        invoice.setTotalHt(discounted.net().totalHt().add(deducted.totalHt()).setScale(4, RoundingMode.HALF_UP));
        invoice.setTotalVat(discounted.net().totalVat().add(deducted.totalVat()).setScale(4, RoundingMode.HALF_UP));
        invoice.setTotalTtc(discounted.net().totalTtc().add(deducted.totalTtc()).setScale(4, RoundingMode.HALF_UP));

        issue(invoice, workspace);
        invoice = invoiceRepository.save(invoice);

        quotation.setConvertedInvoiceId(invoice.getId());
        quotation.setStatus(QuotationStatus.CONVERTED);
        quotationRepository.save(quotation);

        auditService.record(tenantId, "Invoice", invoice.getId(), "CONVERT_FROM_QUOTATION",
                deposit == null
                        ? Map.of("quotationId", quotationId.toString())
                        : Map.of("quotationId", quotationId.toString(), "depositInvoiceId", deposit.getId().toString()));
        auditService.record(tenantId, "Invoice", invoice.getId(), "ISSUE",
                Map.of("number", invoice.getNumber(), "totalTtc", invoice.getTotalTtc()));

        return invoiceService.toDetailedResponse(invoice);
    }

    private static Invoice newInvoice(UUID tenantId, Quotation quotation, InvoiceType type,
                                      com.nerd.erp.domain.customer.Customer customer) {
        Invoice invoice = new Invoice();
        invoice.setTenantId(tenantId);
        invoice.setWorkspaceId(quotation.getWorkspaceId());
        invoice.setCustomerId(quotation.getCustomerId());
        invoice.setSourceQuotationId(quotation.getId());
        invoice.setInvoiceType(type);
        invoice.setIssueDate(LocalDate.now());
        invoice.setSnapshotCustomer(CustomerSnapshotFactory.fromCustomer(customer));
        return invoice;
    }

    private void issue(Invoice invoice, Workspace workspace) {
        invoice.setNumber(documentNumberService.nextNumber(workspace, DocumentSequenceType.INVOICE));
        invoice.setStatus(InvoiceStatus.ISSUED);
        invoice.setIssuedAt(Instant.now());
    }

    private DocumentLineBuilder.LineInput fromQuotationLine(QuotationLine line) {
        return new DocumentLineBuilder.LineInput(
                line.getProductId(), line.getDescription(), line.getQuantity(),
                line.getUnitPriceHt(), line.getVatRate(), line.getDiscountPercent());
    }

    private static String plain(BigDecimal v) {
        BigDecimal s = nz(v).stripTrailingZeros();
        return s.scale() <= 0 ? s.toPlainString() : s.setScale(2, RoundingMode.HALF_UP).toPlainString().replace('.', ',');
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
