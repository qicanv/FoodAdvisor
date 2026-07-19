ALTER TABLE reviews ADD COLUMN IF NOT EXISTS review_time TIMESTAMPTZ;

UPDATE reviews SET review_time = created_at WHERE review_time IS NULL;

ALTER TABLE reviews ALTER COLUMN review_time SET NOT NULL;