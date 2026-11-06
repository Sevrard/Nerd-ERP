-- Lot 2 : comptes & accès. Jetons à usage unique (invitation, réinitialisation de mot de passe),
-- suivi de la dernière connexion et état « mot de passe défini » (invitation en attente sinon).
ALTER TABLE app_user ADD COLUMN last_login_at TIMESTAMPTZ;
ALTER TABLE app_user ADD COLUMN password_set BOOLEAN NOT NULL DEFAULT TRUE;

CREATE TABLE account_token (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id             UUID NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    -- Tenant d'origine de l'invitation (null pour une réinitialisation de mot de passe).
    tenant_id           UUID REFERENCES tenant (id) ON DELETE CASCADE,
    kind                VARCHAR(32) NOT NULL,
    -- Seul le hachage SHA-256 du jeton est conservé ; le jeton en clair ne circule que dans l'e-mail.
    token_hash          VARCHAR(64) NOT NULL UNIQUE,
    expires_at          TIMESTAMPTZ NOT NULL,
    used_at             TIMESTAMPTZ,
    created_by_user_id  UUID REFERENCES app_user (id) ON DELETE SET NULL,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_account_token_kind CHECK (kind IN ('INVITATION', 'PASSWORD_RESET'))
);

CREATE INDEX idx_account_token_user_kind ON account_token (user_id, kind);
