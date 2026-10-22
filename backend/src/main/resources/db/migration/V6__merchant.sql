CREATE TABLE merchant (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL,
    workspace_id    UUID NOT NULL,
    name            VARCHAR(64) NOT NULL,
    slug            VARCHAR(32) NOT NULL,
    logo_url        VARCHAR(512),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT fk_merchant_workspace FOREIGN KEY (workspace_id, tenant_id)
        REFERENCES workspace (id, tenant_id) ON DELETE CASCADE,
    CONSTRAINT uq_merchant_workspace_slug UNIQUE (workspace_id, slug)
);

ALTER TABLE merchant ADD CONSTRAINT uq_merchant_id_tenant UNIQUE (id, tenant_id);

CREATE INDEX idx_merchant_workspace ON merchant (tenant_id, workspace_id);

INSERT INTO merchant (id, tenant_id, workspace_id, name, slug, logo_url, created_at, updated_at)
SELECT gen_random_uuid(), w.tenant_id, w.id, v.name, v.slug, v.logo_url, now(), now()
FROM workspace w
CROSS JOIN (
    VALUES
        ('Etsy', 'etsy', '/branding/etsy-icon.png'),
        ('Nerd-Forge', 'nerd-forge', NULL)
) AS v(name, slug, logo_url)
WHERE w.slug = 'nerd-forge'
ON CONFLICT (workspace_id, slug) DO NOTHING;
