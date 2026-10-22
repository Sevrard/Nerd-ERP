package com.nerd.erp.application.invoicescan;

import java.time.Instant;
import java.util.UUID;

public record InvoiceScanSession(
        UUID id,
        UUID tenantId,
        UUID workspaceId,
        UUID userId,
        ExtractedInvoiceData extraction,
        CustomerMatchStatus customerMatchStatus,
        UUID matchedCustomerId,
        byte[] sourcePagePdf,
        Instant createdAt,
        Instant expiresAt
) {
}
