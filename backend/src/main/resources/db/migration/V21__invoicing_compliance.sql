-- Lot 3 : facturation conforme. Mentions légales et conditions de règlement par workspace,
-- avoirs liés à leur facture, envoi par e-mail, acceptation de devis en ligne, relances.

-- Mentions légales / conditions de règlement (reportées sur les PDF).
ALTER TABLE workspace
    ADD COLUMN vat_regime             VARCHAR(20)    NOT NULL DEFAULT 'NORMAL',
    ADD COLUMN payment_terms_days     INTEGER        NOT NULL DEFAULT 30,
    ADD COLUMN late_penalty_rate      NUMERIC(5, 2)  NOT NULL DEFAULT 12.00,
    ADD COLUMN recovery_fee_amount    NUMERIC(19, 4) NOT NULL DEFAULT 40,
    -- Null = « Escompte pour paiement anticipé : néant ».
    ADD COLUMN early_payment_discount VARCHAR(255),
    ADD COLUMN payment_terms_text     VARCHAR(512),
    ADD COLUMN legal_footer_text      VARCHAR(1024),
    ADD COLUMN credit_note_prefix     VARCHAR(20)    NOT NULL DEFAULT 'AV',
    ADD COLUMN reminders_enabled      BOOLEAN        NOT NULL DEFAULT FALSE,
    ADD CONSTRAINT chk_workspace_vat_regime CHECK (vat_regime IN ('NORMAL', 'FRANCHISE_293B')),
    ADD CONSTRAINT chk_workspace_payment_terms CHECK (payment_terms_days BETWEEN 0 AND 120);

-- Les avoirs ont leur propre séquence (AV-AAAA-00001).
ALTER TABLE document_sequence DROP CONSTRAINT chk_sequence_type;
ALTER TABLE document_sequence
    ADD CONSTRAINT chk_sequence_type CHECK (sequence_type IN ('QUOTATION', 'INVOICE', 'CREDIT_NOTE'));

-- Factures : avoir ↔ facture d'origine, envoi par e-mail, relances.
ALTER TABLE invoice
    ADD COLUMN credited_invoice_id UUID REFERENCES invoice (id) ON DELETE RESTRICT,
    ADD COLUMN credit_reason       VARCHAR(512),
    ADD COLUMN sent_at             TIMESTAMPTZ,
    ADD COLUMN reminder_count      INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN last_reminder_at    TIMESTAMPTZ;

CREATE INDEX idx_invoice_credited_invoice ON invoice (credited_invoice_id);
CREATE INDEX idx_invoice_reminders ON invoice (tenant_id, workspace_id, status, due_date);

-- Devis : envoi par e-mail, lien public signé, décision du client (en ligne ou saisie manuellement).
ALTER TABLE quotation
    ADD COLUMN sent_at           TIMESTAMPTZ,
    ADD COLUMN public_token_hash VARCHAR(64) UNIQUE,
    ADD COLUMN decided_at        TIMESTAMPTZ,
    ADD COLUMN decided_by        VARCHAR(255),
    ADD COLUMN decision_comment  VARCHAR(1024),
    ADD COLUMN decision_source   VARCHAR(20),
    ADD CONSTRAINT chk_quotation_decision_source CHECK (decision_source IS NULL OR decision_source IN ('MANUAL', 'ONLINE'));
