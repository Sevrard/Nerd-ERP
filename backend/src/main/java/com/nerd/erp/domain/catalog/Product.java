package com.nerd.erp.domain.catalog;

import com.nerd.erp.domain.shared.TenantScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "product")
@Getter
@Setter
public class Product extends TenantScopedEntity {

    @Column(name = "workspace_id", nullable = false)
    private UUID workspaceId;

    @Column(name = "reference_code", nullable = false, length = 64)
    private String referenceCode;

    @Column(nullable = false)
    private String name;

    private String description;

    @Column(name = "unit_price_ht", nullable = false, precision = 19, scale = 4)
    private BigDecimal unitPriceHt;

    @Column(name = "vat_rate", nullable = false, precision = 5, scale = 2)
    private BigDecimal vatRate = new BigDecimal("20.00");

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "etsy_listing_id")
    private Long etsyListingId;
}
