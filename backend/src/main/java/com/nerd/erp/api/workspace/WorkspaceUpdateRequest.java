package com.nerd.erp.api.workspace;

import com.nerd.erp.domain.workspace.SequenceReset;
import com.nerd.erp.domain.workspace.VatRegime;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

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
        /** IBAN sans espaces (normalisé côté service), 15 à 34 caractères alphanumériques. */
        @Size(max = 42) @Pattern(regexp = "^$|^[A-Za-z0-9 ]{15,42}$", message = "IBAN invalide") String iban,
        @Size(max = 11) @Pattern(regexp = "^$|^[A-Za-z0-9]{8}([A-Za-z0-9]{3})?$", message = "BIC invalide") String bic,
        @NotBlank @Size(min = 3, max = 3) String currencyCode,
        @NotBlank @Size(max = 20) String invoicePrefix,
        @NotBlank @Size(max = 20) String quotationPrefix,
        // --- Mentions légales / conditions de règlement (tous optionnels : valeurs par défaut conservées) ---
        @Size(max = 20) String creditNotePrefix,
        VatRegime vatRegime,
        @Min(0) @Max(120) Integer paymentTermsDays,
        @DecimalMin("0") @DecimalMax("100") BigDecimal latePenaltyRate,
        @DecimalMin("0") BigDecimal recoveryFeeAmount,
        @Size(max = 255) String earlyPaymentDiscount,
        @Size(max = 512) String paymentTermsText,
        @Size(max = 1024) String legalFooterText,
        Boolean remindersEnabled,
        // --- Numérotation (optionnels) ---
        @Size(max = 64) String numberPattern,
        SequenceReset sequenceReset
) {
}
