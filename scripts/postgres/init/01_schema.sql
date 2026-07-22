\set ON_ERROR_STOP on

-- Canonical schema is intentionally an empty-database initializer.
-- Re-running it must fail instead of hiding schema drift.
DO $canonical_preflight$
BEGIN
    IF EXISTS (
        SELECT 1
          FROM information_schema.tables
         WHERE table_schema = 'public'
           AND table_type = 'BASE TABLE'
    ) THEN
        RAISE EXCEPTION
            'Canonical schema requires an empty public schema';
    END IF;
END
$canonical_preflight$;

\ir 01_identity_and_merchant.inc
\ir 01_core_schema.inc
\ir 01_model_config.inc
\ir 01_prompt_management.inc
\ir 01_review_images.inc
\ir 01_review_communication.inc
\ir 01_reply_draft.inc
\ir 01_audit_logs.inc
\ir 01_rate_limit_events.inc
\ir 01_region_hot_words.inc
\ir 01_ai_request_tracing.inc
\ir 01_recommendation_evaluation.inc
\ir 01_regression_testing.inc
