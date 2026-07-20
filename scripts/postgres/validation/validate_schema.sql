\set ON_ERROR_STOP on

DO $validation$
DECLARE
    expected_tables text[] := ARRAY[
        'ai_call_logs', 'ai_request_trace_stages', 'ai_request_traces',
        'ai_trace_retrieval_sources', 'audit_logs', 'auth_sessions', 'chat_messages',
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
    invalid_fk_count integer;
    missing_sequence_count integer;
    missing_trace_constraints text[];
    missing_trace_indexes text[];
    invalid_trace_column_count integer;
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

    IF (
        SELECT count(*) FROM information_schema.columns
         WHERE table_schema = 'public' AND table_name = 'ai_call_logs'
           AND column_name IN ('root_trace_id', 'stage_name')
    ) <> 2 THEN
        RAISE EXCEPTION 'ai_call_logs tracing columns are missing';
    END IF;
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
         WHERE conrelid = 'ai_call_logs'::regclass
           AND conname = 'uk_ai_call_trace'
    ) THEN
        RAISE EXCEPTION 'ai_call_logs.trace_id unique constraint is missing';
    END IF;
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
         WHERE conrelid = 'ai_request_traces'::regclass
           AND conname = 'uk_ai_request_traces_trace'
    ) OR NOT EXISTS (
        SELECT 1 FROM pg_constraint
         WHERE conrelid = 'ai_request_trace_stages'::regclass
           AND conname = 'uk_ai_request_trace_stage'
    ) THEN
        RAISE EXCEPTION 'AI request trace unique constraints are missing';
    END IF;

    SELECT array_agg(name ORDER BY name)
      INTO missing_trace_constraints
      FROM unnest(ARRAY[
          'ck_ai_request_traces_status',
          'ck_ai_request_traces_duration',
          'ck_ai_request_trace_stages_status',
          'ck_ai_request_trace_stages_sequence',
          'ck_ai_request_trace_stages_attempt',
          'ck_ai_request_trace_stages_duration',
          'ck_ai_trace_sources_rank',
          'fk_ai_request_traces_session',
          'fk_ai_request_traces_user',
          'fk_ai_request_trace_stages_trace',
          'fk_ai_trace_sources_trace',
          'fk_ai_trace_sources_stage',
          'fk_ai_trace_sources_merchant',
          'fk_ai_call_logs_root_trace'
      ]) AS name
     WHERE NOT EXISTS (
         SELECT 1 FROM pg_constraint WHERE conname = name
     );
    IF missing_trace_constraints IS NOT NULL THEN
        RAISE EXCEPTION 'AI request trace constraints are missing: %', missing_trace_constraints;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
         WHERE conname = 'ck_ai_request_traces_status'
           AND pg_get_constraintdef(oid) LIKE '%RUNNING%SUCCESS%FAILED%FALLBACK%'
    ) OR NOT EXISTS (
        SELECT 1 FROM pg_constraint
         WHERE conname = 'ck_ai_request_trace_stages_status'
           AND pg_get_constraintdef(oid) LIKE '%RUNNING%SUCCESS%FAILED%FALLBACK%SKIPPED%'
    ) THEN
        RAISE EXCEPTION 'AI request trace status checks are invalid';
    END IF;

    SELECT array_agg(name ORDER BY name)
      INTO missing_trace_indexes
      FROM unnest(ARRAY[
          'idx_ai_request_traces_created',
          'idx_ai_request_traces_started',
          'idx_ai_request_traces_request',
          'idx_ai_request_traces_session',
          'idx_ai_request_traces_scene_status',
          'idx_ai_request_trace_stages_trace',
          'idx_ai_request_trace_stages_started',
          'idx_ai_trace_sources_trace',
          'idx_ai_call_logs_root_trace'
      ]) AS name
     WHERE NOT EXISTS (
         SELECT 1 FROM pg_indexes
          WHERE schemaname = 'public' AND indexname = name
     );
    IF missing_trace_indexes IS NOT NULL THEN
        RAISE EXCEPTION 'AI request trace indexes are missing: %', missing_trace_indexes;
    END IF;

    SELECT count(*) INTO invalid_trace_column_count
      FROM (VALUES
          ('ai_request_traces', 'trace_id', 'character varying', 100, 'NO'),
          ('ai_request_traces', 'scene', 'character varying', 100, 'NO'),
          ('ai_request_traces', 'status', 'character varying', 20, 'NO'),
          ('ai_request_trace_stages', 'stage_name', 'character varying', 100, 'NO'),
          ('ai_request_trace_stages', 'sequence_no', 'integer', NULL::integer, 'NO'),
          ('ai_request_trace_stages', 'attempt_no', 'integer', NULL::integer, 'NO'),
          ('ai_request_trace_stages', 'duration_ms', 'bigint', NULL::integer, 'YES'),
          ('ai_call_logs', 'root_trace_id', 'character varying', 100, 'YES'),
          ('ai_call_logs', 'stage_name', 'character varying', 100, 'YES')
      ) AS expected(table_name, column_name, data_type, max_length, nullable)
     WHERE NOT EXISTS (
         SELECT 1 FROM information_schema.columns c
          WHERE c.table_schema = 'public'
            AND c.table_name = expected.table_name
            AND c.column_name = expected.column_name
            AND c.data_type = expected.data_type
            AND (expected.max_length IS NULL
                 OR c.character_maximum_length = expected.max_length)
            AND c.is_nullable = expected.nullable
     );
    IF invalid_trace_column_count <> 0 THEN
        RAISE EXCEPTION 'AI request trace field types, lengths, or nullability are invalid';
    END IF;
    IF (
        SELECT count(*) FROM information_schema.columns
         WHERE table_schema = 'public' AND column_default IS NOT NULL
           AND (table_name, column_name) IN (
               ('ai_request_traces', 'structured_conditions'),
               ('ai_request_traces', 'final_output_summary'),
               ('ai_request_traces', 'started_at'),
               ('ai_request_trace_stages', 'attempt_no'),
               ('ai_request_trace_stages', 'input_summary'),
               ('ai_request_trace_stages', 'output_summary'),
               ('ai_request_trace_stages', 'started_at')
           )
    ) <> 7 THEN
        RAISE EXCEPTION 'AI request trace defaults are missing';
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

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
         WHERE table_schema = 'public'
           AND table_name = 'recommendation_evidences'
           AND column_name = 'condition_key'
    ) THEN
        RAISE EXCEPTION
            'recommendation_evidences.condition_key is missing';
    END IF;

    IF (
        SELECT count(*) FROM information_schema.columns
         WHERE table_schema = 'public'
           AND table_name = 'merchant_summary_evidences'
           AND column_name IN (
               'source_type', 'source_merchant_id', 'review_version'
           )
    ) <> 3 THEN
        RAISE EXCEPTION
            'merchant_summary_evidences traceability columns are missing';
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
         WHERE conrelid = 'merchant_summary_evidences'::regclass
           AND conname = 'ck_summary_evidence_source_type'
    ) OR NOT EXISTS (
        SELECT 1 FROM pg_constraint
         WHERE conrelid = 'merchant_summary_evidences'::regclass
           AND conname = 'ck_summary_evidence_type'
    ) THEN
        RAISE EXCEPTION
            'merchant_summary_evidences source/type checks are missing';
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

--     SELECT count(*) INTO table_count
--       FROM information_schema.tables
--      WHERE table_schema = 'public' AND table_type = 'BASE TABLE';
--     IF table_count <> cardinality(expected_tables) THEN
--         RAISE EXCEPTION 'Expected % tables, found %',
--             cardinality(expected_tables), table_count;
--     END IF;

--     IF EXISTS (
--         SELECT 1
--           FROM pg_stat_user_tables
--          WHERE schemaname = 'public' AND n_live_tup <> 0
--     ) THEN
--         RAISE EXCEPTION 'Canonical schema validation database contains rows';
--     END IF;
END
$validation$;

SELECT count(*) AS table_count
  FROM information_schema.tables
 WHERE table_schema = 'public' AND table_type = 'BASE TABLE';

SELECT count(*) AS index_count
  FROM pg_indexes
 WHERE schemaname = 'public';
