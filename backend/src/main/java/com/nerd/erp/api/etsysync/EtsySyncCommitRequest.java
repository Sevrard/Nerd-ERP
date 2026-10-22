package com.nerd.erp.api.etsysync;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

public record EtsySyncCommitRequest(
        @NotNull UUID sessionId, @Valid List<EtsySyncProductCommit> products,
        @Valid List<EtsySyncReceiptCommit> receipts) {
}
