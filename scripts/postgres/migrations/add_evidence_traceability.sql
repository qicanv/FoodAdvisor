\set ON_ERROR_STOP on

BEGIN;

ALTER TABLE recommendation_evidences
    ADD COLUMN IF NOT EXISTS condition_key VARCHAR(100);

ALTER TABLE merchant_summary_evidences
    ADD COLUMN IF NOT EXISTS source_type VARCHAR(30),
    ADD COLUMN IF NOT EXISTS source_merchant_id BIGINT,
    ADD COLUMN IF NOT EXISTS review_version INTEGER;

UPDATE merchant_summary_evidences evidence
   SET source_type = 'REVIEW'
 WHERE source_type IS NULL;

UPDATE merchant_summary_evidences evidence
   SET source_merchant_id = summary.merchant_id
  FROM merchant_review_summaries summary
 WHERE evidence.summary_id = summary.id
   AND evidence.source_merchant_id IS NULL;

UPDATE merchant_summary_evidences evidence
   SET review_version = review.current_version
  FROM reviews review
 WHERE evidence.review_id = review.id
   AND evidence.review_version IS NULL;

DO $constraints$
BEGIN
    IF EXISTS (
        SELECT 1
          FROM merchant_summary_evidences
         WHERE source_type IS NULL OR source_merchant_id IS NULL
    ) THEN
        RAISE EXCEPTION
            'Cannot enforce summary evidence source columns: backfill is incomplete';
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
         WHERE conname = 'fk_summary_evidence_merchant'
           AND conrelid = 'merchant_summary_evidences'::regclass
    ) THEN
        ALTER TABLE merchant_summary_evidences
            ADD CONSTRAINT fk_summary_evidence_merchant
            FOREIGN KEY (source_merchant_id) REFERENCES merchants(id)
            ON DELETE RESTRICT;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
         WHERE conname = 'ck_summary_evidence_source_type'
           AND conrelid = 'merchant_summary_evidences'::regclass
    ) THEN
        ALTER TABLE merchant_summary_evidences
            ADD CONSTRAINT ck_summary_evidence_source_type
            CHECK (source_type IN ('REVIEW'));
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
         WHERE conname = 'ck_summary_evidence_type'
           AND conrelid = 'merchant_summary_evidences'::regclass
    ) THEN
        ALTER TABLE merchant_summary_evidences
            ADD CONSTRAINT ck_summary_evidence_type
            CHECK (evidence_type IN (
                'REVIEW',
                'ADVANTAGE',
                'DISADVANTAGE',
                'DISH',
                'ENVIRONMENT',
                'SERVICE',
                'RECENT_CHANGE'
            ));
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
         WHERE conname = 'ck_summary_evidence_review_version'
           AND conrelid = 'merchant_summary_evidences'::regclass
    ) THEN
        ALTER TABLE merchant_summary_evidences
            ADD CONSTRAINT ck_summary_evidence_review_version
            CHECK (review_version IS NULL OR review_version >= 1);
    END IF;
END
$constraints$;

ALTER TABLE merchant_summary_evidences
    ALTER COLUMN source_type SET NOT NULL,
    ALTER COLUMN source_merchant_id SET NOT NULL;

CREATE INDEX IF NOT EXISTS idx_recommendation_evidences_condition
    ON recommendation_evidences(condition_key);

CREATE INDEX IF NOT EXISTS idx_summary_evidences_merchant
    ON merchant_summary_evidences(source_merchant_id);

COMMIT;
