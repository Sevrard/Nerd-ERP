package com.nerd.erp.application.invoice;

import com.nerd.erp.api.error.ApiException;
import com.nerd.erp.api.invoice.CreditNoteRequest;
import com.nerd.erp.api.invoice.InvoiceResponse;
import com.nerd.erp.api.quotation.DocumentLineRequest;
import com.nerd.erp.application.audit.AuditService;
import com.nerd.erp.application.document.DocumentLineBuilder;
import com.nerd.erp.application.document.DocumentNumberService;
import com.nerd.erp.application.money.MoneyCalculator;
import com.nerd.erp.application.workspace.WorkspaceService;
import com.nerd.erp.domain.document.DocumentSequenceType;
import com.nerd.erp.domain.invoice.Invoice;
import com.nerd.erp.domain.invoice.InvoiceLine;
import com.nerd.erp.domain.invoice.InvoiceStatus;
import com.nerd.erp.domain.invoice.InvoiceType;
import com.nerd.erp.domain.workspace.Workspace;
import com.nerd.erp.infrastructure.persistence.invoice.InvoiceRepository;
import com.nerd.erp.infrastructure.security.core.SecurityContextHelper;
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

/**
 * Avoirs : seule voie de correction / annulation d'une facture émise.
 * Un avoir est une {@link Invoice} de type {@link InvoiceType#CREDIT_NOTE} aux montants <b>négatifs</b>
 * (quantités négatives), numérotée dans sa propre séquence, liée à la facture d'origine.
 * Un avoir total sur une facture non encaissée passe la facture d'origine à {@link InvoiceStatus#CANCELLED}.
 */
@Service
public class CreditNoteService {

    public static final String CREDIT_NOTE_NOT_ALLOWED = "CREDIT_NOTE_NOT_ALLOWED";
    public static final String CREDIT_EXCEEDS_INVOICE = "CREDIT_EXCEEDS_INVOICE";

    private final InvoiceRepository invoiceRepository;
    private final InvoiceService invoiceService;
    private final WorkspaceService workspaceService;
    private final DocumentNumberService documentNumberService;
    private final AuditService auditService;

    public CreditNoteService(InvoiceRepository invoiceRepository,
                             InvoiceService invoiceService,
                             WorkspaceService workspaceService,
                             DocumentNumberService documentNumberService,
                             AuditService auditService) {
        this.invoiceRepository = invoiceRepository;
        this.invoiceService = invoiceService;
        this.workspaceService = workspaceService;
        this.documentNumberService = documentNumberService;
        this.auditService = auditService;
    }

    @Transactional
    public InvoiceResponse create(UUID invoiceId, CreditNoteRequest request) {
        SecurityContextHelper.requireWriteAccess();
        Invoice original = invoiceService.requireInvoice(invoiceId);
        UUID tenantId = original.getTenantId();

        if (original.isCreditNote()) {
            throw new ApiException(HttpStatus.CONFLICT, CREDIT_NOTE_NOT_ALLOWED,
                    "Impossible d'émettre un avoir sur un avoir");
        }
        if (original.getStatus() == InvoiceStatus.DRAFT || original.getIssuedAt() == null) {
            throw new ApiException(HttpStatus.CONFLICT, CREDIT_NOTE_NOT_ALLOWED,
                    "Un avoir ne peut porter que sur une facture émise");
        }
        if (original.getStatus() == InvoiceStatus.CANCELLED) {
            throw new ApiException(HttpStatus.CONFLICT, CREDIT_NOTE_NOT_ALLOWED,
                    "Cette facture est déjà annulée");
        }

        Workspace workspace = workspaceService.requireInTenant(original.getWorkspaceId(), tenantId);

        Invoice creditNote = new Invoice();
        creditNote.setTenantId(tenantId);
        creditNote.setWorkspaceId(original.getWorkspaceId());
        creditNote.setCustomerId(original.getCustomerId());
        creditNote.setInvoiceType(InvoiceType.CREDIT_NOTE);
        creditNote.setCreditedInvoiceId(original.getId());
        creditNote.setCreditReason(request.reason().trim());
        creditNote.setIssueDate(LocalDate.now());
        creditNote.setDueDate(LocalDate.now());
        creditNote.setSnapshotCustomer(original.getSnapshotCustomer());
        creditNote.setExternalReference(original.getExternalReference());

        List<DocumentLineBuilder.LineInput> inputs = request.isTotal()
                ? original.getLines().stream().map(CreditNoteService::negate).toList()
                : request.lines().stream().map(CreditNoteService::negate).toList();
        List<InvoiceLine> lines = DocumentLineBuilder.buildInvoiceLines(tenantId, inputs);
        creditNote.replaceLines(lines);

        MoneyCalculator.DocumentTotals gross = DocumentLineBuilder.totalsFromInvoiceLines(lines);
        // Avoir total : on reprend la remise de la facture pour obtenir exactement l'inverse de ses totaux.
        BigDecimal discountPercent = request.isTotal() ? nz(original.getDiscountPercent()) : BigDecimal.ZERO;
        MoneyCalculator.DiscountedTotals discounted = MoneyCalculator.applyDiscount(gross, discountPercent);
        creditNote.setDiscountPercent(discountPercent);
        creditNote.setDiscountAmountHt(discounted.discountAmountHt());
        creditNote.setTotalHt(discounted.net().totalHt());
        creditNote.setTotalVat(discounted.net().totalVat());
        creditNote.setTotalTtc(discounted.net().totalTtc());
        creditNote.setAmountPaid(BigDecimal.ZERO);

        // Plafond : le cumul des avoirs ne peut dépasser le TTC de la facture.
        BigDecimal alreadyCredited = invoiceRepository
                .findByTenantIdAndCreditedInvoiceIdOrderByIssueDateAscCreatedAtAsc(tenantId, original.getId())
                .stream()
                .map(cn -> nz(cn.getTotalTtc()).abs())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal credited = alreadyCredited.add(creditNote.getTotalTtc().abs());
        BigDecimal originalTtc = nz(original.getTotalTtc());
        if (credited.compareTo(originalTtc) > 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, CREDIT_EXCEEDS_INVOICE,
                    "Le cumul des avoirs (" + credited.setScale(2, RoundingMode.HALF_UP)
                            + ") dépasse le montant TTC de la facture (" + originalTtc.setScale(2, RoundingMode.HALF_UP) + ")");
        }

        creditNote.setNumber(documentNumberService.nextNumber(workspace, DocumentSequenceType.CREDIT_NOTE));
        creditNote.setIssuedAt(Instant.now());

        // Avoir total sur une facture non encaissée : la facture est annulée et l'avoir est soldé
        // (aucun mouvement de trésorerie). Sinon l'avoir reste ouvert : il vient en déduction de l'encours client.
        boolean fullyCredited = credited.compareTo(originalTtc) == 0;
        boolean cancelled = fullyCredited && nz(original.getAmountPaid()).signum() == 0;
        creditNote.setStatus(cancelled ? InvoiceStatus.PAID : InvoiceStatus.ISSUED);
        creditNote = invoiceRepository.save(creditNote);

        if (cancelled) {
            original.setStatus(InvoiceStatus.CANCELLED);
            invoiceRepository.save(original);
            auditService.record(tenantId, "Invoice", original.getId(), "CANCEL",
                    Map.of("creditNoteId", creditNote.getId().toString(), "creditNoteNumber", creditNote.getNumber()));
        }

        auditService.record(tenantId, "Invoice", creditNote.getId(), "CREATE_CREDIT_NOTE",
                Map.of(
                        "creditedInvoiceId", original.getId().toString(),
                        "creditedInvoiceNumber", original.getNumber(),
                        "totalTtc", creditNote.getTotalTtc(),
                        "total", request.isTotal(),
                        "originalCancelled", cancelled,
                        "reason", request.reason().trim()));

        return invoiceService.toDetailedResponse(creditNote);
    }

    private static DocumentLineBuilder.LineInput negate(InvoiceLine line) {
        return new DocumentLineBuilder.LineInput(line.getProductId(), line.getDescription(),
                line.getQuantity().negate(), line.getUnitPriceHt(), line.getVatRate(), line.getDiscountPercent());
    }

    private static DocumentLineBuilder.LineInput negate(DocumentLineRequest line) {
        return new DocumentLineBuilder.LineInput(line.productId(), line.description(),
                line.quantity().negate(), line.unitPriceHt(), line.vatRate(), line.discountPercent());
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
