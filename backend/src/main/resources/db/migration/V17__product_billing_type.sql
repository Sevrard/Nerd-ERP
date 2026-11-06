-- Mode de tarification et unité d'un produit : colonnes dédiées à la place des lignes
-- « Facturation : X » / « Quantité indicative : N unité » portées par la description.
ALTER TABLE product
    ADD COLUMN billing_type VARCHAR(32) NOT NULL DEFAULT 'UNIT',
    ADD COLUMN unit_label   VARCHAR(32);

-- Contrainte posée avant la reprise : PostgreSQL refuse un ALTER TABLE après des UPDATE ayant laissé
-- des événements de trigger (FK) en attente dans la même transaction. Le CASE ci-dessous ne produit
-- que des valeurs admises.
ALTER TABLE product ADD CONSTRAINT chk_product_billing_type
    CHECK (billing_type IN ('UNIT', 'DAYS', 'FIXED_PRICE', 'RECURRING'));

-- Reprise des produits existants : type depuis « Facturation : … », unité depuis « Quantité indicative : … ».
UPDATE product
SET billing_type = CASE substring(description from 'Facturation :\s*([A-Z_]+)')
                       WHEN 'DAYS' THEN 'DAYS'
                       WHEN 'FIXED_PRICE' THEN 'FIXED_PRICE'
                       WHEN 'RECURRING' THEN 'RECURRING'
                       ELSE 'UNIT'
                   END,
    unit_label   = left(substring(description from 'Quantité indicative :\s*[0-9.,]+\s*(\S+)'), 32)
WHERE description IS NOT NULL;

-- Unité par défaut cohérente avec le mode lorsque rien n'était renseigné.
UPDATE product
SET unit_label = CASE billing_type
                     WHEN 'DAYS' THEN 'jour'
                     WHEN 'FIXED_PRICE' THEN 'forfait'
                     WHEN 'RECURRING' THEN 'mois'
                     ELSE NULL
                 END
WHERE unit_label IS NULL OR unit_label = '';

-- Les lignes de métadonnées désormais modélisées sont retirées de la description.
UPDATE product
SET description = NULLIF(
        btrim(
            regexp_replace(description, '(^|\n)(Facturation|Quantité indicative) :[^\n]*', '', 'g'),
            E' \n\r\t'),
        '')
WHERE description ~ '(^|\n)(Facturation|Quantité indicative) :';
