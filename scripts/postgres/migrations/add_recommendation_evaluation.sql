-- ============================================================
-- 推荐结果评测模块
-- ============================================================

-- 1. 标准测试集
CREATE TABLE IF NOT EXISTS recommendation_eval_datasets (
    id              BIGSERIAL PRIMARY KEY,
    name            VARCHAR(200) NOT NULL,
    description     TEXT,
    data_version    VARCHAR(100),
    status          VARCHAR(30) NOT NULL DEFAULT 'DRAFT',
    created_by      BIGINT,
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT ck_recommendation_eval_dataset_status
        CHECK (status IN ('DRAFT', 'ACTIVE', 'ARCHIVED'))
);

-- 2. 标准测试案例
CREATE TABLE IF NOT EXISTS recommendation_eval_cases (
    id                      BIGSERIAL PRIMARY KEY,
    dataset_id              BIGINT NOT NULL,
    case_code               VARCHAR(100) NOT NULL,
    case_name               VARCHAR(200),
    input_text              TEXT NOT NULL,
    expected_constraints    JSONB NOT NULL DEFAULT '{}'::jsonb,
    location_snapshot       JSONB NOT NULL DEFAULT '{}'::jsonb,
    tags                    JSONB NOT NULL DEFAULT '[]'::jsonb,
    sequence_no             INTEGER NOT NULL DEFAULT 0,
    enabled                 BOOLEAN NOT NULL DEFAULT TRUE,
    created_at              TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at              TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_recommendation_eval_case_dataset
        FOREIGN KEY (dataset_id)
        REFERENCES recommendation_eval_datasets(id)
        ON DELETE CASCADE,

    CONSTRAINT uk_recommendation_eval_case_code
        UNIQUE (dataset_id, case_code)
);

-- 3. 一次批量评测运行
CREATE TABLE IF NOT EXISTS recommendation_eval_runs (
    id                      BIGSERIAL PRIMARY KEY,
    dataset_id              BIGINT NOT NULL,
    status                  VARCHAR(30) NOT NULL DEFAULT 'PENDING',

    model_name              VARCHAR(150),
    model_version           VARCHAR(100),
    prompt_version          VARCHAR(100),
    algorithm_version       VARCHAR(100),
    data_version            VARCHAR(100),

    requested_count         INTEGER NOT NULL DEFAULT 0,
    success_count           INTEGER NOT NULL DEFAULT 0,
    failed_count            INTEGER NOT NULL DEFAULT 0,
    unique_merchant_count   INTEGER NOT NULL DEFAULT 0,

    metrics                 JSONB NOT NULL DEFAULT '{}'::jsonb,
    error_message           TEXT,

    created_by              BIGINT,
    started_at              TIMESTAMP,
    completed_at            TIMESTAMP,
    created_at              TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at              TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_recommendation_eval_run_dataset
        FOREIGN KEY (dataset_id)
        REFERENCES recommendation_eval_datasets(id)
        ON DELETE RESTRICT,

    CONSTRAINT ck_recommendation_eval_run_status
        CHECK (
            status IN (
                'PENDING',
                'RUNNING',
                'COMPLETED',
                'PARTIAL',
                'FAILED'
            )
        )
);

-- 4. 单个测试案例的运行结果
CREATE TABLE IF NOT EXISTS recommendation_eval_case_results (
    id                          BIGSERIAL PRIMARY KEY,
    run_id                      BIGINT NOT NULL,
    case_id                     BIGINT NOT NULL,

    status                      VARCHAR(30) NOT NULL,
    trace_id                    VARCHAR(100),

    input_snapshot              TEXT,
    expected_constraints        JSONB NOT NULL DEFAULT '{}'::jsonb,
    extracted_constraints       JSONB NOT NULL DEFAULT '{}'::jsonb,
    merged_constraints          JSONB NOT NULL DEFAULT '{}'::jsonb,

    recommendation_snapshot     JSONB NOT NULL DEFAULT '[]'::jsonb,
    hard_condition_metrics      JSONB NOT NULL DEFAULT '{}'::jsonb,
    failure_reasons             JSONB NOT NULL DEFAULT '[]'::jsonb,

    result_count                INTEGER NOT NULL DEFAULT 0,
    duration_ms                 BIGINT,
    error_message               TEXT,

    relevance_label             VARCHAR(30),
    annotation_note             TEXT,
    annotated_by                BIGINT,
    annotated_at                TIMESTAMP,

    created_at                  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at                  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_recommendation_eval_result_run
        FOREIGN KEY (run_id)
        REFERENCES recommendation_eval_runs(id)
        ON DELETE CASCADE,

    CONSTRAINT fk_recommendation_eval_result_case
        FOREIGN KEY (case_id)
        REFERENCES recommendation_eval_cases(id)
        ON DELETE RESTRICT,

    CONSTRAINT uk_recommendation_eval_run_case
        UNIQUE (run_id, case_id),

    CONSTRAINT ck_recommendation_eval_result_status
        CHECK (
            status IN (
                'SUCCESS',
                'FAILED',
                'SKIPPED'
            )
        ),

    CONSTRAINT ck_recommendation_eval_relevance
        CHECK (
            relevance_label IS NULL
            OR relevance_label IN (
                'RELEVANT',
                'PARTIALLY_RELEVANT',
                'IRRELEVANT'
            )
        )
);

-- ============================================================
-- 推荐结果评测模块索引
-- ============================================================

CREATE INDEX IF NOT EXISTS idx_recommendation_eval_datasets_status
    ON recommendation_eval_datasets(status);

CREATE INDEX IF NOT EXISTS idx_recommendation_eval_datasets_created_at
    ON recommendation_eval_datasets(created_at DESC);

CREATE INDEX IF NOT EXISTS idx_recommendation_eval_cases_dataset
    ON recommendation_eval_cases(dataset_id, enabled, sequence_no);

CREATE INDEX IF NOT EXISTS idx_recommendation_eval_runs_dataset
    ON recommendation_eval_runs(dataset_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_recommendation_eval_runs_status
    ON recommendation_eval_runs(status);

CREATE INDEX IF NOT EXISTS idx_recommendation_eval_runs_created_at
    ON recommendation_eval_runs(created_at DESC);

CREATE INDEX IF NOT EXISTS idx_recommendation_eval_case_results_run
    ON recommendation_eval_case_results(run_id);

CREATE INDEX IF NOT EXISTS idx_recommendation_eval_case_results_case
    ON recommendation_eval_case_results(case_id);

CREATE INDEX IF NOT EXISTS idx_recommendation_eval_case_results_status
    ON recommendation_eval_case_results(status);

CREATE INDEX IF NOT EXISTS idx_recommendation_eval_case_results_relevance
    ON recommendation_eval_case_results(relevance_label);

CREATE INDEX IF NOT EXISTS idx_recommendation_eval_case_results_created_at
    ON recommendation_eval_case_results(created_at DESC);