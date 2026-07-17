-- ============================================
-- Audit logs for administrator troubleshooting.
-- This file is an incremental migration and can be run on existing databases.
-- ============================================

CREATE TABLE IF NOT EXISTS audit_logs (
    id BIGSERIAL PRIMARY KEY,
    operation_type VARCHAR(50) NOT NULL,
    operator_user_id BIGINT,
    operator_username VARCHAR(100),
    operator_role VARCHAR(30),
    module VARCHAR(80) NOT NULL,
    level VARCHAR(20) NOT NULL,
    result VARCHAR(20) NOT NULL,
    object_type VARCHAR(80),
    object_id VARCHAR(100),
    error_code VARCHAR(100),
    error_message TEXT,
    request_method VARCHAR(20),
    request_uri VARCHAR(500),
    ip_address VARCHAR(100),
    user_agent VARCHAR(500),
    business_trace_id VARCHAR(100),
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_audit_logs_operator
        FOREIGN KEY (operator_user_id) REFERENCES users(id)
        ON DELETE SET NULL,

    CONSTRAINT ck_audit_operation_type
        CHECK (operation_type IN (
            'LOGIN',
            'ADMIN_OPERATION',
            'AI_CALL',
            'API_EXCEPTION',
            'DATA_IMPORT',
            'CONTENT_MODERATION'
        )),

    CONSTRAINT ck_audit_level
        CHECK (level IN ('INFO', 'WARN', 'ERROR')),

    CONSTRAINT ck_audit_result
        CHECK (result IN ('SUCCESS', 'FAILURE'))
);

CREATE INDEX IF NOT EXISTS idx_audit_logs_created_at
    ON audit_logs(created_at DESC);

CREATE INDEX IF NOT EXISTS idx_audit_logs_operator_user_id
    ON audit_logs(operator_user_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_audit_logs_operator_username
    ON audit_logs(operator_username, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_audit_logs_module
    ON audit_logs(module, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_audit_logs_level
    ON audit_logs(level, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_audit_logs_operation_type
    ON audit_logs(operation_type, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_audit_logs_object
    ON audit_logs(object_type, object_id, created_at DESC);
