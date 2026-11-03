package com.nerd.erp.domain.catalog;

import com.nerd.erp.domain.shared.TenantScopedEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "catalog_package")
@Getter
@Setter
public class CatalogPackage extends TenantScopedEntity {

    @Column(name = "workspace_id", nullable = false)
    private UUID workspaceId;

    @Column(name = "reference_code", nullable = false, length = 64)
    private String referenceCode;

    @Column(nullable = false)
    private String title;

    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "discount_type", nullable = false, length = 16)
    private PackageDiscountType discountType = PackageDiscountType.PERCENT;

    @Column(name = "discount_value", nullable = false, precision = 19, scale = 4)
    private BigDecimal discountValue = BigDecimal.ZERO;

    @Column(name = "badge_theme", nullable = false, length = 32)
    private String badgeTheme = "purple";

    @Column(nullable = false)
    private boolean active = true;

    @OneToMany(mappedBy = "catalogPackage", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("lineOrder ASC")
    private List<CatalogPackageItem> items = new ArrayList<>();
}
