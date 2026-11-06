package com.nerd.erp.domain.workspace;

import com.nerd.erp.domain.shared.TenantScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Table(name = "workspace")
@Getter
@Setter
public class Workspace extends TenantScopedEntity {

    @Column(name = "organization_id", nullable = false)
    private java.util.UUID organizationId;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, length = 100)
    private String slug;

    @Column(name = "brand_color", length = 7)
    private String brandColor;

    @Column(name = "logo_url", length = 2048)
    private String logoUrl;

    @Column(name = "currency_code", nullable = false, length = 3)
    private String currencyCode = "EUR";

    @Column(name = "invoice_prefix", nullable = false, length = 20)
    private String invoicePrefix = "FAC";

    @Column(name = "quotation_prefix", nullable = false, length = 20)
    private String quotationPrefix = "DEV";

    @Column(name = "legal_subtitle", length = 255)
    private String legalSubtitle;

    @Column(name = "address_line1", length = 255)
    private String addressLine1;

    @Column(name = "address_line2", length = 255)
    private String addressLine2;

    @Column(name = "postal_code", length = 20)
    private String postalCode;

    @Column(length = 120)
    private String city;

    @Column(length = 120)
    private String region;

    @Column(name = "country_code", nullable = false, length = 2)
    private String countryCode = "FR";

    @Column(name = "contact_email", length = 255)
    private String contactEmail;

    @Column(name = "website_url", length = 512)
    private String websiteUrl;

    @Column(length = 20)
    private String siren;

    @Column(name = "vat_number", length = 32)
    private String vatNumber;

    @Column(length = 34)
    private String iban;

    @Column(length = 11)
    private String bic;

    // --- Mentions légales et conditions de règlement (reportées sur les PDF) ---

    @Enumerated(EnumType.STRING)
    @Column(name = "vat_regime", nullable = false, length = 20)
    private VatRegime vatRegime = VatRegime.NORMAL;

    /** Délai de paiement par défaut (échéance = date d'émission + N jours). */
    @Column(name = "payment_terms_days", nullable = false)
    private int paymentTermsDays = 30;

    /** Taux annuel des pénalités de retard (%). */
    @Column(name = "late_penalty_rate", nullable = false, precision = 5, scale = 2)
    private BigDecimal latePenaltyRate = new BigDecimal("12.00");

    /** Indemnité forfaitaire pour frais de recouvrement (40 € en France). */
    @Column(name = "recovery_fee_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal recoveryFeeAmount = new BigDecimal("40");

    /** Null = « Escompte pour paiement anticipé : néant ». */
    @Column(name = "early_payment_discount", length = 255)
    private String earlyPaymentDiscount;

    @Column(name = "payment_terms_text", length = 512)
    private String paymentTermsText;

    @Column(name = "legal_footer_text", length = 1024)
    private String legalFooterText;

    @Column(name = "credit_note_prefix", nullable = false, length = 20)
    private String creditNotePrefix = "AV";

    /** Motif de numérotation ({PREFIX}, {YYYY}, {YY}, {MM}, {SEQ:n}). */
    @Column(name = "number_pattern", nullable = false, length = 64)
    private String numberPattern = "{PREFIX}-{YYYY}-{SEQ:5}";

    @Enumerated(EnumType.STRING)
    @Column(name = "sequence_reset", nullable = false, length = 10)
    private SequenceReset sequenceReset = SequenceReset.YEARLY;

    /** Relances automatiques des factures échues (J+7, J+15). */
    @Column(name = "reminders_enabled", nullable = false)
    private boolean remindersEnabled = false;

    @Column(nullable = false)
    private boolean active = true;
}
