-- Retrait de l'intégration Etsy : colonnes de liaison (V10) et marchand seedé (V6).
-- Les factures, clients et produits importés restent en base : seuls les identifiants Etsy disparaissent.

DROP INDEX IF EXISTS idx_invoice_line_etsy_listing;
ALTER TABLE invoice_line
    DROP COLUMN IF EXISTS etsy_listing_id,
    DROP COLUMN IF EXISTS etsy_transaction_id;

DROP INDEX IF EXISTS uq_invoice_workspace_etsy_receipt;
ALTER TABLE invoice DROP COLUMN IF EXISTS etsy_receipt_id;

DROP INDEX IF EXISTS uq_product_workspace_etsy_listing;
ALTER TABLE product DROP COLUMN IF EXISTS etsy_listing_id;

-- Marchand « Etsy » du scan PDF (aucune FK ne le référence : invoice.merchant est un libellé).
DELETE FROM merchant WHERE slug = 'etsy';
