-- Boîte d'envoi SMTP propre à chaque workspace (devis / factures envoyés depuis la messagerie du client).
CREATE TABLE workspace_mail_settings (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id           UUID NOT NULL REFERENCES tenant (id) ON DELETE CASCADE,
    workspace_id        UUID NOT NULL UNIQUE REFERENCES workspace (id) ON DELETE CASCADE,
    enabled             BOOLEAN NOT NULL DEFAULT TRUE,
    host                VARCHAR(255) NOT NULL,
    port                INTEGER NOT NULL DEFAULT 587,
    security            VARCHAR(16) NOT NULL DEFAULT 'STARTTLS',
    username            VARCHAR(255) NOT NULL,
    password_encrypted  VARCHAR(1024) NOT NULL,
    from_email          VARCHAR(255) NOT NULL,
    from_name           VARCHAR(255),
    reply_to            VARCHAR(255),
    last_test_at        TIMESTAMPTZ,
    last_test_ok        BOOLEAN,
    last_error          VARCHAR(1024),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_workspace_mail_security CHECK (security IN ('STARTTLS', 'SSL', 'NONE'))
);

-- Journal des e-mails émis (sans le corps du message).
CREATE TABLE email_log (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL REFERENCES tenant (id) ON DELETE CASCADE,
    workspace_id    UUID REFERENCES workspace (id) ON DELETE SET NULL,
    kind            VARCHAR(32) NOT NULL,
    status          VARCHAR(16) NOT NULL DEFAULT 'QUEUED',
    from_email      VARCHAR(255) NOT NULL,
    to_email        VARCHAR(255) NOT NULL,
    subject         VARCHAR(512) NOT NULL,
    related_id      UUID,
    sender_source   VARCHAR(16) NOT NULL,
    attempts        INTEGER NOT NULL DEFAULT 0,
    sent_at         TIMESTAMPTZ,
    last_error      VARCHAR(1024),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_email_log_status CHECK (status IN ('QUEUED', 'SENT', 'FAILED'))
);

CREATE INDEX idx_email_log_workspace_created ON email_log (workspace_id, created_at DESC);
CREATE INDEX idx_email_log_tenant_created ON email_log (tenant_id, created_at DESC);
