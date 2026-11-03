CREATE TABLE product_category (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL REFERENCES tenant (id) ON DELETE CASCADE,
    workspace_id    UUID NOT NULL,
    code            VARCHAR(64) NOT NULL,
    label           VARCHAR(255) NOT NULL,
    sort_order      INT NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_product_category_workspace FOREIGN KEY (workspace_id, tenant_id)
        REFERENCES workspace (id, tenant_id) ON DELETE RESTRICT,
    CONSTRAINT uq_product_category_code_workspace UNIQUE (workspace_id, code)
);

CREATE INDEX idx_product_category_workspace ON product_category (tenant_id, workspace_id);
ALTER TABLE product_category ADD CONSTRAINT uq_product_category_id_tenant UNIQUE (id, tenant_id);

ALTER TABLE product ADD COLUMN category_id UUID;

ALTER TABLE product ADD CONSTRAINT fk_product_category FOREIGN KEY (category_id, tenant_id)
    REFERENCES product_category (id, tenant_id) DEFERRABLE INITIALLY DEFERRED;

CREATE INDEX idx_product_category_id ON product (tenant_id, category_id);
