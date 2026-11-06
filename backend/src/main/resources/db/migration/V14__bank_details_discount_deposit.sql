-- Coordonnées bancaires de l'émetteur (affichées sur devis / factures)
ALTER TABLE workspace
    ADD COLUMN iban VARCHAR(34),
    ADD COLUMN bic  VARCHAR(11);

-- Remise commerciale globale (% sur le HT) et acompte à la commande (% du TTC) sur les devis.
-- total_ht / total_vat / total_ttc restent les montants NETS après remise ;
-- discount_amount_ht conserve le montant de remise pour tracer le sous-total brut.
ALTER TABLE quotation
    ADD COLUMN discount_percent    NUMERIC(5, 2)  NOT NULL DEFAULT 0,
    ADD COLUMN discount_amount_ht  NUMERIC(19, 4) NOT NULL DEFAULT 0,
    ADD COLUMN deposit_percent     NUMERIC(5, 2)  NOT NULL DEFAULT 0;

-- La remise est reportée sur la facture issue du devis.
ALTER TABLE invoice
    ADD COLUMN discount_percent    NUMERIC(5, 2)  NOT NULL DEFAULT 0,
    ADD COLUMN discount_amount_ht  NUMERIC(19, 4) NOT NULL DEFAULT 0;
