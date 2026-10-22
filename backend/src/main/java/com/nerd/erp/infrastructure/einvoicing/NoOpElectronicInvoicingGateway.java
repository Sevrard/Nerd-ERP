package com.nerd.erp.infrastructure.einvoicing;

import com.nerd.erp.application.einvoicing.ElectronicInvoicingGateway;
import com.nerd.erp.domain.invoice.Invoice;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class NoOpElectronicInvoicingGateway implements ElectronicInvoicingGateway {

    @Override
    public SubmissionResult submitInvoice(UUID tenantId, Invoice invoice) {
        return new SubmissionResult(false, null,
                "Electronic invoicing is not configured. Integrate an accredited platform via ElectronicInvoicingGateway.");
    }

    @Override
    public StatusResult getSubmissionStatus(UUID tenantId, String externalSubmissionId) {
        return new StatusResult("NOT_CONFIGURED", "No external platform connected");
    }
}
