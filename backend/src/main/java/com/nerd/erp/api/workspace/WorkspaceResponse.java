package com.nerd.erp.api.workspace;

import lombok.Builder;

import java.util.UUID;

/**
 * Construit via {@link WorkspaceResponseBuilder} (Lombok) pour éviter les constructeurs
 * positionnels à 20+ arguments ; voir {@code WorkspaceMapper}.
 */
@Builder
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
        String iban,
        String bic,
        String invoicePrefix,
        String quotationPrefix
) {
}
