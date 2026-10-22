package com.nerd.erp.application.invoicescan;

import com.nerd.erp.domain.invoice.Invoice;
import com.nerd.erp.infrastructure.persistence.invoice.InvoiceRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class InvoiceDuplicateFinder {

    private final InvoiceRepository invoiceRepository;

    public InvoiceDuplicateFinder(InvoiceRepository invoiceRepository) {
        this.invoiceRepository = invoiceRepository;
    }

    public Optional<Invoice> findByOrderNumber(UUID tenantId, UUID workspaceId, String merchant, String orderNumber) {
        if (orderNumber == null || orderNumber.isBlank()) {
            return Optional.empty();
        }
        String order = orderNumber.trim();
        if (merchant != null && !merchant.isBlank()) {
            Optional<Invoice> scoped = invoiceRepository
                    .findByTenantIdAndWorkspaceIdAndMerchantIgnoreCaseAndOrderNumberIgnoreCase(
                            tenantId, workspaceId, merchant.trim(), order);
            if (scoped.isPresent()) {
                return scoped;
            }
        }
        return invoiceRepository.findByTenantIdAndWorkspaceIdAndOrderNumberIgnoreCase(tenantId, workspaceId, order);
    }

    public Optional<Invoice> findByDocumentReference(UUID tenantId, UUID workspaceId, String externalReference) {
        if (externalReference == null || externalReference.isBlank()) {
            return Optional.empty();
        }
        String ref = externalReference.trim();
        Optional<Invoice> byColumn = invoiceRepository
                .findByTenantIdAndWorkspaceIdAndExternalReferenceIgnoreCase(tenantId, workspaceId, ref);
        if (byColumn.isPresent()) {
            return byColumn;
        }
        List<Invoice> legacy = invoiceRepository.findAllByTenantIdAndWorkspaceIdAndNotesContainingRef(
                tenantId, workspaceId, ref, Pageable.ofSize(1));
        return legacy.isEmpty() ? Optional.empty() : Optional.of(legacy.getFirst());
    }
}
