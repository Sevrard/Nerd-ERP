ALTER TABLE invoice
    ADD COLUMN merchant VARCHAR(64),
    ADD COLUMN order_number VARCHAR(128);

CREATE INDEX idx_invoice_workspace_order_number
    ON invoice (tenant_id, workspace_id, order_number)
    WHERE order_number IS NOT NULL;
