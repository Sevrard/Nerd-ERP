package com.nerd.erp.application.etsysync;

import com.fasterxml.jackson.databind.JsonNode;
import com.nerd.erp.api.error.ApiException;
import com.nerd.erp.api.etsysync.*;
import com.nerd.erp.application.audit.AuditService;
import com.nerd.erp.application.customer.CustomerService;
import com.nerd.erp.application.customer.CustomerSnapshotFactory;
import com.nerd.erp.application.document.DocumentNumberService;
import com.nerd.erp.application.money.MoneyCalculator;
import com.nerd.erp.application.workspace.WorkspaceService;
import com.nerd.erp.domain.catalog.Product;
import com.nerd.erp.domain.customer.Customer;
import com.nerd.erp.domain.document.DocumentSequenceType;
import com.nerd.erp.domain.invoice.Invoice;
import com.nerd.erp.domain.invoice.InvoiceLine;
import com.nerd.erp.domain.invoice.InvoiceStatus;
import com.nerd.erp.domain.invoice.InvoiceType;
import com.nerd.erp.domain.workspace.Workspace;
import com.nerd.erp.infrastructure.etsy.EtsyMoneyParser;
import com.nerd.erp.infrastructure.etsy.EtsyOpenApiClient;
import com.nerd.erp.infrastructure.persistence.catalog.ProductRepository;
import com.nerd.erp.infrastructure.persistence.invoice.InvoiceLineRepository;
import com.nerd.erp.infrastructure.persistence.invoice.InvoiceRepository;
import com.nerd.erp.infrastructure.persistence.invoice.projection.EtsyListingSalesRow;
import com.nerd.erp.infrastructure.security.core.SecurityContextHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class EtsySyncService {

    private static final Logger log = LoggerFactory.getLogger(EtsySyncService.class);
    private static final String NERD_FORGE = "nerd-forge";
    private static final String MERCHANT = "Etsy";
    private static final BigDecimal VAT = new BigDecimal("20.00");

    private final EtsyOpenApiClient etsyClient;
    private final EtsySyncSessionStore sessionStore;
    private final WorkspaceService workspaceService;
    private final ProductRepository productRepository;
    private final InvoiceRepository invoiceRepository;
    private final InvoiceLineRepository invoiceLineRepository;
    private final CustomerService customerService;
    private final DocumentNumberService documentNumberService;
    private final AuditService auditService;

    public EtsySyncService(EtsyOpenApiClient etsyClient, EtsySyncSessionStore sessionStore,
                             WorkspaceService workspaceService, ProductRepository productRepository,
                             InvoiceRepository invoiceRepository, InvoiceLineRepository invoiceLineRepository,
                             CustomerService customerService, DocumentNumberService documentNumberService,
                             AuditService auditService) {
        this.etsyClient = etsyClient;
        this.sessionStore = sessionStore;
        this.workspaceService = workspaceService;
        this.productRepository = productRepository;
        this.invoiceRepository = invoiceRepository;
        this.invoiceLineRepository = invoiceLineRepository;
        this.customerService = customerService;
        this.documentNumberService = documentNumberService;
        this.auditService = auditService;
    }

    public EtsySyncFetchResponse fetch(UUID workspaceId) {
        SecurityContextHelper.requireWriteAccess();
        UUID tenantId = SecurityContextHelper.requireTenantId();
        UUID userId = SecurityContextHelper.requireUser().userId();
        Workspace ws = requireNerdForge(workspaceId, tenantId);
        etsyClient.requireConfigured();
        List<EtsySyncProductDraft> products = mapListings(tenantId, ws.getId(), etsyClient.fetchAllActiveListings());
        List<EtsySyncReceiptDraft> receipts = mapReceipts(tenantId, ws.getId(), etsyClient.fetchAllReceipts());
        int emailsUpdated = customerService.backfillEmailsFromEtsyReceipts(ws.getId(), receipts);
        if (emailsUpdated > 0) {
            log.info("Etsy fetch : {} e-mail(s) client complété(s) depuis les receipts", emailsUpdated);
        }
        UUID sessionId = UUID.randomUUID();
        sessionStore.save(new EtsySyncSession(sessionId, tenantId, ws.getId(), userId, products, receipts,
                EtsySyncSessionStore.defaultExpiry()));
        return new EtsySyncFetchResponse(sessionId, products.size(), receipts.size(), products, receipts);
    }

    @Transactional
    public EtsySyncCommitResponse commit(UUID workspaceId, EtsySyncCommitRequest request) {
        SecurityContextHelper.requireWriteAccess();
        UUID tenantId = SecurityContextHelper.requireTenantId();
        Workspace ws = requireNerdForge(workspaceId, tenantId);
        EtsySyncSession session = sessionStore.require(request.sessionId(), tenantId);
        if (!session.workspaceId().equals(ws.getId())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Session liée à un autre workspace");
        }
        Map<Long, EtsySyncProductDraft> productDrafts = indexProducts(session.products());
        Map<Long, EtsySyncReceiptDraft> receiptDrafts = indexReceipts(session.receipts());
        Map<Long, UUID> listingToProduct = new HashMap<>();
        int created = 0, updated = 0, skippedProducts = 0;
        for (EtsySyncProductCommit c : request.products()) {
            if (!c.importProduct()) {
                skippedProducts++;
                EtsySyncProductDraft d = productDrafts.get(c.listingId());
                if (d != null && d.existingProductId() != null) {
                    listingToProduct.put(c.listingId(), d.existingProductId());
                }
                continue;
            }
            boolean existed = productDrafts.get(c.listingId()) != null && productDrafts.get(c.listingId()).alreadyInCatalog();
            listingToProduct.put(c.listingId(), upsertProduct(tenantId, ws.getId(), c).getId());
            if (existed) {
                updated++;
            } else {
                created++;
            }
        }
        session.products().forEach(d -> listingToProduct.putIfAbsent(d.listingId(), d.existingProductId()));
        int imported = 0, skippedReceipts = 0;
        for (EtsySyncReceiptCommit c : request.receipts()) {
            if (!c.importReceipt()) {
                skippedReceipts++;
                continue;
            }
            EtsySyncReceiptDraft d = receiptDrafts.get(c.receiptId());
            if (d == null || d.alreadyImported()) {
                skippedReceipts++;
                continue;
            }
            if (importReceipt(tenantId, ws, d, c, listingToProduct)) {
                imported++;
            }
        }
        sessionStore.remove(session.id());
        auditService.record(tenantId, "Workspace", ws.getId(), "ETSY_SYNC_COMMIT",
                Map.of("productsCreated", created, "receiptsImported", imported));
        return new EtsySyncCommitResponse(created, updated, skippedProducts, imported, skippedReceipts);
    }

    @Transactional(readOnly = true)
    public List<EtsyListingSalesStat> salesByListing(UUID workspaceId, LocalDate from, LocalDate to) {
        UUID tenantId = SecurityContextHelper.requireTenantId();
        requireNerdForge(workspaceId, tenantId);
        return invoiceLineRepository.aggregateSalesByListingInPeriod(tenantId, workspaceId, from, to).stream()
                .map(this::toStat).toList();
    }

    private EtsyListingSalesStat toStat(EtsyListingSalesRow r) {
        return new EtsyListingSalesStat(r.getEtsyListingId(), r.getProductId(), r.getProductName(),
                r.getProductReferenceCode(), r.getQuantitySold(), r.getRevenueHt(), r.getRevenueTtc(),
                r.getOrderCount() != null ? r.getOrderCount() : 0L, r.getLineCount() != null ? r.getLineCount() : 0L);
    }

    private boolean importReceipt(UUID tenantId, Workspace ws, EtsySyncReceiptDraft draft,
                                  EtsySyncReceiptCommit commit, Map<Long, UUID> listingToProduct) {
        if (invoiceRepository.findByTenantIdAndWorkspaceIdAndEtsyReceiptId(tenantId, ws.getId(), draft.receiptId()).isPresent()) {
            return false;
        }
        Customer customer = resolveCustomer(ws.getId(), draft, commit);
        Invoice inv = new Invoice();
        inv.setTenantId(tenantId);
        inv.setWorkspaceId(ws.getId());
        inv.setCustomerId(customer.getId());
        inv.setInvoiceType(InvoiceType.STANDARD);
        inv.setNumber(documentNumberService.nextNumber(tenantId, ws.getId(), DocumentSequenceType.INVOICE, ws.getInvoicePrefix()));
        inv.setIssueDate(draft.orderDate());
        inv.setDueDate(draft.orderDate());
        inv.setMerchant(MERCHANT);
        inv.setOrderNumber(String.valueOf(draft.receiptId()));
        inv.setEtsyReceiptId(draft.receiptId());
        inv.setExternalReference("ETSY-RECEIPT-" + draft.receiptId());
        inv.setSnapshotCustomer(CustomerSnapshotFactory.fromCustomer(customer));
        inv.setIssuedAt(Instant.now());
        inv.setNotes("Import Etsy receipt #" + draft.receiptNumber());
        List<InvoiceLine> lines = new ArrayList<>();
        BigDecimal ht = BigDecimal.ZERO, vat = BigDecimal.ZERO, ttc = BigDecimal.ZERO;
        int order = 0;
        for (EtsySyncTransactionDraft tx : draft.transactions()) {
            BigDecimal qty = tx.quantity() != null ? tx.quantity() : BigDecimal.ONE;
            MoneyCalculator.LineTotals t = MoneyCalculator.computeLine(qty, tx.unitPriceHt(), tx.vatRate());
            InvoiceLine line = new InvoiceLine();
            line.setTenantId(tenantId);
            line.setLineOrder(order++);
            line.setDescription(tx.title());
            line.setQuantity(qty);
            line.setUnitPriceHt(tx.unitPriceHt());
            line.setVatRate(tx.vatRate());
            line.setLineTotalHt(t.totalHt());
            line.setLineTotalVat(t.totalVat());
            line.setLineTotalTtc(t.totalTtc());
            line.setEtsyListingId(tx.listingId());
            line.setEtsyTransactionId(tx.transactionId());
            line.setProductId(resolveProductId(tenantId, ws.getId(), tx.listingId(), listingToProduct));
            lines.add(line);
            ht = ht.add(t.totalHt());
            vat = vat.add(t.totalVat());
            ttc = ttc.add(t.totalTtc());
        }
        inv.setTotalHt(ht);
        inv.setTotalVat(vat);
        inv.setTotalTtc(ttc);
        if (draft.paid()) {
            inv.setStatus(InvoiceStatus.PAID);
            inv.setAmountPaid(ttc);
        } else {
            inv.setStatus(InvoiceStatus.ISSUED);
            inv.setAmountPaid(BigDecimal.ZERO);
        }
        inv.replaceLines(lines);
        invoiceRepository.save(inv);
        return true;
    }

    private Customer resolveCustomer(UUID workspaceId, EtsySyncReceiptDraft draft, EtsySyncReceiptCommit commit) {
        if (commit.customerId() != null) {
            return customerService.requireCustomer(commit.customerId());
        }
        if (commit.createCustomerFromBuyer()) {
            String email = draft.buyerEmail();
            if ((email == null || email.isBlank()) && draft.buyerUserId() > 0) {
                email = etsyClient.fetchUserPrimaryEmail(draft.buyerUserId()).orElse(null);
            }
            return customerService.resolveFromEtsyImport(
                    workspaceId,
                    draft.buyerName(),
                    email,
                    draft.addressLine1(),
                    draft.addressLine2(),
                    draft.postalCode(),
                    draft.city(),
                    draft.countryCode());
        }
        throw new ApiException(HttpStatus.BAD_REQUEST, "Client requis pour receipt " + draft.receiptId());
    }

    private UUID resolveProductId(UUID tenantId, UUID workspaceId, long listingId, Map<Long, UUID> listingToProduct) {
        UUID fromSession = listingToProduct.get(listingId);
        if (fromSession != null) {
            return fromSession;
        }
        return productRepository.findByTenantIdAndWorkspaceIdAndEtsyListingId(tenantId, workspaceId, listingId)
                .map(Product::getId)
                .orElse(null);
    }

    private Product upsertProduct(UUID tenantId, UUID workspaceId, EtsySyncProductCommit c) {
        Product p = productRepository.findByTenantIdAndWorkspaceIdAndEtsyListingId(tenantId, workspaceId, c.listingId())
                .orElseGet(Product::new);
        if (p.getId() == null) {
            p.setTenantId(tenantId);
            p.setWorkspaceId(workspaceId);
        }
        p.setEtsyListingId(c.listingId());
        p.setReferenceCode(c.referenceCode());
        p.setName(c.name());
        p.setDescription(c.description());
        p.setUnitPriceHt(c.unitPriceHt());
        p.setVatRate(c.vatRate());
        p.setActive(c.active());
        return productRepository.save(p);
    }

    private List<EtsySyncProductDraft> mapListings(UUID tenantId, UUID workspaceId, List<JsonNode> nodes) {
        List<EtsySyncProductDraft> out = new ArrayList<>();
        for (JsonNode n : nodes) {
            long id = n.path("listing_id").asLong();
            if (id <= 0) {
                continue;
            }
            BigDecimal ttc = EtsyMoneyParser.toMajorUnits(n.get("price"));
            BigDecimal ht = ttc.divide(new BigDecimal("1.20"), 4, RoundingMode.HALF_UP);
            var ex = productRepository.findByTenantIdAndWorkspaceIdAndEtsyListingId(tenantId, workspaceId, id);
            out.add(new EtsySyncProductDraft(id, "ETSY-" + id, text(n, "title"), text(n, "description"), ht, VAT,
                    ex.isPresent(), ex.map(Product::getId).orElse(null)));
        }
        return out;
    }

    private List<EtsySyncReceiptDraft> mapReceipts(UUID tenantId, UUID workspaceId, List<JsonNode> nodes) {
        List<EtsySyncReceiptDraft> out = new ArrayList<>();
        Map<Long, String> buyerEmails = new HashMap<>();
        for (JsonNode r : nodes) {
            long receiptId = r.path("receipt_id").asLong();
            if (receiptId <= 0) {
                continue;
            }
            List<EtsySyncTransactionDraft> txs = new ArrayList<>();
            for (JsonNode tx : etsyClient.fetchReceiptTransactions(receiptId)) {
                BigDecimal qty = BigDecimal.valueOf(tx.path("quantity").asInt(1));
                BigDecimal unitTtc = EtsyMoneyParser.toMajorUnits(tx.get("price"));
                BigDecimal unitHt = unitTtc.divide(new BigDecimal("1.20"), 4, RoundingMode.HALF_UP);
                txs.add(new EtsySyncTransactionDraft(tx.path("transaction_id").asLong(), tx.path("listing_id").asLong(),
                        text(tx, "title"), qty, unitHt, VAT, unitTtc.multiply(qty).setScale(4, RoundingMode.HALF_UP)));
            }
            LocalDate date = Instant.ofEpochSecond(r.path("created_timestamp").asLong()).atZone(ZoneOffset.UTC).toLocalDate();
            var ex = invoiceRepository.findByTenantIdAndWorkspaceIdAndEtsyReceiptId(tenantId, workspaceId, receiptId);
            long buyerUserId = r.path("buyer_user_id").asLong(0);
            String buyerName = formatPersonName(text(r, "name"));
            String buyerEmail = resolveBuyerEmail(r, buyerUserId, buyerEmails);
            String addressLine1 = text(r, "first_line");
            String addressLine2 = text(r, "second_line");
            String postalCode = text(r, "zip");
            String city = text(r, "city");
            String countryCode = text(r, "country_iso");
            out.add(new EtsySyncReceiptDraft(
                    receiptId,
                    r.path("receipt_number").asLong(receiptId),
                    date,
                    buyerUserId,
                    buyerName,
                    buyerEmail,
                    addressLine1,
                    addressLine2,
                    postalCode,
                    city,
                    countryCode,
                    EtsyMoneyParser.toMajorUnits(r.get("grandtotal")),
                    r.path("is_paid").asBoolean(false),
                    ex.isPresent(),
                    ex.map(Invoice::getId).orElse(null),
                    txs));
        }
        return out;
    }

    private String resolveBuyerEmail(JsonNode receipt, long buyerUserId, Map<Long, String> cache) {
        String fromReceipt = extractReceiptEmail(receipt);
        if (fromReceipt != null) {
            return fromReceipt;
        }
        if (buyerUserId <= 0) {
            return null;
        }
        if (cache.containsKey(buyerUserId)) {
            return cache.get(buyerUserId);
        }
        String email = etsyClient.fetchUserPrimaryEmail(buyerUserId).orElse(null);
        if (email == null) {
            log.debug("Receipt {} : pas d'e-mail (buyer_user_id={})", receipt.path("receipt_id").asLong(), buyerUserId);
        }
        cache.put(buyerUserId, email);
        return email;
    }

    private static String extractReceiptEmail(JsonNode receipt) {
        String buyer = text(receipt, "buyer_email");
        if (looksLikeEmail(buyer)) {
            return buyer.trim();
        }
        String payment = text(receipt, "payment_email");
        if (looksLikeEmail(payment)) {
            return payment.trim();
        }
        return null;
    }

    private static boolean looksLikeEmail(String value) {
        return value != null && value.contains("@") && value.indexOf('@') < value.length() - 1;
    }

    private Workspace requireNerdForge(UUID workspaceId, UUID tenantId) {
        Workspace ws = workspaceService.requireInTenant(workspaceId, tenantId);
        if (!NERD_FORGE.equals(ws.getSlug())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Réservé au workspace Nerd-Forge");
        }
        return ws;
    }

    private static Map<Long, EtsySyncProductDraft> indexProducts(List<EtsySyncProductDraft> list) {
        Map<Long, EtsySyncProductDraft> m = new HashMap<>();
        list.forEach(p -> m.put(p.listingId(), p));
        return m;
    }

    private static Map<Long, EtsySyncReceiptDraft> indexReceipts(List<EtsySyncReceiptDraft> list) {
        Map<Long, EtsySyncReceiptDraft> m = new HashMap<>();
        list.forEach(r -> m.put(r.receiptId(), r));
        return m;
    }

    private static String text(JsonNode n, String f) {
        JsonNode v = n.get(f);
        return v == null || v.isNull() ? "" : v.asText("").trim();
    }

    /** Affichage unifié prénom + nom (casse titre, espaces normalisés). */
    static String formatPersonName(String raw) {
        String collapsed = collapseSpaces(raw);
        if (collapsed.isBlank()) {
            return collapsed;
        }
        return Arrays.stream(collapsed.split(" "))
                .filter(w -> !w.isBlank())
                .map(EtsySyncService::titleCaseWord)
                .collect(Collectors.joining(" "));
    }

    private static String collapseSpaces(String s) {
        if (s == null) {
            return "";
        }
        return s.trim().replaceAll("\\s+", " ");
    }

    private static String titleCaseWord(String word) {
        if (word == null || word.isBlank()) {
            return word == null ? "" : word;
        }
        return Arrays.stream(word.split("-"))
                .map(part -> {
                    String lower = part.toLowerCase(Locale.FRENCH);
                    if (lower.isEmpty()) {
                        return part;
                    }
                    return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
                })
                .collect(Collectors.joining("-"));
    }

}
