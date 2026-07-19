-- ============================================================
-- 迁移脚本：新增 review_reply_draft 表
-- 功能：EPIC-02 故事7 — 评价辅助回复
-- 日期：2026-07-19
-- 适用：已有数据的数据库增量迁移
-- ============================================================

-- 仅在表不存在时创建（幂等迁移）
CREATE TABLE IF NOT EXISTS review_reply_draft (
    id BIGSERIAL PRIMARY KEY,
    review_id BIGINT NOT NULL,
    merchant_id BIGINT NOT NULL,
    generated_content TEXT NOT NULL,
    edited_content TEXT,
    strategy VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    generated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    published_at TIMESTAMPTZ,
    confirmed_by BIGINT,
    ai_trace_id VARCHAR(100),
    model_name VARCHAR(100),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_reply_draft_review
        FOREIGN KEY (review_id) REFERENCES reviews(id) ON DELETE CASCADE,
    CONSTRAINT fk_reply_draft_merchant
        FOREIGN KEY (merchant_id) REFERENCES merchants(id) ON DELETE RESTRICT,
    CONSTRAINT fk_reply_draft_confirmed_by
        FOREIGN KEY (confirmed_by) REFERENCES merchant_members(id) ON DELETE SET NULL,
    CONSTRAINT ck_reply_draft_status
        CHECK (status IN ('DRAFT', 'PUBLISHED', 'DISCARDED')),
    CONSTRAINT ck_reply_draft_strategy
        CHECK (strategy IN ('POSITIVE', 'NEGATIVE'))
);

-- 仅在索引不存在时创建
CREATE INDEX IF NOT EXISTS idx_reply_draft_review_id
    ON review_reply_draft(review_id);

CREATE INDEX IF NOT EXISTS idx_reply_draft_merchant_id
    ON review_reply_draft(merchant_id);

CREATE INDEX IF NOT EXISTS idx_reply_draft_status
    ON review_reply_draft(status);

CREATE INDEX IF NOT EXISTS idx_reply_draft_merchant_status
    ON review_reply_draft(merchant_id, status);
