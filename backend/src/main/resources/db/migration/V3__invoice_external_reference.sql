ALTER TABLE invoice
    ADD COLUMN external_reference VARCHAR(128);

CREATE UNIQUE INDEX uq_invoice_workspace_external_ref
    ON invoice (workspace_id, external_reference)
    WHERE external_reference IS NOT NULL;
