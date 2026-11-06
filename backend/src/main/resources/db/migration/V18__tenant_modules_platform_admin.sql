-- Modules optionnels activables par tenant (socle toujours actif, non listé ici).
CREATE TABLE tenant_module (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL REFERENCES tenant (id) ON DELETE CASCADE,
    module_code     VARCHAR(64) NOT NULL,
    enabled         BOOLEAN NOT NULL DEFAULT TRUE,
    activated_at    TIMESTAMPTZ,
    expires_at      DATE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_tenant_module UNIQUE (tenant_id, module_code),
    CONSTRAINT chk_tenant_module_code CHECK (module_code IN ('MAINTENANCE', 'INVOICE_SCAN'))
);

CREATE INDEX idx_tenant_module_tenant ON tenant_module (tenant_id);

-- Les tenants existants conservent l'accès à tout ce qui était disponible avant l'introduction des modules.
INSERT INTO tenant_module (tenant_id, module_code, enabled, activated_at)
SELECT t.id, m.code, TRUE, NOW()
FROM tenant t
CROSS JOIN (VALUES ('MAINTENANCE'), ('INVOICE_SCAN')) AS m(code);

-- Administrateur de la plateforme (éditeur) : gère les tenants et leurs modules, hors contexte tenant.
ALTER TABLE app_user ADD COLUMN platform_admin BOOLEAN NOT NULL DEFAULT FALSE;
