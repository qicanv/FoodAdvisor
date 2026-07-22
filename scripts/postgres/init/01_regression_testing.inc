-- ============================================================
-- 通用回归测试模块
-- 支持：
--   1. 需求提取
--   2. 推荐结果
--   3. 评价摘要
--   4. 情感分析
-- ============================================================

-- 1. 标准测试集
CREATE TABLE IF NOT EXISTS regression_test_sets (
    id                  BIGSERIAL PRIMARY KEY,
    name                VARCHAR(200) NOT NULL,
    description         TEXT,
    test_type           VARCHAR(50) NOT NULL,
    data_version        VARCHAR(100) NOT NULL,
    status              VARCHAR(30) NOT NULL DEFAULT 'DRAFT',
    metadata            JSONB NOT NULL DEFAULT '{}'::jsonb,

    created_by          BIGINT,
    created_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT ck_regression_test_set_type
        CHECK (
            test_type IN (
                'CONSTRAINT_EXTRACTION',
                'RECOMMENDATION',
                'REVIEW_SUMMARY',
                'SENTIMENT_ANALYSIS'
            )
        ),

    CONSTRAINT ck_regression_test_set_status
        CHECK (
            status IN (
                'DRAFT',
                'ACTIVE',
                'ARCHIVED'
            )
        )
);

-- 2. 标准测试案例
CREATE TABLE IF NOT EXISTS regression_test_cases (
    id                  BIGSERIAL PRIMARY KEY,
    test_set_id         BIGINT NOT NULL,
    case_code           VARCHAR(100) NOT NULL,
    case_name           VARCHAR(200),
    description         TEXT,

    input_payload       JSONB NOT NULL DEFAULT '{}'::jsonb,
    expected_output     JSONB NOT NULL DEFAULT '{}'::jsonb,
    assertion_config    JSONB NOT NULL DEFAULT '{}'::jsonb,
    tags                JSONB NOT NULL DEFAULT '[]'::jsonb,

    sequence_no         INTEGER NOT NULL DEFAULT 0,
    enabled             BOOLEAN NOT NULL DEFAULT TRUE,

    created_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_regression_test_case_set
        FOREIGN KEY (test_set_id)
        REFERENCES regression_test_sets(id)
        ON DELETE CASCADE,

    CONSTRAINT uk_regression_test_case_code
        UNIQUE (test_set_id, case_code)
);

-- 3. 一次回归测试批量任务
-- 一次任务可以同时选择多个测试集。
CREATE TABLE IF NOT EXISTS regression_test_runs (
    id                          BIGSERIAL PRIMARY KEY,
    run_name                    VARCHAR(200),
    baseline_run_id             BIGINT,
    status                      VARCHAR(30) NOT NULL DEFAULT 'PENDING',

    model_versions              JSONB NOT NULL DEFAULT '{}'::jsonb,
    prompt_versions             JSONB NOT NULL DEFAULT '{}'::jsonb,
    algorithm_versions          JSONB NOT NULL DEFAULT '{}'::jsonb,
    data_versions               JSONB NOT NULL DEFAULT '{}'::jsonb,
    request_snapshot            JSONB NOT NULL DEFAULT '{}'::jsonb,

    requested_set_count         INTEGER NOT NULL DEFAULT 0,
    completed_set_count         INTEGER NOT NULL DEFAULT 0,
    requested_case_count        INTEGER NOT NULL DEFAULT 0,
    completed_case_count        INTEGER NOT NULL DEFAULT 0,

    passed_count                INTEGER NOT NULL DEFAULT 0,
    assertion_failed_count      INTEGER NOT NULL DEFAULT 0,
    execution_error_count       INTEGER NOT NULL DEFAULT 0,

    progress_percent            NUMERIC(5, 2) NOT NULL DEFAULT 0,
    error_message               TEXT,

    created_by                  BIGINT,
    started_at                  TIMESTAMP,
    completed_at                TIMESTAMP,
    created_at                  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at                  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_regression_test_run_baseline
        FOREIGN KEY (baseline_run_id)
        REFERENCES regression_test_runs(id)
        ON DELETE SET NULL,

    CONSTRAINT ck_regression_test_run_status
        CHECK (
            status IN (
                'PENDING',
                'RUNNING',
                'COMPLETED',
                'PARTIAL',
                'FAILED'
            )
        ),

    CONSTRAINT ck_regression_test_run_progress
        CHECK (
            progress_percent >= 0
            AND progress_percent <= 100
        )
);

-- 4. 回归任务与测试集的关联及分组进度
CREATE TABLE IF NOT EXISTS regression_test_run_sets (
    id                          BIGSERIAL PRIMARY KEY,
    run_id                      BIGINT NOT NULL,
    test_set_id                 BIGINT NOT NULL,
    test_type                   VARCHAR(50) NOT NULL,
    status                      VARCHAR(30) NOT NULL DEFAULT 'PENDING',

    model_name                  VARCHAR(150),
    model_version               VARCHAR(100),
    prompt_version              VARCHAR(100),
    algorithm_version           VARCHAR(100),
    data_version                VARCHAR(100),

    requested_case_count        INTEGER NOT NULL DEFAULT 0,
    completed_case_count        INTEGER NOT NULL DEFAULT 0,
    passed_count                INTEGER NOT NULL DEFAULT 0,
    assertion_failed_count      INTEGER NOT NULL DEFAULT 0,
    execution_error_count       INTEGER NOT NULL DEFAULT 0,

    progress_percent            NUMERIC(5, 2) NOT NULL DEFAULT 0,
    error_message               TEXT,

    started_at                  TIMESTAMP,
    completed_at                TIMESTAMP,
    created_at                  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at                  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_regression_test_run_set_run
        FOREIGN KEY (run_id)
        REFERENCES regression_test_runs(id)
        ON DELETE CASCADE,

    CONSTRAINT fk_regression_test_run_set_test_set
        FOREIGN KEY (test_set_id)
        REFERENCES regression_test_sets(id)
        ON DELETE RESTRICT,

    CONSTRAINT uk_regression_test_run_set
        UNIQUE (run_id, test_set_id),

    CONSTRAINT ck_regression_test_run_set_type
        CHECK (
            test_type IN (
                'CONSTRAINT_EXTRACTION',
                'RECOMMENDATION',
                'REVIEW_SUMMARY',
                'SENTIMENT_ANALYSIS'
            )
        ),

    CONSTRAINT ck_regression_test_run_set_status
        CHECK (
            status IN (
                'PENDING',
                'RUNNING',
                'COMPLETED',
                'PARTIAL',
                'FAILED',
                'SKIPPED'
            )
        ),

    CONSTRAINT ck_regression_test_run_set_progress
        CHECK (
            progress_percent >= 0
            AND progress_percent <= 100
        )
);

-- 5. 单个测试案例的运行结果
CREATE TABLE IF NOT EXISTS regression_test_case_results (
    id                          BIGSERIAL PRIMARY KEY,
    run_id                      BIGINT NOT NULL,
    run_set_id                  BIGINT NOT NULL,
    case_id                     BIGINT NOT NULL,
    baseline_result_id          BIGINT,

    case_code_snapshot          VARCHAR(100) NOT NULL,
    test_type                   VARCHAR(50) NOT NULL,

    execution_status            VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    assertion_status            VARCHAR(30) NOT NULL DEFAULT 'NOT_EVALUATED',
    comparison_status           VARCHAR(30) NOT NULL DEFAULT 'NOT_COMPARED',

    model_name                  VARCHAR(150),
    model_version               VARCHAR(100),
    prompt_version              VARCHAR(100),
    algorithm_version           VARCHAR(100),
    data_version                VARCHAR(100),

    input_snapshot              JSONB NOT NULL DEFAULT '{}'::jsonb,
    expected_snapshot           JSONB NOT NULL DEFAULT '{}'::jsonb,
    actual_output               JSONB NOT NULL DEFAULT '{}'::jsonb,
    metrics                     JSONB NOT NULL DEFAULT '{}'::jsonb,
    failure_reasons             JSONB NOT NULL DEFAULT '[]'::jsonb,

    trace_id                    VARCHAR(100),
    duration_ms                 BIGINT,
    error_message               TEXT,

    started_at                  TIMESTAMP,
    completed_at                TIMESTAMP,
    created_at                  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at                  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_regression_test_result_run
        FOREIGN KEY (run_id)
        REFERENCES regression_test_runs(id)
        ON DELETE CASCADE,

    CONSTRAINT fk_regression_test_result_run_set
        FOREIGN KEY (run_set_id)
        REFERENCES regression_test_run_sets(id)
        ON DELETE CASCADE,

    CONSTRAINT fk_regression_test_result_case
        FOREIGN KEY (case_id)
        REFERENCES regression_test_cases(id)
        ON DELETE RESTRICT,

    CONSTRAINT fk_regression_test_result_baseline
        FOREIGN KEY (baseline_result_id)
        REFERENCES regression_test_case_results(id)
        ON DELETE SET NULL,

    CONSTRAINT uk_regression_test_run_case
        UNIQUE (run_id, case_id),

    CONSTRAINT ck_regression_test_result_type
        CHECK (
            test_type IN (
                'CONSTRAINT_EXTRACTION',
                'RECOMMENDATION',
                'REVIEW_SUMMARY',
                'SENTIMENT_ANALYSIS'
            )
        ),

    CONSTRAINT ck_regression_test_execution_status
        CHECK (
            execution_status IN (
                'PENDING',
                'RUNNING',
                'SUCCESS',
                'FAILED',
                'SKIPPED'
            )
        ),

    CONSTRAINT ck_regression_test_assertion_status
        CHECK (
            assertion_status IN (
                'PASSED',
                'FAILED',
                'NOT_EVALUATED'
            )
        ),

    CONSTRAINT ck_regression_test_comparison_status
        CHECK (
            comparison_status IN (
                'NOT_COMPARED',
                'NEW_FAILURE',
                'FIXED',
                'STILL_FAILED',
                'STILL_PASSED',
                'NEW_CASE',
                'REMOVED_CASE'
            )
        )
);

-- ============================================================
-- 索引
-- ============================================================

CREATE INDEX IF NOT EXISTS idx_regression_test_sets_type_status
    ON regression_test_sets(test_type, status);

CREATE INDEX IF NOT EXISTS idx_regression_test_sets_created_at
    ON regression_test_sets(created_at DESC);

CREATE INDEX IF NOT EXISTS idx_regression_test_cases_set_enabled
    ON regression_test_cases(test_set_id, enabled, sequence_no);

CREATE INDEX IF NOT EXISTS idx_regression_test_runs_status_created
    ON regression_test_runs(status, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_regression_test_runs_baseline
    ON regression_test_runs(baseline_run_id);

CREATE INDEX IF NOT EXISTS idx_regression_test_run_sets_run
    ON regression_test_run_sets(run_id, status);

CREATE INDEX IF NOT EXISTS idx_regression_test_run_sets_test_set
    ON regression_test_run_sets(test_set_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_regression_test_results_run
    ON regression_test_case_results(run_id, id);

CREATE INDEX IF NOT EXISTS idx_regression_test_results_run_set
    ON regression_test_case_results(run_set_id, id);

CREATE INDEX IF NOT EXISTS idx_regression_test_results_assertion
    ON regression_test_case_results(
        run_id,
        assertion_status
    );

CREATE INDEX IF NOT EXISTS idx_regression_test_results_comparison
    ON regression_test_case_results(
        run_id,
        comparison_status
    );

CREATE INDEX IF NOT EXISTS idx_regression_test_results_case
    ON regression_test_case_results(case_id, created_at DESC);