package com.nerd.erp.domain.catalog;

import com.nerd.erp.domain.shared.TenantScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "catalog_package_item")
@Getter
@Setter
public class CatalogPackageItem extends TenantScopedEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "package_id", nullable = false)
    private CatalogPackage catalogPackage;

    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal quantity = BigDecimal.ONE;

    @Column(name = "line_order", nullable = false)
    private int lineOrder;
}
