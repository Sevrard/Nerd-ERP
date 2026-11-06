package com.nerd.erp.application.onboarding;

import com.nerd.erp.application.account.AccountService;
import com.nerd.erp.application.mail.MailService;
import com.nerd.erp.application.workspace.WorkspaceService;
import com.nerd.erp.domain.workspace.Workspace;
import com.nerd.erp.infrastructure.persistence.catalog.ProductRepository;
import com.nerd.erp.infrastructure.persistence.customer.CustomerRepository;
import com.nerd.erp.infrastructure.persistence.invoice.InvoiceRepository;
import com.nerd.erp.infrastructure.persistence.quotation.QuotationRepository;
import com.nerd.erp.infrastructure.security.core.SecurityContextHelper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Checklist de démarrage d'un workspace : chaque étape est évaluée sur les données réelles (pas de flag
 * « vu » à stocker) ; le frontend affiche l'avancement et les liens d'action.
 */
@Service
public class OnboardingService {

    public record Step(String code, String label, String description, boolean done, String route) {
    }

    public record Checklist(UUID workspaceId, int done, int total, List<Step> steps) {
    }

    private final WorkspaceService workspaceService;
    private final MailService mailService;
    private final CustomerRepository customerRepository;
    private final ProductRepository productRepository;
    private final QuotationRepository quotationRepository;
    private final InvoiceRepository invoiceRepository;
    private final AccountService accountService;

    public OnboardingService(WorkspaceService workspaceService,
                             MailService mailService,
                             CustomerRepository customerRepository,
                             ProductRepository productRepository,
                             QuotationRepository quotationRepository,
                             InvoiceRepository invoiceRepository,
                             AccountService accountService) {
        this.workspaceService = workspaceService;
        this.mailService = mailService;
        this.customerRepository = customerRepository;
        this.productRepository = productRepository;
        this.quotationRepository = quotationRepository;
        this.invoiceRepository = invoiceRepository;
        this.accountService = accountService;
    }

    @Transactional(readOnly = true)
    public Checklist checklist(UUID workspaceId) {
        UUID tenantId = SecurityContextHelper.requireTenantId();
        Workspace ws = workspaceService.requireInTenant(workspaceId, tenantId);

        List<Step> steps = new ArrayList<>();
        steps.add(new Step("TERMS", "Accepter les conditions d'utilisation",
                "CGU et CGV de la plateforme, à accepter une fois par utilisateur.",
                accountService.termsStatus().accepted(), "/legal/cgu"));
        steps.add(new Step("LEGAL_PROFILE", "Compléter le profil légal de l'entreprise",
                "Adresse, SIREN et IBAN apparaissent sur vos devis et factures (roue crantée du workspace, barre latérale).",
                legalProfileComplete(ws), null));
        steps.add(new Step("MAIL", "Connecter une boîte e-mail d'envoi",
                "Nécessaire pour envoyer devis, factures et relances depuis l'application.",
                mailService.canSendFrom(tenantId, workspaceId), "/settings/mail"));
        steps.add(new Step("CUSTOMER", "Ajouter un premier client",
                "Saisie manuelle ou import CSV depuis votre ancien outil.",
                customerRepository.existsByTenantIdAndWorkspaceId(tenantId, workspaceId), "/customers"));
        steps.add(new Step("PRODUCT", "Créer votre catalogue",
                "Prestations et produits réutilisables dans les devis (import CSV possible).",
                productRepository.existsByTenantIdAndWorkspaceId(tenantId, workspaceId), "/products"));
        steps.add(new Step("QUOTATION", "Rédiger un premier devis",
                "Remise, acompte et lien d'acceptation en ligne inclus.",
                quotationRepository.existsByTenantIdAndWorkspaceId(tenantId, workspaceId), "/quotations/new"));
        steps.add(new Step("INVOICE", "Émettre une première facture",
                "Depuis un devis accepté ou directement.",
                invoiceRepository.existsByTenantIdAndWorkspaceId(tenantId, workspaceId), "/invoices"));

        int done = (int) steps.stream().filter(Step::done).count();
        return new Checklist(workspaceId, done, steps.size(), steps);
    }

    private static boolean legalProfileComplete(Workspace ws) {
        return notBlank(ws.getAddressLine1()) && notBlank(ws.getPostalCode()) && notBlank(ws.getCity())
                && notBlank(ws.getSiren()) && notBlank(ws.getIban());
    }

    private static boolean notBlank(String v) {
        return v != null && !v.isBlank();
    }
}
