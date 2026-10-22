-- Liaison Etsy : catalogue, commandes et lignes (stats ventes par listing)

ALTER TABLE product ADD COLUMN etsy_listing_id BIGINT;
CREATE UNIQUE INDEX uq_product_workspace_etsy_listing
    ON product (tenant_id, workspace_id, etsy_listing_id) WHERE etsy_listing_id IS NOT NULL;

ALTER TABLE invoice ADD COLUMN etsy_receipt_id BIGINT;
CREATE UNIQUE INDEX uq_invoice_workspace_etsy_receipt
    ON invoice (tenant_id, workspace_id, etsy_receipt_id) WHERE etsy_receipt_id IS NOT NULL;

ALTER TABLE invoice_line
    ADD COLUMN product_id UUID,
    ADD COLUMN etsy_listing_id BIGINT,
    ADD COLUMN etsy_transaction_id BIGINT;

ALTER TABLE invoice_line
    ADD CONSTRAINT fk_invoice_line_product FOREIGN KEY (product_id, tenant_id)
        REFERENCES product (id, tenant_id) DEFERRABLE INITIALLY DEFERRED;

CREATE INDEX idx_invoice_line_etsy_listing ON invoice_line (tenant_id, etsy_listing_id)
    WHERE etsy_listing_id IS NOT NULL;
