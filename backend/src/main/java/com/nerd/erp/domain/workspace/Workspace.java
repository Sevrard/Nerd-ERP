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

    @Column(nullable = false)
    private boolean active = true;
}
