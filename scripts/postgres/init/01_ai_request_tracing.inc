-- Root AI request traces. Payload columns contain sanitized, allow-listed summaries only.
CREATE TABLE ai_request_traces (
    id BIGSERIAL PRIMARY KEY,
    trace_id VARCHAR(100) NOT NULL,
    request_id VARCHAR(100),
    session_id BIGINT,
    user_id BIGINT,
    scene VARCHAR(100) NOT NULL,
    intent VARCHAR(100),
    structured_conditions JSONB NOT NULL DEFAULT '{}'::jsonb,
    provider VARCHAR(100),
    model_name VARCHAR(200),
    model_version VARCHAR(100),
    prompt_version VARCHAR(100),
    status VARCHAR(20) NOT NULL,
    final_output_summary JSONB NOT NULL DEFAULT '{}'::jsonb,
    error_code VARCHAR(100),
    error_message VARCHAR(500),
    started_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMPTZ,
    total_duration_ms BIGINT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_ai_request_traces_trace UNIQUE (trace_id),
    CONSTRAINT ck_ai_request_traces_status
        CHECK (status IN ('RUNNING', 'SUCCESS', 'FAILED', 'FALLBACK')),
    CONSTRAINT ck_ai_request_traces_duration
        CHECK (total_duration_ms IS NULL OR total_duration_ms >= 0),
    CONSTRAINT fk_ai_request_traces_session
        FOREIGN KEY (session_id) REFERENCES chat_sessions(id) ON DELETE SET NULL,
    CONSTRAINT fk_ai_request_traces_user
        FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE SET NULL
);

CREATE TABLE ai_request_trace_stages (
    id BIGSERIAL PRIMARY KEY,
    trace_id VARCHAR(100) NOT NULL,
    stage_name VARCHAR(100) NOT NULL,
    sequence_no INTEGER NOT NULL,
    attempt_no INTEGER NOT NULL DEFAULT 1,
    status VARCHAR(20) NOT NULL,
    input_summary JSONB NOT NULL DEFAULT '{}'::jsonb,
    output_summary JSONB NOT NULL DEFAULT '{}'::jsonb,
    provider VARCHAR(100),
    model_name VARCHAR(200),
    model_version VARCHAR(100),
    prompt_version VARCHAR(100),
    duration_ms BIGINT,
    error_code VARCHAR(100),
    error_message VARCHAR(500),
    started_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_ai_request_trace_stage
        UNIQUE (trace_id, sequence_no, attempt_no),
    CONSTRAINT ck_ai_request_trace_stages_status
        CHECK (status IN ('RUNNING', 'SUCCESS', 'FAILED', 'FALLBACK', 'SKIPPED')),
    CONSTRAINT ck_ai_request_trace_stages_sequence
        CHECK (sequence_no > 0),
    CONSTRAINT ck_ai_request_trace_stages_attempt
        CHECK (attempt_no > 0),
    CONSTRAINT ck_ai_request_trace_stages_duration
        CHECK (duration_ms IS NULL OR duration_ms >= 0),
    CONSTRAINT fk_ai_request_trace_stages_trace
        FOREIGN KEY (trace_id) REFERENCES ai_request_traces(trace_id) ON DELETE CASCADE
);

CREATE TABLE ai_trace_retrieval_sources (
    id BIGSERIAL PRIMARY KEY,
    trace_id VARCHAR(100) NOT NULL,
    stage_id BIGINT,
    source_type VARCHAR(50) NOT NULL,
    source_id VARCHAR(100),
    document_id VARCHAR(200),
    chunk_id VARCHAR(200),
    merchant_id BIGINT,
    merchant_name VARCHAR(200),
    summary VARCHAR(500),
    rank_no INTEGER,
    relevance_score NUMERIC(12, 8),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_ai_trace_sources_trace
        FOREIGN KEY (trace_id) REFERENCES ai_request_traces(trace_id) ON DELETE CASCADE,
    CONSTRAINT fk_ai_trace_sources_stage
        FOREIGN KEY (stage_id) REFERENCES ai_request_trace_stages(id) ON DELETE SET NULL,
    CONSTRAINT fk_ai_trace_sources_merchant
        FOREIGN KEY (merchant_id) REFERENCES merchants(id) ON DELETE SET NULL,
    CONSTRAINT ck_ai_trace_sources_rank CHECK (rank_no IS NULL OR rank_no > 0)
);

ALTER TABLE ai_call_logs
    ADD COLUMN root_trace_id VARCHAR(100),
    ADD COLUMN stage_name VARCHAR(100),
    ADD CONSTRAINT fk_ai_call_logs_root_trace
        FOREIGN KEY (root_trace_id) REFERENCES ai_request_traces(trace_id) ON DELETE SET NULL;
