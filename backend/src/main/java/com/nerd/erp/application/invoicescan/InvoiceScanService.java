package com.nerd.erp.application.invoicescan;

import com.nerd.erp.api.customer.CustomerRequest;
import com.nerd.erp.api.error.ApiException;
import com.nerd.erp.api.invoice.InvoiceResponse;
import com.nerd.erp.api.invoicescan.InvoiceScanBatchPreviewResponse;
import com.nerd.erp.api.invoicescan.InvoiceScanCommitRequest;
import com.nerd.erp.api.invoicescan.InvoiceScanDuplicateInfo;
import com.nerd.erp.api.invoicescan.InvoiceScanPreviewResponse;
import com.nerd.erp.infrastructure.pdf.PdfDocumentSplitter;
import com.nerd.erp.infrastructure.storage.InvoiceDocumentStorage;
import com.nerd.erp.api.payment.PaymentRequest;
import com.nerd.erp.application.payment.PaymentService;
import com.nerd.erp.domain.payment.PaymentMethod;
import com.nerd.erp.application.merchant.MerchantScanContext;
import com.nerd.erp.application.merchant.MerchantScanContextResolver;
import com.nerd.erp.application.audit.AuditService;
import com.nerd.erp.application.catalog.ImportCatalogProductService;
import com.nerd.erp.application.customer.CustomerService;
import com.nerd.erp.application.customer.CustomerSnapshotFactory;
import com.nerd.erp.application.document.DocumentLineBuilder;
import com.nerd.erp.application.document.DocumentNumberService;
import com.nerd.erp.application.invoice.InvoiceMapper;
import com.nerd.erp.application.money.MoneyCalculator;
import com.nerd.erp.application.workspace.WorkspaceService;
import com.nerd.erp.application.workspace.WorkspaceShopSyncPolicy;
import com.nerd.erp.domain.customer.Customer;
import com.nerd.erp.domain.customer.CustomerStatus;
import com.nerd.erp.domain.customer.CustomerType;
import com.nerd.erp.domain.document.DocumentSequenceType;
import com.nerd.erp.domain.invoice.Invoice;
import com.nerd.erp.domain.invoice.InvoiceLine;
import com.nerd.erp.domain.invoice.InvoiceStatus;
import com.nerd.erp.domain.invoice.InvoiceType;
import com.nerd.erp.domain.workspace.Workspace;
import com.nerd.erp.infrastructure.gemini.GeminiInvoiceExtractor;
import com.nerd.erp.infrastructure.persistence.invoice.InvoiceRepository;
import com.nerd.erp.infrastructure.security.core.SecurityContextHelper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class InvoiceScanService {

    private static final long MAX_PDF_BYTES = 10 * 1024 * 1024;
    private final GeminiInvoiceExtractor geminiInvoiceExtractor;
    private final CustomerMatcher customerMatcher;
    private final InvoiceScanSessionStore sessionStore;
    private final CustomerService customerService;
    private final WorkspaceService workspaceService;
    private final InvoiceRepository invoiceRepository;
    private final DocumentNumberService documentNumberService;
    private final InvoiceMapper invoiceMapper;
    private final AuditService auditService;
    private final InvoiceDuplicateFinder duplicateFinder;
    private final PaymentService paymentService;
    private final PdfDocumentSplitter pdfDocumentSplitter;
    private final MerchantScanContextResolver merchantScanContextResolver;
    private final InvoiceDocumentStorage invoiceDocumentStorage;
    private final ImportCatalogProductService importCatalogProductService;

    public InvoiceScanService(GeminiInvoiceExtractor geminiInvoiceExtractor,
                              CustomerMatcher customerMatcher,
                              InvoiceScanSessionStore sessionStore,
                              CustomerService customerService,
                              WorkspaceService workspaceService,
                              InvoiceRepository invoiceRepository,
                              DocumentNumberService documentNumberService,
                              InvoiceMapper invoiceMapper,
                              AuditService auditService,
                              InvoiceDuplicateFinder duplicateFinder,
                              PaymentService paymentService,
                              PdfDocumentSplitter pdfDocumentSplitter,
                              MerchantScanContextResolver merchantScanContextResolver,
                              InvoiceDocumentStorage invoiceDocumentStorage,
                              ImportCatalogProductService importCatalogProductService) {
        this.geminiInvoiceExtractor = geminiInvoiceExtractor;
        this.customerMatcher = customerMatcher;
        this.sessionStore = sessionStore;
        this.customerService = customerService;
        this.workspaceService = workspaceService;
        this.invoiceRepository = invoiceRepository;
        this.documentNumberService = documentNumberService;
        this.invoiceMapper = invoiceMapper;
        this.auditService = auditService;
        this.duplicateFinder = duplicateFinder;
        this.paymentService = paymentService;
        this.pdfDocumentSplitter = pdfDocumentSplitter;
        this.merchantScanContextResolver = merchantScanContextResolver;
        this.invoiceDocumentStorage = invoiceDocumentStorage;
        this.importCatalogProductService = importCatalogProductService;
    }

    public InvoiceScanBatchPreviewResponse analyze(UUID workspaceId, MultipartFile file, UUID merchantId) {
        SecurityContextHelper.requireWriteAccess();
        UUID tenantId = SecurityContextHelper.requireTenantId();
        UUID userId = SecurityContextHelper.requireUser().userId();
        Workspace workspace = workspaceService.requireInTenant(workspaceId, tenantId);
        WorkspaceShopSyncPolicy.ensurePdfImportAllowed(workspace);
        MerchantScanContext merchantContext = merchantScanContextResolver.resolve(tenantId, workspaceId, merchantId);

        byte[] bytes = readPdf(file);
        List<byte[]> pages = pdfDocumentSplitter.splitIntoPages(bytes);
        List<InvoiceScanBatchPreviewResponse.InvoiceScanPageResult> results = new ArrayList<>();

        for (int i = 0; i < pages.size(); i++) {
            int pageNumber = i + 1;
            try {
                InvoiceScanPreviewResponse preview = analyzePage(
                        tenantId, workspaceId, userId, pages.get(i), merchantContext);
                results.add(InvoiceScanBatchPreviewResponse.InvoiceScanPageResult.success(pageNumber, preview));
            } catch (ApiException e) {
                results.add(InvoiceScanBatchPreviewResponse.InvoiceScanPageResult.failure(
                        pageNumber, e.getMessage()));
            } catch (Exception e) {
                results.add(InvoiceScanBatchPreviewResponse.InvoiceScanPageResult.failure(
                        pageNumber, "Échec de l'analyse de la page " + pageNumber));
            }
        }

        return new InvoiceScanBatchPreviewResponse(pages.size(), results);
    }

    private InvoiceScanPreviewResponse analyzePage(UUID tenantId,
                                                 UUID workspaceId,
                                                 UUID userId,
                                                 byte[] pagePdf,
                                                 MerchantScanContext merchantContext) {
        String prompt = merchantContext.hasCustomPrompt() ? merchantContext.extractionPrompt() : null;
        ExtractedInvoiceData extraction = geminiInvoiceExtractor.extract(pagePdf, prompt);
        validateExtraction(extraction);

        CustomerMatcher.MatchResult match = customerMatcher.match(tenantId, workspaceId, extraction.customer());
        InvoiceScanSession session = sessionStore.create(
                tenantId, workspaceId, userId, extraction,
                match.status(), match.customerId(), pagePdf);

        String importMerchant = merchantContext.merchantName() != null
                ? merchantContext.merchantName()
                : resolveImportMerchant(null, extraction);
        InvoiceScanDuplicateInfo duplicate = detectDuplicate(tenantId, workspaceId, extraction, importMerchant);
        return InvoiceScanPreviewResponse.from(session, duplicate);
    }

    @Transactional
    public InvoiceResponse commit(InvoiceScanCommitRequest request) {
        SecurityContextHelper.requireWriteAccess();
        UUID tenantId = SecurityContextHelper.requireTenantId();
        Workspace workspace = workspaceService.requireInTenant(request.workspaceId(), tenantId);
        WorkspaceShopSyncPolicy.ensurePdfImportAllowed(workspace);

        if (!request.invoiceConfirmed()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Confirmation de la facture requise");
        }

        InvoiceScanSession session = sessionStore.require(request.scanId(), tenantId);
        if (!session.workspaceId().equals(request.workspaceId())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Workspace incompatible avec la session");
        }

        ExtractedInvoiceData data = mergeExtraction(session.extraction(), request);
        String importMerchant = resolveImportMerchant(request.merchant(), data);
        InvoiceScanDuplicateInfo duplicate = detectDuplicate(
                tenantId, request.workspaceId(), data, importMerchant);
        if (duplicate.detected()) {
            if (!request.recordDuplicateAsPayment()) {
                throw new ApiException(HttpStatus.CONFLICT,
                        "Facture en doublon (réf. " + duplicate.externalReference()
                                + ") — confirmez l’enregistrement en paiement");
            }
            if (!request.duplicatePaymentConfirmed()) {
                throw new ApiException(HttpStatus.CONFLICT,
                        "Confirmation requise pour enregistrer le montant en paiement");
            }
            return recordDuplicatePayment(request, session, duplicate);
        }
        if (request.recordDuplicateAsPayment()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Aucun doublon détecté pour ce document");
        }

        UUID customerId = resolveCustomerId(request, session, tenantId);

        Customer customer = customerService.requireCustomer(customerId);

        Invoice invoice = new Invoice();
        invoice.setTenantId(tenantId);
        invoice.setWorkspaceId(request.workspaceId());
        invoice.setCustomerId(customerId);
        invoice.setInvoiceType(InvoiceType.STANDARD);
        invoice.setIssueDate(parseDate(data.issueDate()));
        invoice.setDueDate(invoice.getIssueDate().plusDays(30));
        invoice.setSnapshotCustomer(CustomerSnapshotFactory.fromCustomer(customer));
        invoice.setNotes(buildNotes(data, request.notes()));
        applyDocumentReferences(invoice, data, importMerchant);

        List<DocumentLineBuilder.LineInput> inputs = buildLineInputsForCommit(tenantId, data, request.totalTtc());
        if (inputs.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Aucune ligne exploitable dans le scan");
        }
        inputs = importCatalogProductService.attachCatalogProducts(tenantId, request.workspaceId(), inputs);
        List<InvoiceLine> lines = DocumentLineBuilder.buildInvoiceLines(tenantId, inputs);
        invoice.replaceLines(lines);
        MoneyCalculator.DocumentTotals totals = DocumentLineBuilder.totalsFromInvoiceLines(lines);
        invoice.setTotalHt(totals.totalHt());
        invoice.setTotalVat(totals.totalVat());
        invoice.setTotalTtc(totals.totalTtc());
        invoice.setAmountPaid(totals.totalTtc());

        String number = documentNumberService.nextNumber(tenantId, workspace.getId(),
                DocumentSequenceType.INVOICE, workspace.getInvoicePrefix());
        invoice.setNumber(number);
        invoice.setStatus(InvoiceStatus.PAID);
        invoice.setIssuedAt(Instant.now());

        invoice = invoiceRepository.save(invoice);
        persistSourcePdf(session, invoice);
        invoice = invoiceRepository.save(invoice);
        sessionStore.remove(session.id());

        auditService.record(tenantId, "Invoice", invoice.getId(), "IMPORT_FROM_PDF_SCAN",
                Map.of(
                        "scanId", session.id().toString(),
                        "merchant", importMerchant,
                        "orderNumber", invoice.getOrderNumber() != null ? invoice.getOrderNumber() : "",
                        "externalRef", invoice.getExternalReference() != null ? invoice.getExternalReference() : ""));

        return invoiceMapper.toResponse(invoice);
    }

    private InvoiceResponse recordDuplicatePayment(InvoiceScanCommitRequest request,
                                                    InvoiceScanSession session,
                                                    InvoiceScanDuplicateInfo duplicate) {
        UUID tenantId = SecurityContextHelper.requireTenantId();
        Invoice existing = invoiceRepository.findByIdAndTenantId(duplicate.existingInvoiceId(), tenantId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Facture existante introuvable"));

        ExtractedInvoiceData data = mergeExtraction(session.extraction(), request);
        BigDecimal scannedTtc = duplicate.scannedAmountTtc();
        if (request.totalTtc() != null && request.totalTtc().signum() > 0) {
            scannedTtc = request.totalTtc();
        } else if (scannedTtc == null || scannedTtc.signum() <= 0) {
            scannedTtc = resolveScannedTotalTtc(tenantId, data);
        }
        if (scannedTtc == null || scannedTtc.signum() <= 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Montant TTC du scan invalide");
        }
        BigDecimal remaining = existing.getTotalTtc().subtract(existing.getAmountPaid());
        if (remaining.signum() <= 0) {
            throw new ApiException(HttpStatus.CONFLICT, "Cette facture est déjà entièrement payée");
        }
        BigDecimal paymentAmount = scannedTtc.min(remaining);

        String externalRef = duplicate.externalReference();
        PaymentRequest paymentRequest = new PaymentRequest(
                request.workspaceId(),
                existing.getCustomerId(),
                paymentAmount,
                parseDate(data.issueDate()),
                PaymentMethod.TRANSFER,
                externalRef,
                "Scan PDF — doublon réf. " + externalRef,
                List.of(new PaymentRequest.AllocationRequest(existing.getId(), paymentAmount)));
        paymentService.record(paymentRequest);
        sessionStore.remove(session.id());

        Invoice updated = invoiceRepository.findByIdAndTenantId(existing.getId(), tenantId)
                .orElseThrow();
        auditService.record(tenantId, "Invoice", updated.getId(), "SCAN_DUPLICATE_AS_PAYMENT",
                Map.of("externalRef", externalRef, "paymentAmount", paymentAmount.toPlainString()));
        return invoiceMapper.toResponse(updated);
    }

    private InvoiceScanDuplicateInfo detectDuplicate(UUID tenantId,
                                                     UUID workspaceId,
                                                     ExtractedInvoiceData data,
                                                     String importMerchant) {
        BigDecimal scannedTtc = resolveScannedTotalTtc(tenantId, data);
        String orderNumber = trimToNull(data.orderNumber());
        if (orderNumber != null) {
            var byOrder = duplicateFinder.findByOrderNumber(tenantId, workspaceId, importMerchant, orderNumber);
            if (byOrder.isPresent()) {
                return InvoiceScanDuplicateInfo.of(byOrder.get(), orderNumber, scannedTtc);
            }
        }
        String ref = InvoiceExternalReference.fromExtraction(data);
        if (ref == null) {
            return InvoiceScanDuplicateInfo.none();
        }
        return duplicateFinder.findByDocumentReference(tenantId, workspaceId, ref)
                .map(inv -> InvoiceScanDuplicateInfo.of(inv, ref, scannedTtc))
                .orElse(InvoiceScanDuplicateInfo.none());
    }

    private static void applyDocumentReferences(Invoice invoice, ExtractedInvoiceData data, String merchant) {
        invoice.setMerchant(trimToNull(merchant));
        invoice.setOrderNumber(trimToNull(data.orderNumber()));
        invoice.setExternalReference(InvoiceExternalReference.fromExtraction(data));
    }

    private static String resolveImportMerchant(String requestMerchant, ExtractedInvoiceData data) {
        if (requestMerchant != null && !requestMerchant.isBlank()) {
            return requestMerchant.trim();
        }
        if (data == null) {
            return null;
        }
        return trimToNull(data.merchant());
    }

    private static String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private UUID resolveCustomerId(InvoiceScanCommitRequest request,
                                   InvoiceScanSession session,
                                   UUID tenantId) {
        if (request.customerId() != null) {
            Customer selected = customerService.requireCustomer(request.customerId());
            workspaceService.requireInTenant(selected.getWorkspaceId(), tenantId);
            if (!selected.getWorkspaceId().equals(session.workspaceId())) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "Client hors du workspace");
            }
            return selected.getId();
        }
        UUID customerId = session.matchedCustomerId();
        if (session.customerMatchStatus() == CustomerMatchStatus.NOT_FOUND) {
            if (!request.createCustomerConfirmed()) {
                throw new ApiException(HttpStatus.CONFLICT,
                        "Client inconnu : sélectionnez un client ou confirmez la création");
            }
            Customer created = createCustomerFromExtraction(request.workspaceId(), session.extraction().customer());
            return created.getId();
        }
        if (customerId == null) {
            throw new ApiException(HttpStatus.CONFLICT, "Client introuvable pour cette session");
        }
        return customerId;
    }

    private static ExtractedInvoiceData mergeExtraction(ExtractedInvoiceData data, InvoiceScanCommitRequest request) {
        String orderNumber = trimToNull(request.orderNumber());
        if (orderNumber == null) {
            orderNumber = data.orderNumber();
        }
        String invoiceNumber = trimToNull(request.invoiceNumber());
        if (invoiceNumber == null) {
            invoiceNumber = data.invoiceNumber();
        }
        String issueDate = data.issueDate();
        if (request.issueDate() != null) {
            issueDate = request.issueDate().toString();
        }
        BigDecimal totalTtc = data.totalTtc();
        if (request.totalTtc() != null && request.totalTtc().signum() > 0) {
            totalTtc = request.totalTtc();
        }
        String merchant = trimToNull(request.merchant());
        if (merchant == null) {
            merchant = data.merchant();
        }
        return new ExtractedInvoiceData(
                invoiceNumber,
                orderNumber,
                merchant,
                issueDate,
                data.customer(),
                data.lines(),
                data.shippingAmountHt(),
                data.shippingVatRate(),
                data.totalHt(),
                data.totalVat(),
                totalTtc,
                data.currency());
    }

    private List<DocumentLineBuilder.LineInput> buildLineInputsForCommit(UUID tenantId,
                                                                         ExtractedInvoiceData data,
                                                                         BigDecimal totalTtcOverride) {
        List<DocumentLineBuilder.LineInput> inputs = buildLineInputs(data);
        if (inputs.isEmpty()) {
            return inputs;
        }
        if (totalTtcOverride == null || totalTtcOverride.signum() <= 0) {
            return inputs;
        }
        List<InvoiceLine> lines = DocumentLineBuilder.buildInvoiceLines(tenantId, inputs);
        BigDecimal currentTtc = DocumentLineBuilder.totalsFromInvoiceLines(lines).totalTtc();
        if (currentTtc.signum() <= 0) {
            return inputs;
        }
        if (currentTtc.subtract(totalTtcOverride).abs().compareTo(new BigDecimal("0.02")) <= 0) {
            return inputs;
        }
        BigDecimal ratio = totalTtcOverride.divide(currentTtc, 12, RoundingMode.HALF_UP);
        List<DocumentLineBuilder.LineInput> scaled = new ArrayList<>();
        for (DocumentLineBuilder.LineInput input : inputs) {
            scaled.add(new DocumentLineBuilder.LineInput(
                    input.productId(),
                    input.description(),
                    input.quantity(),
                    input.unitPriceHt().multiply(ratio).setScale(4, RoundingMode.HALF_UP),
                    input.vatRate()));
        }
        return scaled;
    }

    private BigDecimal resolveScannedTotalTtc(UUID tenantId, ExtractedInvoiceData data) {
        if (data.totalTtc() != null && data.totalTtc().signum() > 0) {
            return data.totalTtc();
        }
        List<DocumentLineBuilder.LineInput> inputs = buildLineInputs(data);
        if (inputs.isEmpty()) {
            return BigDecimal.ZERO;
        }
        List<InvoiceLine> lines = DocumentLineBuilder.buildInvoiceLines(tenantId, inputs);
        return DocumentLineBuilder.totalsFromInvoiceLines(lines).totalTtc();
    }

    private static byte[] readPdf(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Fichier PDF requis");
        }
        if (!"application/pdf".equalsIgnoreCase(file.getContentType())
                && (file.getOriginalFilename() == null || !file.getOriginalFilename().toLowerCase().endsWith(".pdf"))) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Seuls les fichiers PDF sont acceptés");
        }
        try {
            byte[] bytes = file.getBytes();
            if (bytes.length > MAX_PDF_BYTES) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "PDF trop volumineux (max 10 Mo)");
            }
            return bytes;
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Impossible de lire le PDF");
        }
    }

    private static void validateExtraction(ExtractedInvoiceData data) {
        if (data.lines() == null || data.lines().isEmpty()) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Aucune ligne d'article détectée dans le document");
        }
    }

    private Customer createCustomerFromExtraction(UUID workspaceId, ExtractedInvoiceData.ExtractedCustomer extracted) {
        CustomerType type = "INDIVIDUAL".equalsIgnoreCase(extracted.customerType())
                ? CustomerType.INDIVIDUAL
                : CustomerType.COMPANY;
        CustomerRequest req = new CustomerRequest(
                workspaceId,
                type,
                extracted.companyName(),
                extracted.firstName(),
                extracted.lastName(),
                extracted.email(),
                extracted.phone(),
                extracted.addressLine1(),
                extracted.addressLine2(),
                extracted.postalCode(),
                extracted.city(),
                extracted.countryCode() != null ? extracted.countryCode() : "FR",
                CustomerStatus.CLIENT,
                "Créé via scan PDF facture",
                null);
        return customerService.createAndReturnEntity(req);
    }

    private static List<DocumentLineBuilder.LineInput> buildLineInputs(ExtractedInvoiceData data) {
        List<DocumentLineBuilder.LineInput> inputs = new ArrayList<>();
        for (ExtractedInvoiceData.ExtractedLine line : data.lines()) {
            if (line.description() == null || line.description().isBlank()) {
                continue;
            }
            BigDecimal qty = line.quantity() != null && line.quantity().signum() > 0
                    ? line.quantity() : BigDecimal.ONE;
            inputs.add(new DocumentLineBuilder.LineInput(
                    null,
                    line.description().trim(),
                    qty,
                    line.unitPriceHt() != null ? line.unitPriceHt() : BigDecimal.ZERO,
                    normalizeVatPercent(line.vatRate())));
        }
        if (data.shippingAmountHt() != null && data.shippingAmountHt().signum() > 0) {
            BigDecimal shippingVat = normalizeVatPercent(data.shippingVatRate());
            inputs.add(new DocumentLineBuilder.LineInput(
                    null, "Frais de port", BigDecimal.ONE, data.shippingAmountHt(), shippingVat));
        }
        return inputs;
    }

    private static LocalDate parseDate(String iso) {
        if (iso == null || iso.isBlank()) {
            return LocalDate.now();
        }
        try {
            return LocalDate.parse(iso.trim());
        } catch (Exception e) {
            return LocalDate.now();
        }
    }

    /** ERP attend un taux en % (ex. 20), Gemini peut renvoyer 0.20 ou 20. */
    private static BigDecimal normalizeVatPercent(BigDecimal rate) {
        if (rate == null) {
            return new BigDecimal("20");
        }
        if (rate.compareTo(BigDecimal.ONE) <= 0) {
            return rate.multiply(new BigDecimal("100"));
        }
        return rate;
    }

    private static String buildNotes(ExtractedInvoiceData data, String userNotes) {
        StringBuilder sb = new StringBuilder("Import scan PDF");
        if (data.invoiceNumber() != null) {
            sb.append(" — Réf. ").append(data.invoiceNumber());
        }
        if (data.orderNumber() != null) {
            sb.append(" — Commande ").append(data.orderNumber());
        }
        if (userNotes != null && !userNotes.isBlank()) {
            sb.append("\n").append(userNotes.trim());
        }
        return sb.toString();
    }

    private void persistSourcePdf(InvoiceScanSession session, Invoice invoice) {
        byte[] pdf = session.sourcePagePdf();
        if (pdf == null || pdf.length == 0) {
            return;
        }
        invoice.setSourceDocumentPath(invoiceDocumentStorage.storeSourcePdf(invoice, pdf));
    }
}
