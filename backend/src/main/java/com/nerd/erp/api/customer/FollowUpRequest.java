package com.nerd.erp.api.customer;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.UUID;

public record FollowUpRequest(
        @NotNull UUID workspaceId,
        @NotNull Instant scheduledAt,
        @NotBlank String actionType,
        String notes
) {
}
