-- Lot 4 : finitions devis / produits (remise par ligne, factures d'acompte, numérotation configurable).
-- Lot 8 : onboarding client (identité visuelle de l'écran de connexion, acceptation des CGU).

-- Remise commerciale par ligne (% du HT ligne), en plus de la remise globale du document.
ALTER TABLE quotation_line ADD COLUMN discount_percent NUMERIC(5, 2) NOT NULL DEFAULT 0;
ALTER TABLE invoice_line   ADD COLUMN discount_percent NUMERIC(5, 2) NOT NULL DEFAULT 0;

-- Factures d'acompte : même série de numérotation que les factures, type dédié ; la facture de solde
-- référence l'acompte qu'elle déduit.
ALTER TABLE invoice DROP CONSTRAINT chk_invoice_type;
ALTER TABLE invoice
    ADD CONSTRAINT chk_invoice_type CHECK (invoice_type IN ('STANDARD', 'DEPOSIT', 'CREDIT_NOTE')),
    ADD COLUMN deposit_invoice_id UUID REFERENCES invoice (id) ON DELETE RESTRICT;

CREATE INDEX idx_invoice_deposit_invoice ON invoice (deposit_invoice_id) WHERE deposit_invoice_id IS NOT NULL;

-- Un devis peut désormais produire une facture d'acompte ET une facture de solde : l'unicité passe de
-- (devis) à (devis, type), ce qui conserve l'idempotence de chaque conversion.
ALTER TABLE invoice DROP CONSTRAINT uq_invoice_source_quotation;
CREATE UNIQUE INDEX uq_invoice_source_quotation_type ON invoice (source_quotation_id, invoice_type)
    WHERE source_quotation_id IS NOT NULL;
CREATE INDEX idx_invoice_source_quotation ON invoice (tenant_id, source_quotation_id) WHERE source_quotation_id IS NOT NULL;

-- Numérotation configurable : motif ({PREFIX}, {YYYY}, {YY}, {MM}, {SEQ} ou {SEQ:n}) et périodicité de
-- remise à zéro du compteur. La colonne document_sequence.year porte la période (année, AAAAMM ou 0).
ALTER TABLE workspace
    ADD COLUMN number_pattern VARCHAR(64) NOT NULL DEFAULT '{PREFIX}-{YYYY}-{SEQ:5}',
    ADD COLUMN sequence_reset VARCHAR(10) NOT NULL DEFAULT 'YEARLY',
    ADD CONSTRAINT chk_workspace_sequence_reset CHECK (sequence_reset IN ('YEARLY', 'MONTHLY', 'NEVER'));

-- Écran de connexion aux couleurs du client (exposé publiquement par slug de compte).
ALTER TABLE tenant
    ADD COLUMN brand_color   VARCHAR(7),
    ADD COLUMN logo_url      VARCHAR(2048),
    ADD COLUMN login_tagline VARCHAR(255);

-- Acceptation des conditions d'utilisation (version acceptée, pour redemander en cas de mise à jour).
ALTER TABLE app_user
    ADD COLUMN terms_version     VARCHAR(20),
    ADD COLUMN terms_accepted_at TIMESTAMPTZ;
