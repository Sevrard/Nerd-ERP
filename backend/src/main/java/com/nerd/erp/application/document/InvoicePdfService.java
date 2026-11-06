package com.nerd.erp.application.document;

import com.nerd.erp.api.error.ApiException;
import com.nerd.erp.application.document.pdf.InvoicePdfGenerator;
import com.nerd.erp.application.document.pdf.ProductSkuResolver;
import com.nerd.erp.application.invoice.InvoiceService;
import com.nerd.erp.application.workspace.WorkspaceService;
import com.nerd.erp.domain.invoice.Invoice;
import com.nerd.erp.domain.invoice.InvoiceLine;
import com.nerd.erp.domain.invoice.InvoiceStatus;
import com.nerd.erp.domain.workspace.Workspace;
import com.nerd.erp.infrastructure.storage.InvoiceDocumentStorage;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

@Service
public class InvoicePdfService {

    private final InvoiceService invoiceService;
    private final WorkspaceService workspaceService;
    private final InvoiceDocumentStorage documentStorage;
    private final InvoicePdfGenerator pdfGenerator;
    private final ProductSkuResolver skuResolver;

    public InvoicePdfService(InvoiceService invoiceService,
                             WorkspaceService workspaceService,
                             InvoiceDocumentStorage documentStorage,
                             InvoicePdfGenerator pdfGenerator,
                             ProductSkuResolver skuResolver) {
        this.invoiceService = invoiceService;
        this.workspaceService = workspaceService;
        this.documentStorage = documentStorage;
        this.pdfGenerator = pdfGenerator;
        this.skuResolver = skuResolver;
    }

    @Transactional(readOnly = true)
    public GeneratedDocument downloadInvoiceDocument(UUID invoiceId) {
        Invoice invoice = invoiceService.requireInvoice(invoiceId);
        if (invoice.getStatus() == InvoiceStatus.DRAFT && invoice.getIssuedAt() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Facture non émise");
        }
        String filename = "facture-" + GeneratedDocument.sanitizeFilename(invoice.getNumber()) + ".pdf";
        byte[] stored = documentStorage.readOrNull(invoice.getSourceDocumentPath());
        if (stored != null && stored.length > 0) {
            return new GeneratedDocument(stored, filename);
        }
        Workspace workspace = workspaceService.requireInTenant(invoice.getWorkspaceId(), invoice.getTenantId());
        Map<UUID, String> skus = skuResolver.resolve(invoice.getTenantId(),
                invoice.getLines().stream().map(InvoiceLine::getProductId).toList());
        return new GeneratedDocument(pdfGenerator.generate(invoice, workspace, skus), filename);
    }
}
