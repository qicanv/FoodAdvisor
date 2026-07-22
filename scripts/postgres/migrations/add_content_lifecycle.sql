-- ============================================
-- 内容生命周期管理：状态变更历史 + OpenSearch 同步任务
-- 对应 EPIC-04 故事8
-- 参考 docs/数据库设计.md 6.20、6.22 节
-- ============================================

BEGIN;

-- ============================================
-- 1. 内容状态变更历史表（数据库设计文档 6.22 节）
-- ============================================
CREATE TABLE IF NOT EXISTS content_status_history (
    id BIGSERIAL PRIMARY KEY,
    content_type VARCHAR(30) NOT NULL,
    content_id BIGINT NOT NULL,
    old_status VARCHAR(30),
    new_status VARCHAR(30) NOT NULL,
    operator_user_id BIGINT,
    reason TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT ck_csh_content_type
        CHECK (content_type IN ('MERCHANT', 'DISH', 'TOPIC', 'KNOWLEDGE'))
);

CREATE INDEX IF NOT EXISTS idx_csh_content
    ON content_status_history(content_type, content_id, created_at DESC);

COMMENT ON TABLE content_status_history IS '内容状态变更历史';
COMMENT ON COLUMN content_status_history.content_type IS '内容类型：MERCHANT/DISH/TOPIC/KNOWLEDGE';
COMMENT ON COLUMN content_status_history.content_id IS '内容主键ID';
COMMENT ON COLUMN content_status_history.old_status IS '变更前状态';
COMMENT ON COLUMN content_status_history.new_status IS '变更后状态';
COMMENT ON COLUMN content_status_history.operator_user_id IS '操作人员用户ID';
COMMENT ON COLUMN content_status_history.reason IS '变更原因';

-- ============================================
-- 2. OpenSearch 同步任务表（数据库设计文档 6.20 节）
-- ============================================
CREATE TABLE IF NOT EXISTS opensearch_sync_tasks (
    id BIGSERIAL PRIMARY KEY,
    source_type VARCHAR(30) NOT NULL,
    source_id BIGINT NOT NULL,
    operation_type VARCHAR(20) NOT NULL,
    content_version INTEGER NOT NULL DEFAULT 1,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    retry_count INTEGER NOT NULL DEFAULT 0,
    next_retry_at TIMESTAMPTZ,
    error_message TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT ck_ost_source_type
        CHECK (source_type IN ('MERCHANT', 'DISH', 'REVIEW', 'TOPIC')),

    CONSTRAINT ck_ost_operation_type
        CHECK (operation_type IN ('UPSERT', 'DISABLE', 'DELETE', 'REINDEX')),

    CONSTRAINT ck_ost_status
        CHECK (status IN ('PENDING', 'PROCESSING', 'SUCCESS', 'FAILED')),

    CONSTRAINT ck_ost_retry_count
        CHECK (retry_count >= 0),

    CONSTRAINT ck_ost_content_version
        CHECK (content_version >= 1)
);

CREATE INDEX IF NOT EXISTS idx_ost_status
    ON opensearch_sync_tasks(status, next_retry_at);

CREATE INDEX IF NOT EXISTS idx_ost_source
    ON opensearch_sync_tasks(source_type, source_id);

COMMENT ON TABLE opensearch_sync_tasks IS 'OpenSearch 同步任务';
COMMENT ON COLUMN opensearch_sync_tasks.source_type IS '来源类型：MERCHANT/DISH/REVIEW/TOPIC';
COMMENT ON COLUMN opensearch_sync_tasks.source_id IS '来源主键ID';
COMMENT ON COLUMN opensearch_sync_tasks.operation_type IS '操作类型：UPSERT/DISABLE/DELETE/REINDEX';
COMMENT ON COLUMN opensearch_sync_tasks.status IS '同步状态：PENDING/PROCESSING/SUCCESS/FAILED';
COMMENT ON COLUMN opensearch_sync_tasks.retry_count IS '已重试次数';
COMMENT ON COLUMN opensearch_sync_tasks.next_retry_at IS '下次重试时间';

COMMIT;
