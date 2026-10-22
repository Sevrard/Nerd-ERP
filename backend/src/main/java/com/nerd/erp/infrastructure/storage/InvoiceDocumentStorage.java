package com.nerd.erp.infrastructure.storage;

import com.nerd.erp.api.error.ApiException;
import com.nerd.erp.domain.invoice.Invoice;
import com.nerd.erp.infrastructure.config.storage.StorageProperties;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

@Component
public class InvoiceDocumentStorage {

    private final Path root;

    public InvoiceDocumentStorage(StorageProperties properties) {
        this.root = Path.of(properties.invoiceDocumentsOrDefault()).toAbsolutePath().normalize();
    }

    public String storeSourcePdf(Invoice invoice, byte[] pdfBytes) {
        if (pdfBytes == null || pdfBytes.length == 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "PDF source vide");
        }
        String relative = relativePath(invoice.getTenantId(), invoice.getWorkspaceId(), invoice.getId());
        Path target = root.resolve(relative).normalize();
        if (!target.startsWith(root)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Chemin document invalide");
        }
        try {
            Files.createDirectories(target.getParent());
            Files.write(target, pdfBytes);
            return relative;
        } catch (IOException e) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Impossible d'enregistrer le PDF : " + e.getMessage());
        }
    }

    public byte[] readOrNull(String relativePath) {
        if (relativePath == null || relativePath.isBlank()) {
            return null;
        }
        Path file = root.resolve(relativePath).normalize();
        if (!file.startsWith(root) || !Files.isRegularFile(file)) {
            return null;
        }
        try {
            return Files.readAllBytes(file);
        } catch (IOException e) {
            return null;
        }
    }

    public void deleteIfPresent(String relativePath) {
        if (relativePath == null || relativePath.isBlank()) {
            return;
        }
        Path file = root.resolve(relativePath).normalize();
        if (!file.startsWith(root)) {
            return;
        }
        try {
            Files.deleteIfExists(file);
        } catch (IOException ignored) {
            // best effort
        }
    }

    private static String relativePath(UUID tenantId, UUID workspaceId, UUID invoiceId) {
        return tenantId + "/" + workspaceId + "/" + invoiceId + ".pdf";
    }
}
