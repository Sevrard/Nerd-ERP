package com.nerd.erp.infrastructure.config.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "nerd.storage")
public record StorageProperties(
        String invoiceDocuments
) {
    public String invoiceDocumentsOrDefault() {
        if (invoiceDocuments == null || invoiceDocuments.isBlank()) {
            return "data/invoice-documents";
        }
        return invoiceDocuments.trim();
    }
}
