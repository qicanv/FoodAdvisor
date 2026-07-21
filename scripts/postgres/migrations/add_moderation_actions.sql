CREATE TABLE IF NOT EXISTS moderation_actions (
    id BIGSERIAL PRIMARY KEY,
    task_id BIGINT,
    operator_user_id BIGINT NOT NULL,
    action VARCHAR(20) NOT NULL,
    previous_status VARCHAR(20),
    new_status VARCHAR(20),
    remark TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_moderation_action_operator
        FOREIGN KEY (operator_user_id) REFERENCES users(id)
        ON DELETE RESTRICT,

    CONSTRAINT ck_moderation_action
        CHECK (action IN ('APPROVE', 'REJECT', 'DELETE', 'RETURN_FOR_MODIFICATION'))
);

CREATE INDEX IF NOT EXISTS idx_moderation_actions_task
    ON moderation_actions(task_id);

CREATE INDEX IF NOT EXISTS idx_moderation_actions_operator
    ON moderation_actions(operator_user_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_moderation_actions_action
    ON moderation_actions(action, created_at DESC);