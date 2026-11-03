CREATE TABLE catalog_package (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL REFERENCES tenant (id) ON DELETE CASCADE,
    workspace_id    UUID NOT NULL,
    reference_code  VARCHAR(64) NOT NULL,
    title           VARCHAR(255) NOT NULL,
    description     TEXT,
    discount_type   VARCHAR(16) NOT NULL DEFAULT 'PERCENT',
    discount_value  NUMERIC(19, 4) NOT NULL DEFAULT 0,
    badge_theme     VARCHAR(32) NOT NULL DEFAULT 'purple',
    active          BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_catalog_package_workspace FOREIGN KEY (workspace_id, tenant_id)
        REFERENCES workspace (id, tenant_id) ON DELETE RESTRICT,
    CONSTRAINT uq_catalog_package_ref_workspace UNIQUE (workspace_id, reference_code),
    CONSTRAINT chk_catalog_package_discount_type CHECK (discount_type IN ('PERCENT', 'FIXED'))
);

CREATE INDEX idx_catalog_package_workspace ON catalog_package (tenant_id, workspace_id);
ALTER TABLE catalog_package ADD CONSTRAINT uq_catalog_package_id_tenant UNIQUE (id, tenant_id);

CREATE TABLE catalog_package_item (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL REFERENCES tenant (id) ON DELETE CASCADE,
    package_id      UUID NOT NULL,
    product_id      UUID NOT NULL,
    quantity        NUMERIC(19, 4) NOT NULL DEFAULT 1,
    line_order      INT NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_catalog_package_item_package FOREIGN KEY (package_id, tenant_id)
        REFERENCES catalog_package (id, tenant_id) ON DELETE CASCADE,
    CONSTRAINT fk_catalog_package_item_product FOREIGN KEY (product_id, tenant_id)
        REFERENCES product (id, tenant_id) ON DELETE RESTRICT,
    CONSTRAINT uq_catalog_package_item_product UNIQUE (package_id, product_id)
);

CREATE INDEX idx_catalog_package_item_package ON catalog_package_item (tenant_id, package_id);
