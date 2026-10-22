package com.nerd.erp.application.einvoicing;

import com.nerd.erp.domain.invoice.Invoice;

import java.util.UUID;

/**
 * Point d'intégration futur avec une plateforme agréée de facturation électronique (PDP/PPF).
 * La génération PDF locale ne suffit pas à la conformité réglementaire française.
 */
public interface ElectronicInvoicingGateway {

    SubmissionResult submitInvoice(UUID tenantId, Invoice invoice);

    StatusResult getSubmissionStatus(UUID tenantId, String externalSubmissionId);

    record SubmissionResult(boolean accepted, String externalSubmissionId, String message) {
    }

    record StatusResult(String status, String message) {
    }
}
