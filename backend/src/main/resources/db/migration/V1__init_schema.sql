-- Nerd ERP — schéma initial multi-tenant

CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- Tenancy
CREATE TABLE tenant (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name            VARCHAR(255) NOT NULL,
    slug            VARCHAR(100) NOT NULL,
    active          BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_tenant_slug UNIQUE (slug)
);

CREATE TABLE app_user (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email           VARCHAR(320) NOT NULL,
    password_hash   VARCHAR(255) NOT NULL,
    display_name    VARCHAR(255) NOT NULL,
    enabled         BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_app_user_email UNIQUE (email)
);

CREATE TABLE tenant_membership (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL REFERENCES tenant (id) ON DELETE CASCADE,
    user_id         UUID NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    role            VARCHAR(50) NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_tenant_membership UNIQUE (tenant_id, user_id),
    CONSTRAINT chk_membership_role CHECK (role IN ('OWNER', 'ADMIN', 'USER', 'READONLY'))
);

-- Organization
CREATE TABLE organization (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL REFERENCES tenant (id) ON DELETE CASCADE,
    legal_name      VARCHAR(255) NOT NULL,
    trade_name      VARCHAR(255),
    siret           VARCHAR(14),
    vat_number      VARCHAR(32),
    address_line1   VARCHAR(255),
    address_line2   VARCHAR(255),
    postal_code     VARCHAR(20),
    city            VARCHAR(100),
    country_code    VARCHAR(2) NOT NULL DEFAULT 'FR',
    email           VARCHAR(320),
    phone           VARCHAR(50),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_organization_tenant ON organization (tenant_id);
ALTER TABLE organization ADD CONSTRAINT uq_organization_id_tenant UNIQUE (id, tenant_id);

-- Workspace
CREATE TABLE workspace (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL REFERENCES tenant (id) ON DELETE CASCADE,
    organization_id UUID NOT NULL REFERENCES organization (id) ON DELETE RESTRICT,
    name            VARCHAR(255) NOT NULL,
    slug            VARCHAR(100) NOT NULL,
    brand_color     VARCHAR(7),
    logo_url        VARCHAR(2048),
    currency_code   VARCHAR(3) NOT NULL DEFAULT 'EUR',
    invoice_prefix  VARCHAR(20) NOT NULL DEFAULT 'FAC',
    quotation_prefix VARCHAR(20) NOT NULL DEFAULT 'DEV',
    active          BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_workspace_tenant_slug UNIQUE (tenant_id, slug),
    CONSTRAINT fk_workspace_org_tenant FOREIGN KEY (organization_id, tenant_id)
        REFERENCES organization (id, tenant_id) DEFERRABLE INITIALLY DEFERRED
);

CREATE INDEX idx_workspace_tenant ON workspace (tenant_id);
ALTER TABLE workspace ADD CONSTRAINT uq_workspace_id_tenant UNIQUE (id, tenant_id);

-- Customer
CREATE TABLE customer (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL REFERENCES tenant (id) ON DELETE CASCADE,
    workspace_id    UUID NOT NULL,
    customer_type   VARCHAR(20) NOT NULL DEFAULT 'COMPANY',
    company_name    VARCHAR(255),
    first_name      VARCHAR(100),
    last_name       VARCHAR(100),
    email           VARCHAR(320),
    phone           VARCHAR(50),
    address_line1   VARCHAR(255),
    address_line2   VARCHAR(255),
    postal_code     VARCHAR(20),
    city            VARCHAR(100),
    country_code    VARCHAR(2) NOT NULL DEFAULT 'FR',
    status          VARCHAR(20) NOT NULL DEFAULT 'PROSPECT',
    notes           TEXT,
    next_follow_up_at TIMESTAMPTZ,
    archived        BOOLEAN NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_customer_workspace FOREIGN KEY (workspace_id, tenant_id)
        REFERENCES workspace (id, tenant_id) ON DELETE RESTRICT,
    CONSTRAINT chk_customer_type CHECK (customer_type IN ('COMPANY', 'INDIVIDUAL')),
    CONSTRAINT chk_customer_status CHECK (status IN ('PROSPECT', 'CLIENT', 'INACTIVE'))
);

CREATE INDEX idx_customer_tenant_workspace ON customer (tenant_id, workspace_id);
CREATE INDEX idx_customer_status ON customer (tenant_id, status) WHERE NOT archived;
ALTER TABLE customer ADD CONSTRAINT uq_customer_id_tenant UNIQUE (id, tenant_id);

-- Product (catalog)
CREATE TABLE product (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL REFERENCES tenant (id) ON DELETE CASCADE,
    workspace_id    UUID NOT NULL,
    reference_code  VARCHAR(64) NOT NULL,
    name            VARCHAR(255) NOT NULL,
    description     TEXT,
    unit_price_ht   NUMERIC(19, 4) NOT NULL,
    vat_rate        NUMERIC(5, 2) NOT NULL DEFAULT 20.00,
    active          BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_product_workspace FOREIGN KEY (workspace_id, tenant_id)
        REFERENCES workspace (id, tenant_id) ON DELETE RESTRICT,
    CONSTRAINT uq_product_ref_workspace UNIQUE (workspace_id, reference_code)
);

CREATE INDEX idx_product_workspace ON product (tenant_id, workspace_id);
ALTER TABLE product ADD CONSTRAINT uq_product_id_tenant UNIQUE (id, tenant_id);

-- Document numbering sequences
CREATE TABLE document_sequence (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL REFERENCES tenant (id) ON DELETE CASCADE,
    workspace_id    UUID NOT NULL,
    sequence_type   VARCHAR(20) NOT NULL,
    year            INT NOT NULL,
    last_value      BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_sequence_workspace FOREIGN KEY (workspace_id, tenant_id)
        REFERENCES workspace (id, tenant_id) ON DELETE CASCADE,
    CONSTRAINT uq_document_sequence UNIQUE (workspace_id, sequence_type, year),
    CONSTRAINT chk_sequence_type CHECK (sequence_type IN ('QUOTATION', 'INVOICE'))
);

-- Quotation
CREATE TABLE quotation (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL REFERENCES tenant (id) ON DELETE CASCADE,
    workspace_id    UUID NOT NULL,
    customer_id     UUID NOT NULL,
    number          VARCHAR(64),
    status          VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    issue_date      DATE,
    valid_until     DATE,
    total_ht        NUMERIC(19, 4) NOT NULL DEFAULT 0,
    total_vat       NUMERIC(19, 4) NOT NULL DEFAULT 0,
    total_ttc       NUMERIC(19, 4) NOT NULL DEFAULT 0,
    notes           TEXT,
    converted_invoice_id UUID,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_quotation_workspace FOREIGN KEY (workspace_id, tenant_id)
        REFERENCES workspace (id, tenant_id) ON DELETE RESTRICT,
    CONSTRAINT fk_quotation_customer FOREIGN KEY (customer_id, tenant_id)
        REFERENCES customer (id, tenant_id) ON DELETE RESTRICT,
    CONSTRAINT chk_quotation_status CHECK (status IN ('DRAFT', 'SENT', 'ACCEPTED', 'REJECTED', 'EXPIRED', 'CONVERTED'))
);

ALTER TABLE quotation ADD CONSTRAINT uq_quotation_id_tenant UNIQUE (id, tenant_id);

CREATE UNIQUE INDEX uq_quotation_converted_invoice ON quotation (converted_invoice_id) WHERE converted_invoice_id IS NOT NULL;

CREATE TABLE quotation_line (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL REFERENCES tenant (id) ON DELETE CASCADE,
    quotation_id    UUID NOT NULL REFERENCES quotation (id) ON DELETE CASCADE,
    product_id      UUID,
    line_order      INT NOT NULL DEFAULT 0,
    description     TEXT NOT NULL,
    quantity        NUMERIC(19, 4) NOT NULL DEFAULT 1,
    unit_price_ht   NUMERIC(19, 4) NOT NULL,
    vat_rate        NUMERIC(5, 2) NOT NULL,
    line_total_ht   NUMERIC(19, 4) NOT NULL,
    line_total_vat  NUMERIC(19, 4) NOT NULL,
    line_total_ttc  NUMERIC(19, 4) NOT NULL,
    CONSTRAINT fk_quotation_line_product FOREIGN KEY (product_id, tenant_id)
        REFERENCES product (id, tenant_id) DEFERRABLE INITIALLY DEFERRED
);

CREATE INDEX idx_quotation_workspace ON quotation (tenant_id, workspace_id);

-- Invoice
CREATE TABLE invoice (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL REFERENCES tenant (id) ON DELETE CASCADE,
    workspace_id    UUID NOT NULL,
    customer_id     UUID NOT NULL,
    source_quotation_id UUID,
    invoice_type    VARCHAR(20) NOT NULL DEFAULT 'STANDARD',
    number          VARCHAR(64) NOT NULL,
    status          VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    issue_date      DATE NOT NULL,
    due_date        DATE NOT NULL,
    total_ht        NUMERIC(19, 4) NOT NULL DEFAULT 0,
    total_vat       NUMERIC(19, 4) NOT NULL DEFAULT 0,
    total_ttc       NUMERIC(19, 4) NOT NULL DEFAULT 0,
    amount_paid     NUMERIC(19, 4) NOT NULL DEFAULT 0,
    snapshot_customer JSONB NOT NULL DEFAULT '{}',
    notes           TEXT,
    issued_at       TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_invoice_workspace FOREIGN KEY (workspace_id, tenant_id)
        REFERENCES workspace (id, tenant_id) ON DELETE RESTRICT,
    CONSTRAINT fk_invoice_customer FOREIGN KEY (customer_id, tenant_id)
        REFERENCES customer (id, tenant_id) ON DELETE RESTRICT,
    CONSTRAINT fk_invoice_source_quotation FOREIGN KEY (source_quotation_id, tenant_id)
        REFERENCES quotation (id, tenant_id) DEFERRABLE INITIALLY DEFERRED,
    CONSTRAINT uq_invoice_number_workspace UNIQUE (workspace_id, number),
    CONSTRAINT uq_invoice_source_quotation UNIQUE (source_quotation_id),
    CONSTRAINT chk_invoice_type CHECK (invoice_type IN ('STANDARD', 'CREDIT_NOTE')),
    CONSTRAINT chk_invoice_status CHECK (status IN ('DRAFT', 'ISSUED', 'PARTIALLY_PAID', 'PAID', 'CANCELLED'))
);

ALTER TABLE invoice ADD CONSTRAINT uq_invoice_id_tenant UNIQUE (id, tenant_id);

CREATE INDEX idx_invoice_workspace ON invoice (tenant_id, workspace_id);
CREATE INDEX idx_invoice_due ON invoice (tenant_id, due_date) WHERE status IN ('ISSUED', 'PARTIALLY_PAID');

CREATE TABLE invoice_line (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL REFERENCES tenant (id) ON DELETE CASCADE,
    invoice_id      UUID NOT NULL REFERENCES invoice (id) ON DELETE CASCADE,
    line_order      INT NOT NULL DEFAULT 0,
    description     TEXT NOT NULL,
    quantity        NUMERIC(19, 4) NOT NULL DEFAULT 1,
    unit_price_ht   NUMERIC(19, 4) NOT NULL,
    vat_rate        NUMERIC(5, 2) NOT NULL,
    line_total_ht   NUMERIC(19, 4) NOT NULL,
    line_total_vat  NUMERIC(19, 4) NOT NULL,
    line_total_ttc  NUMERIC(19, 4) NOT NULL
);

ALTER TABLE quotation ADD CONSTRAINT fk_quotation_converted_invoice FOREIGN KEY (converted_invoice_id, tenant_id)
    REFERENCES invoice (id, tenant_id) DEFERRABLE INITIALLY DEFERRED;

-- Payment
CREATE TABLE payment (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL REFERENCES tenant (id) ON DELETE CASCADE,
    workspace_id    UUID NOT NULL,
    customer_id     UUID NOT NULL,
    amount          NUMERIC(19, 4) NOT NULL,
    payment_date    DATE NOT NULL,
    method          VARCHAR(30) NOT NULL DEFAULT 'TRANSFER',
    reference       VARCHAR(255),
    notes           TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_payment_workspace FOREIGN KEY (workspace_id, tenant_id)
        REFERENCES workspace (id, tenant_id) ON DELETE RESTRICT,
    CONSTRAINT fk_payment_customer FOREIGN KEY (customer_id, tenant_id)
        REFERENCES customer (id, tenant_id) ON DELETE RESTRICT,
    CONSTRAINT chk_payment_method CHECK (method IN ('TRANSFER', 'CARD', 'CHECK', 'CASH', 'OTHER'))
);

CREATE INDEX idx_payment_workspace ON payment (tenant_id, workspace_id);

CREATE TABLE payment_allocation (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL REFERENCES tenant (id) ON DELETE CASCADE,
    payment_id      UUID NOT NULL REFERENCES payment (id) ON DELETE CASCADE,
    invoice_id      UUID NOT NULL,
    amount          NUMERIC(19, 4) NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_allocation_invoice FOREIGN KEY (invoice_id, tenant_id)
        REFERENCES invoice (id, tenant_id) ON DELETE RESTRICT,
    CONSTRAINT uq_allocation_payment_invoice UNIQUE (payment_id, invoice_id)
);

-- Maintenance
CREATE TABLE maintenance_contract (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL REFERENCES tenant (id) ON DELETE CASCADE,
    workspace_id    UUID NOT NULL,
    customer_id     UUID NOT NULL,
    product_id      UUID,
    title           VARCHAR(255) NOT NULL,
    description     TEXT,
    periodicity     VARCHAR(20) NOT NULL DEFAULT 'MONTHLY',
    amount_ht       NUMERIC(19, 4) NOT NULL,
    vat_rate        NUMERIC(5, 2) NOT NULL DEFAULT 20.00,
    start_date      DATE NOT NULL,
    end_date        DATE,
    next_renewal_date DATE,
    status          VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_maint_workspace FOREIGN KEY (workspace_id, tenant_id)
        REFERENCES workspace (id, tenant_id) ON DELETE RESTRICT,
    CONSTRAINT fk_maint_customer FOREIGN KEY (customer_id, tenant_id)
        REFERENCES customer (id, tenant_id) ON DELETE RESTRICT,
    CONSTRAINT fk_maint_product FOREIGN KEY (product_id, tenant_id)
        REFERENCES product (id, tenant_id) DEFERRABLE INITIALLY DEFERRED,
    CONSTRAINT chk_maint_periodicity CHECK (periodicity IN ('MONTHLY', 'QUARTERLY', 'YEARLY')),
    CONSTRAINT chk_maint_status CHECK (status IN ('DRAFT', 'ACTIVE', 'SUSPENDED', 'TERMINATED', 'RENEWAL_DUE'))
);

CREATE INDEX idx_maint_renewal ON maintenance_contract (tenant_id, next_renewal_date);

-- Follow-up
CREATE TABLE follow_up (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL REFERENCES tenant (id) ON DELETE CASCADE,
    workspace_id    UUID NOT NULL,
    customer_id     UUID NOT NULL,
    scheduled_at    TIMESTAMPTZ NOT NULL,
    action_type     VARCHAR(50) NOT NULL DEFAULT 'CALL',
    notes           TEXT,
    completed       BOOLEAN NOT NULL DEFAULT FALSE,
    completed_at    TIMESTAMPTZ,
    created_by_user_id UUID REFERENCES app_user (id),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_followup_workspace FOREIGN KEY (workspace_id, tenant_id)
        REFERENCES workspace (id, tenant_id) ON DELETE RESTRICT,
    CONSTRAINT fk_followup_customer FOREIGN KEY (customer_id, tenant_id)
        REFERENCES customer (id, tenant_id) ON DELETE RESTRICT
);

CREATE INDEX idx_follow_up_scheduled ON follow_up (tenant_id, scheduled_at) WHERE NOT completed;

-- Audit
CREATE TABLE audit_event (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL REFERENCES tenant (id) ON DELETE CASCADE,
    user_id         UUID REFERENCES app_user (id),
    entity_type     VARCHAR(100) NOT NULL,
    entity_id       UUID,
    action          VARCHAR(50) NOT NULL,
    details         JSONB,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_audit_tenant_created ON audit_event (tenant_id, created_at DESC);
