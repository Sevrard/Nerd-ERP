-- Rétro-remplissage pour les imports scan déjà en base (notes « Réf. … »)
UPDATE invoice i
SET external_reference = m.ref
FROM (
    SELECT id,
           TRIM(SUBSTRING(notes FROM 'Réf\. ([[:alnum:]-]+)')) AS ref
    FROM invoice
    WHERE external_reference IS NULL
      AND notes LIKE '%Réf.%'
) m
WHERE i.id = m.id
  AND m.ref IS NOT NULL
  AND m.ref <> '';
