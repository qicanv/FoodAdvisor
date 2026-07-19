\set ON_ERROR_STOP on

DO $validation$
DECLARE
    expected_tables text[] := ARRAY[
        'ai_call_logs', 'audit_logs', 'auth_sessions', 'chat_messages',
        'chat_session_states', 'chat_sessions', 'constraint_extractions',
        'dishes', 'import_task_items', 'import_tasks', 'login_attempts',
        'merchant_business_hours', 'merchant_highlight_evidences',
        'merchant_highlights', 'merchant_members',
        'merchant_reputation_statistics', 'merchant_review_summaries',
        'merchant_summary_evidences', 'merchants', 'model_configs',
        'model_scene_bindings', 'notifications', 'recommendation_evidences',
        'recommendation_feedback', 'recommendation_items', 'recommendations',
        'region_hot_words', 'review_analysis', 'review_images',
        'review_issue_categories', 'review_issue_relations', 'review_reply',
        'review_tag_relations', 'review_tags', 'review_versions', 'reviews',
        'users'
    ];
    missing_tables text[];
    table_count integer;
    invalid_fk_count integer;
    missing_sequence_count integer;
BEGIN
    SELECT array_agg(name ORDER BY name)
      INTO missing_tables
      FROM unnest(expected_tables) AS name
     WHERE to_regclass('public.' || name) IS NULL;
    IF missing_tables IS NOT NULL THEN
        RAISE EXCEPTION 'Missing expected tables: %', missing_tables;
    END IF;

    IF to_regclass('public.restaurants') IS NOT NULL THEN
        RAISE EXCEPTION 'Legacy restaurants table must not exist';
    END IF;
    IF to_regclass('public.recommendation_feedbacks') IS NOT NULL
       OR to_regclass('public.recommendations_feedbacks') IS NOT NULL THEN
        RAISE EXCEPTION 'Plural recommendation feedback table must not exist';
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
         WHERE table_schema = 'public' AND table_name = 'audit_logs'
           AND column_name = 'operator_role'
    ) OR NOT EXISTS (
        SELECT 1 FROM information_schema.columns
         WHERE table_schema = 'public' AND table_name = 'audit_logs'
           AND column_name = 'operation_type'
    ) THEN
        RAISE EXCEPTION 'audit_logs current operator columns are missing';
    END IF;
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
         WHERE table_schema = 'public' AND table_name = 'audit_logs'
           AND column_name = 'actor_role'
    ) THEN
        RAISE EXCEPTION 'audit_logs.actor_role must not exist';
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
         WHERE table_schema = 'public' AND table_name = 'reviews'
           AND column_name = 'review_time' AND is_nullable = 'YES'
           AND data_type = 'timestamp with time zone'
    ) THEN
        RAISE EXCEPTION 'reviews.review_time must be nullable TIMESTAMPTZ';
    END IF;

    IF NOT EXISTS (
        SELECT 1
          FROM pg_indexes
         WHERE schemaname = 'public'
           AND tablename = 'chat_messages'
           AND indexname = 'uk_chat_messages_session_request'
           AND indexdef ~* 'CREATE UNIQUE INDEX'
           AND indexdef ~* '\(session_id, request_id, role\)'
           AND indexdef ~* 'WHERE \(request_id IS NOT NULL\)'
    ) THEN
        RAISE EXCEPTION
            'uk_chat_messages_session_request must uniquely index (session_id, request_id, role) where request_id is not null';
    END IF;

    SELECT count(*) INTO invalid_fk_count
      FROM pg_constraint c
      JOIN pg_class t ON t.oid = c.conrelid
      JOIN pg_namespace n ON n.oid = t.relnamespace
     WHERE n.nspname = 'public' AND c.contype = 'f'
       AND NOT c.convalidated;
    IF invalid_fk_count <> 0 THEN
        RAISE EXCEPTION 'Found % unvalidated foreign keys', invalid_fk_count;
    END IF;

    SELECT count(*) INTO missing_sequence_count
      FROM unnest(expected_tables) AS name
     WHERE pg_get_serial_sequence('public.' || name, 'id') IS NULL;
    IF missing_sequence_count <> 0 THEN
        RAISE EXCEPTION '% expected tables lack working id sequences',
            missing_sequence_count;
    END IF;

    SELECT count(*) INTO table_count
      FROM information_schema.tables
     WHERE table_schema = 'public' AND table_type = 'BASE TABLE';
    IF table_count <> cardinality(expected_tables) THEN
        RAISE EXCEPTION 'Expected % tables, found %',
            cardinality(expected_tables), table_count;
    END IF;

    IF EXISTS (
        SELECT 1
          FROM pg_stat_user_tables
         WHERE schemaname = 'public' AND n_live_tup <> 0
    ) THEN
        RAISE EXCEPTION 'Canonical schema validation database contains rows';
    END IF;
END
$validation$;

SELECT count(*) AS table_count
  FROM information_schema.tables
 WHERE table_schema = 'public' AND table_type = 'BASE TABLE';

SELECT count(*) AS index_count
  FROM pg_indexes
 WHERE schemaname = 'public';
