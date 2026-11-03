package com.nerd.erp.api.workspace;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record WorkspaceUpdateRequest(
        @NotBlank @Size(max = 255) String name,
        @Size(max = 255) String legalSubtitle,
        @Size(max = 255) String addressLine1,
        @Size(max = 255) String addressLine2,
        @Size(max = 20) String postalCode,
        @Size(max = 120) String city,
        @Size(max = 120) String region,
        @NotBlank @Size(min = 2, max = 2) String countryCode,
        @Size(max = 255) String contactEmail,
        @Size(max = 512) String websiteUrl,
        @Size(max = 20) String siren,
        @Size(max = 32) String vatNumber,
        @NotBlank @Size(min = 3, max = 3) String currencyCode,
        @NotBlank @Size(max = 20) String invoicePrefix,
        @NotBlank @Size(max = 20) String quotationPrefix
) {
}
