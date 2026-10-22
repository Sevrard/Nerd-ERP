package com.nerd.erp.application.etsysync;

import com.nerd.erp.api.etsysync.EtsySyncProductDraft;
import com.nerd.erp.api.etsysync.EtsySyncReceiptDraft;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record EtsySyncSession(
        UUID id, UUID tenantId, UUID workspaceId, UUID userId,
        List<EtsySyncProductDraft> products, List<EtsySyncReceiptDraft> receipts, Instant expiresAt) {
}
