-- Alignement Hibernate (VARCHAR) pour bases déjà migrées en V1 avec CHAR(n)
ALTER TABLE organization
    ALTER COLUMN country_code TYPE VARCHAR(2) USING country_code::varchar;

ALTER TABLE customer
    ALTER COLUMN country_code TYPE VARCHAR(2) USING country_code::varchar;

ALTER TABLE workspace
    ALTER COLUMN currency_code TYPE VARCHAR(3) USING currency_code::varchar;
