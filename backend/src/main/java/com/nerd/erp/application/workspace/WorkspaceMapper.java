package com.nerd.erp.application.workspace;

import com.nerd.erp.api.workspace.WorkspaceResponse;
import com.nerd.erp.domain.workspace.Workspace;
import org.springframework.stereotype.Component;

/**
 * Mapping explicite Workspace → WorkspaceResponse par builder nommé.
 * Un test de complétude ({@code WorkspaceMapperTest}) vérifie qu'aucun champ n'est oublié.
 */
@Component
public class WorkspaceMapper {

    public WorkspaceResponse toResponse(Workspace w) {
        return WorkspaceResponse.builder()
                .id(w.getId())
                .name(w.getName())
                .slug(w.getSlug())
                .organizationId(w.getOrganizationId())
                .currencyCode(w.getCurrencyCode())
                .brandColor(w.getBrandColor())
                .logoUrl(w.getLogoUrl())
                .active(w.isActive())
                .legalSubtitle(w.getLegalSubtitle())
                .addressLine1(w.getAddressLine1())
                .addressLine2(w.getAddressLine2())
                .postalCode(w.getPostalCode())
                .city(w.getCity())
                .region(w.getRegion())
                .countryCode(w.getCountryCode())
                .contactEmail(w.getContactEmail())
                .websiteUrl(w.getWebsiteUrl())
                .siren(w.getSiren())
                .vatNumber(w.getVatNumber())
                .iban(w.getIban())
                .bic(w.getBic())
                .invoicePrefix(w.getInvoicePrefix())
                .quotationPrefix(w.getQuotationPrefix())
                .creditNotePrefix(w.getCreditNotePrefix())
                .vatRegime(w.getVatRegime())
                .paymentTermsDays(w.getPaymentTermsDays())
                .latePenaltyRate(w.getLatePenaltyRate())
                .recoveryFeeAmount(w.getRecoveryFeeAmount())
                .earlyPaymentDiscount(w.getEarlyPaymentDiscount())
                .paymentTermsText(w.getPaymentTermsText())
                .legalFooterText(w.getLegalFooterText())
                .remindersEnabled(w.isRemindersEnabled())
                .numberPattern(w.getNumberPattern())
                .sequenceReset(w.getSequenceReset())
                .build();
    }
}
