package com.nerd.erp.domain.workspace;

import com.nerd.erp.domain.shared.TenantScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

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

    @Column(nullable = false)
    private boolean active = true;
}
