package com.nerd.erp.domain.catalog;

import com.nerd.erp.domain.shared.TenantScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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

    @Column(name = "category_id")
    private UUID categoryId;

    @Enumerated(EnumType.STRING)
    @Column(name = "billing_type", nullable = false, length = 32)
    private BillingType billingType = BillingType.UNIT;

    /** Libellé d'unité affiché après le prix (« jour », « forfait », « mois », « pièce »…). Null = défaut du mode. */
    @Column(name = "unit_label", length = 32)
    private String unitLabel;

    public String effectiveUnitLabel() {
        if (unitLabel != null && !unitLabel.isBlank()) {
            return unitLabel;
        }
        return (billingType == null ? BillingType.UNIT : billingType).defaultUnitLabel();
    }
}
