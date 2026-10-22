package com.nerd.erp.api.workspace;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record WorkspaceCreateRequest(
        @NotBlank @Size(max = 255) String name,
        @Size(max = 100) String slug,
        UUID organizationId,
        @Size(min = 3, max = 3) String currencyCode,
        @Size(max = 20) String invoicePrefix,
        @Size(max = 20) String quotationPrefix
) {
}
