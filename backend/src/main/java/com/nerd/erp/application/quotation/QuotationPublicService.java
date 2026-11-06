package com.nerd.erp.application.quotation;

import com.nerd.erp.api.error.ApiException;
import com.nerd.erp.api.quotation.PublicQuotationDtos.AcceptRequest;
import com.nerd.erp.api.quotation.PublicQuotationDtos.PublicLine;
import com.nerd.erp.api.quotation.PublicQuotationDtos.PublicQuotationView;
import com.nerd.erp.api.quotation.PublicQuotationDtos.RejectRequest;
import com.nerd.erp.api.document.VatLineResponse;
import com.nerd.erp.application.audit.AuditService;
import com.nerd.erp.application.document.DocumentLineBuilder;
import com.nerd.erp.application.document.GeneratedDocument;
import com.nerd.erp.application.document.QuotationPdfService;
import com.nerd.erp.application.money.MoneyCalculator;
import com.nerd.erp.domain.customer.Customer;
import com.nerd.erp.domain.customer.CustomerType;
import com.nerd.erp.domain.quotation.Quotation;
import com.nerd.erp.domain.quotation.QuotationDecisionSource;
import com.nerd.erp.domain.quotation.QuotationStatus;
import com.nerd.erp.domain.workspace.Workspace;
import com.nerd.erp.infrastructure.persistence.customer.CustomerRepository;
import com.nerd.erp.infrastructure.persistence.quotation.QuotationRepository;
import com.nerd.erp.infrastructure.persistence.workspace.WorkspaceRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.Objects;

/**
 * Parcours client sans session : consultation, PDF, acceptation ou refus d'un devis via le lien
 * signé reçu par e-mail. Le jeton vaut autorisation ; aucune donnée interne (identifiants) n'est exposée.
 */
@Service
public class QuotationPublicService {

    public static final String LINK_INVALID = "LINK_INVALID";
    public static final String QUOTATION_NOT_DECIDABLE = "QUOTATION_NOT_DECIDABLE";

    private final QuotationPublicLinkService linkService;
    private final QuotationRepository quotationRepository;
    private final WorkspaceRepository workspaceRepository;
    private final CustomerRepository customerRepository;
    private final QuotationPdfService pdfService;
    private final AuditService auditService;

    public QuotationPublicService(QuotationPublicLinkService linkService,
                                  QuotationRepository quotationRepository,
                                  WorkspaceRepository workspaceRepository,
                                  CustomerRepository customerRepository,
                                  QuotationPdfService pdfService,
                                  AuditService auditService) {
        this.linkService = linkService;
        this.quotationRepository = quotationRepository;
        this.workspaceRepository = workspaceRepository;
        this.customerRepository = customerRepository;
        this.pdfService = pdfService;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public PublicQuotationView view(String token) {
        Quotation q = require(token);
        Workspace w = workspace(q);
        Customer c = customer(q);
        return toView(q, w, c);
    }

    @Transactional(readOnly = true)
    public GeneratedDocument pdf(String token) {
        Quotation q = require(token);
        return pdfService.generate(q, customer(q));
    }

    @Transactional
    public PublicQuotationView accept(String token, AcceptRequest request) {
        Quotation q = require(token);
        requireDecidable(q);
        QuotationService.recordDecision(q, QuotationStatus.ACCEPTED, request.name().trim(), request.comment(),
                QuotationDecisionSource.ONLINE);
        q = quotationRepository.save(q);
        auditService.record(q.getTenantId(), "Quotation", q.getId(), "ACCEPT",
                Map.of("source", "ONLINE", "by", request.name().trim()));
        return toView(q, workspace(q), customer(q));
    }

    @Transactional
    public PublicQuotationView reject(String token, RejectRequest request) {
        Quotation q = require(token);
        requireDecidable(q);
        QuotationService.recordDecision(q, QuotationStatus.REJECTED, request.name().trim(), request.reason(),
                QuotationDecisionSource.ONLINE);
        q = quotationRepository.save(q);
        auditService.record(q.getTenantId(), "Quotation", q.getId(), "REJECT",
                Map.of("source", "ONLINE", "by", request.name().trim(),
                        "reason", request.reason() == null ? "" : request.reason()));
        return toView(q, workspace(q), customer(q));
    }

    // --- helpers ---

    private Quotation require(String token) {
        return linkService.findByToken(token)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, LINK_INVALID,
                        "Ce lien n'est plus valide. Le devis a peut-être déjà fait l'objet d'une réponse."));
    }

    private static boolean expired(Quotation q) {
        return q.getValidUntil() != null && q.getValidUntil().isBefore(LocalDate.now());
    }

    private static void requireDecidable(Quotation q) {
        if (q.getStatus() != QuotationStatus.SENT) {
            throw new ApiException(HttpStatus.CONFLICT, QUOTATION_NOT_DECIDABLE,
                    "Ce devis a déjà fait l'objet d'une réponse.");
        }
        if (expired(q)) {
            throw new ApiException(HttpStatus.CONFLICT, QUOTATION_NOT_DECIDABLE,
                    "La date de validité de ce devis est dépassée. Contactez l'émetteur pour une mise à jour.");
        }
    }

    private Workspace workspace(Quotation q) {
        return workspaceRepository.findByIdAndTenantId(q.getWorkspaceId(), q.getTenantId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, LINK_INVALID, "Émetteur introuvable"));
    }

    private Customer customer(Quotation q) {
        return customerRepository.findByIdAndTenantId(q.getCustomerId(), q.getTenantId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, LINK_INVALID, "Client introuvable"));
    }

    private static PublicQuotationView toView(Quotation q, Workspace w, Customer c) {
        BigDecimal discount = nz(q.getDiscountAmountHt());
        BigDecimal totalHt = nz(q.getTotalHt());
        return new PublicQuotationView(
                q.getNumber(),
                q.getStatus(),
                q.getStatus() == QuotationStatus.SENT && !expired(q),
                expired(q),
                q.getIssueDate(),
                q.getValidUntil(),
                w.getName(),
                w.getContactEmail(),
                customerName(c),
                w.getCurrencyCode(),
                totalHt.add(discount),
                nz(q.getDiscountPercent()),
                discount,
                totalHt,
                nz(q.getTotalVat()),
                nz(q.getTotalTtc()),
                nz(q.getDepositPercent()),
                MoneyCalculator.percentOf(q.getTotalTtc(), q.getDepositPercent()),
                VatLineResponse.of(DocumentLineBuilder.vatBreakdown(q.getLines(), q.getDiscountPercent())),
                q.getNotes(),
                q.getDecidedAt(),
                q.getDecidedBy(),
                q.getLines().stream()
                        .map(l -> new PublicLine(l.getDescription(), l.getQuantity(), l.getUnitPriceHt(),
                                l.getVatRate(), nz(l.getDiscountPercent()), l.getLineTotalHt()))
                        .toList());
    }

    private static String customerName(Customer c) {
        String person = (Objects.toString(c.getFirstName(), "") + " " + Objects.toString(c.getLastName(), "")).trim();
        if (c.getCustomerType() == CustomerType.COMPANY && c.getCompanyName() != null && !c.getCompanyName().isBlank()) {
            return c.getCompanyName();
        }
        return person.isBlank() ? Objects.toString(c.getCompanyName(), "Client") : person;
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
