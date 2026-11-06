-- Même garantie que uq_invoice_number_workspace : un numéro de devis est unique par workspace.
-- Index partiel : les brouillons (number IS NULL) ne sont pas concernés.
CREATE UNIQUE INDEX IF NOT EXISTS uq_quotation_number_workspace
    ON quotation (workspace_id, number)
    WHERE number IS NOT NULL;
