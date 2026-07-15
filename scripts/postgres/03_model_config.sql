BEGIN;

CREATE TABLE IF NOT EXISTS model_configs (
    id BIGSERIAL PRIMARY KEY,

    config_name VARCHAR(100) NOT NULL,
    provider VARCHAR(50) NOT NULL DEFAULT 'OPENAI_COMPATIBLE',
    model_name VARCHAR(100) NOT NULL,
    base_url VARCHAR(500) NOT NULL,
    encrypted_api_key TEXT NOT NULL,

    timeout_ms INTEGER NOT NULL DEFAULT 30000,
    temperature NUMERIC(3, 2) NOT NULL DEFAULT 0.70,
    max_output_tokens INTEGER NOT NULL DEFAULT 1024,

    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    last_test_status VARCHAR(20),
    last_test_message VARCHAR(500),
    last_tested_at TIMESTAMPTZ,

    created_by BIGINT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uk_model_configs_config_name
        UNIQUE (config_name),

    CONSTRAINT ck_model_configs_timeout
        CHECK (timeout_ms BETWEEN 1000 AND 120000),

    CONSTRAINT ck_model_configs_temperature
        CHECK (temperature BETWEEN 0 AND 2),

    CONSTRAINT ck_model_configs_max_output_tokens
        CHECK (max_output_tokens BETWEEN 1 AND 32000),

    CONSTRAINT ck_model_configs_status
        CHECK (status IN ('ACTIVE', 'DISABLED')),

    CONSTRAINT ck_model_configs_last_test_status
        CHECK (
            last_test_status IS NULL
            OR last_test_status IN ('SUCCESS', 'FAILED')
        )
);

CREATE TABLE IF NOT EXISTS model_scene_bindings (
    id BIGSERIAL PRIMARY KEY,

    scene_type VARCHAR(50) NOT NULL,
    model_config_id BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',

    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_scene_bindings_model_config
        FOREIGN KEY (model_config_id)
        REFERENCES model_configs(id)
        ON DELETE RESTRICT,

    CONSTRAINT uk_model_scene_bindings_scene
        UNIQUE (scene_type),

    CONSTRAINT ck_model_scene_bindings_scene
        CHECK (
            scene_type IN (
                'STORE_RECOMMENDATION',
                'REVIEW_SUMMARY',
                'REVIEW_REPLY'
            )
        ),

    CONSTRAINT ck_model_scene_bindings_status
        CHECK (status IN ('ACTIVE', 'DISABLED'))
);

CREATE INDEX IF NOT EXISTS idx_model_configs_status
    ON model_configs(status);

CREATE INDEX IF NOT EXISTS idx_model_scene_bindings_config
    ON model_scene_bindings(model_config_id, status);

COMMIT;
