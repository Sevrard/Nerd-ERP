-- Coordonnées émetteur (devis / factures) par workspace
ALTER TABLE workspace
    ADD COLUMN legal_subtitle   VARCHAR(255),
    ADD COLUMN address_line1    VARCHAR(255),
    ADD COLUMN address_line2    VARCHAR(255),
    ADD COLUMN postal_code      VARCHAR(20),
    ADD COLUMN city             VARCHAR(120),
    ADD COLUMN region           VARCHAR(120),
    ADD COLUMN country_code     VARCHAR(2) NOT NULL DEFAULT 'FR',
    ADD COLUMN contact_email    VARCHAR(255),
    ADD COLUMN website_url      VARCHAR(512),
    ADD COLUMN siren            VARCHAR(20),
    ADD COLUMN vat_number       VARCHAR(32);
