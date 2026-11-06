package com.nerd.erp.application.document;

import com.nerd.erp.application.customer.CustomerService;
import com.nerd.erp.application.document.pdf.ProductSkuResolver;
import com.nerd.erp.application.document.pdf.QuotationPdfGenerator;
import com.nerd.erp.application.quotation.QuotationService;
import com.nerd.erp.application.workspace.WorkspaceService;
import com.nerd.erp.domain.customer.Customer;
import com.nerd.erp.domain.quotation.Quotation;
import com.nerd.erp.domain.quotation.QuotationLine;
import com.nerd.erp.domain.workspace.Workspace;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

@Service
public class QuotationPdfService {

    private final QuotationService quotationService;
    private final CustomerService customerService;
    private final WorkspaceService workspaceService;
    private final QuotationPdfGenerator pdfGenerator;
    private final ProductSkuResolver skuResolver;

    public QuotationPdfService(QuotationService quotationService,
                               CustomerService customerService,
                               WorkspaceService workspaceService,
                               QuotationPdfGenerator pdfGenerator,
                               ProductSkuResolver skuResolver) {
        this.quotationService = quotationService;
        this.customerService = customerService;
        this.workspaceService = workspaceService;
        this.pdfGenerator = pdfGenerator;
        this.skuResolver = skuResolver;
    }

    /** Les brouillons sont téléchargeables (aperçu) ; le numéro est alors « Brouillon ». */
    @Transactional(readOnly = true)
    public GeneratedDocument downloadQuotationDocument(UUID quotationId) {
        Quotation quotation = quotationService.requireQuotation(quotationId);
        Customer customer = customerService.requireCustomer(quotation.getCustomerId());
        return generate(quotation, customer);
    }

    /** PDF d'un devis déjà chargé (contrôle d'accès à la charge de l'appelant : lien public, e-mail). */
    @Transactional(readOnly = true)
    public GeneratedDocument generate(Quotation quotation, Customer customer) {
        Workspace workspace = workspaceService.requireInTenant(quotation.getWorkspaceId(), quotation.getTenantId());
        Map<UUID, String> skus = skuResolver.resolve(quotation.getTenantId(),
                quotation.getLines().stream().map(QuotationLine::getProductId).toList());

        String base = quotation.getNumber() != null
                ? quotation.getNumber()
                : "brouillon-" + quotation.getId().toString().substring(0, 8);
        String filename = "devis-" + GeneratedDocument.sanitizeFilename(base) + ".pdf";
        return new GeneratedDocument(pdfGenerator.generate(quotation, workspace, customer, skus), filename);
    }
}
