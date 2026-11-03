package com.nerd.erp.api.workspace;

import java.util.UUID;

public record WorkspaceResponse(
        UUID id,
        String name,
        String slug,
        UUID organizationId,
        String currencyCode,
        String brandColor,
        String logoUrl,
        boolean active,
        String legalSubtitle,
        String addressLine1,
        String addressLine2,
        String postalCode,
        String city,
        String region,
        String countryCode,
        String contactEmail,
        String websiteUrl,
        String siren,
        String vatNumber,
        String invoicePrefix,
        String quotationPrefix
) {
}
