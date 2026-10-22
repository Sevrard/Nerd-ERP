package com.nerd.erp.api.etsysync;

import java.util.List;
import java.util.UUID;

public record EtsySyncFetchResponse(
        UUID sessionId, int listingCount, int receiptCount,
        List<EtsySyncProductDraft> products, List<EtsySyncReceiptDraft> receipts) {
}
