\set ON_ERROR_STOP on

DO $seed_preflight$
BEGIN
    IF EXISTS (SELECT 1 FROM users)
       OR EXISTS (SELECT 1 FROM merchants)
       OR EXISTS (SELECT 1 FROM reviews)
       OR EXISTS (SELECT 1 FROM recommendations) THEN
        RAISE EXCEPTION 'Demo seed requires an empty business database';
    END IF;
END
$seed_preflight$;

BEGIN;
\ir 01_accounts_and_merchants.inc
\ir 02_reviews_and_analysis.inc
\ir 03_reputation_and_ai.inc
COMMIT;
