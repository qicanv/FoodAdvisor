\set ON_ERROR_STOP on

BEGIN;

-- Rate-limit exceed events. No Authorization, JWT, or request payload is persisted.
CREATE TABLE IF NOT EXISTS rate_limit_events (
    id BIGSERIAL PRIMARY KEY,
    rule_name VARCHAR(100) NOT NULL,
    key_type VARCHAR(10) NOT NULL,
    subject_value VARCHAR(255) NOT NULL,
    user_id BIGINT,
    client_ip VARCHAR(64),
    request_method VARCHAR(10) NOT NULL,
    request_path VARCHAR(500) NOT NULL,
    limit_count INTEGER NOT NULL,
    window_seconds INTEGER NOT NULL,
    current_count BIGINT NOT NULL,
    retry_after_seconds INTEGER NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_rate_limit_events_key_type CHECK (key_type IN ('USER', 'IP')),
    CONSTRAINT ck_rate_limit_events_limit_count CHECK (limit_count > 0),
    CONSTRAINT ck_rate_limit_events_window_seconds CHECK (window_seconds > 0),
    CONSTRAINT ck_rate_limit_events_current_count CHECK (current_count > 0),
    CONSTRAINT ck_rate_limit_events_retry_after CHECK (retry_after_seconds >= 0),
    CONSTRAINT fk_rate_limit_events_user
        FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE SET NULL
);

CREATE INDEX IF NOT EXISTS idx_rate_limit_events_created
    ON rate_limit_events(created_at DESC);

CREATE INDEX IF NOT EXISTS idx_rate_limit_events_rule_created
    ON rate_limit_events(rule_name, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_rate_limit_events_user_created
    ON rate_limit_events(user_id, created_at DESC)
    WHERE user_id IS NOT NULL;

COMMIT;
