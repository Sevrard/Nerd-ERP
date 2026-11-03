package com.nerd.erp.domain.catalog;

import com.nerd.erp.domain.shared.TenantScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "product_category")
@Getter
@Setter
public class ProductCategory extends TenantScopedEntity {

    @Column(name = "workspace_id", nullable = false)
    private UUID workspaceId;

    @Column(nullable = false, length = 64)
    private String code;

    @Column(nullable = false)
    private String label;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;
}
