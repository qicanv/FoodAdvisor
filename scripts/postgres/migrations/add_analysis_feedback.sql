-- ============================================================
-- 商家分析结果反馈 (EPIC-06 Story 5)
-- 商家用户对口碑分析和经营建议等结果进行准确/不准确反馈
-- ============================================================

-- 商家分析结果反馈表
CREATE TABLE IF NOT EXISTS analysis_feedback (
    id              BIGSERIAL       PRIMARY KEY,
    merchant_id     BIGINT          NOT NULL,
    analysis_type   VARCHAR(50)     NOT NULL,
    analysis_id     BIGINT,
    feedback_type   VARCHAR(20)     NOT NULL,
    content         TEXT,
    created_by      BIGINT          NOT NULL,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT now()
);

COMMENT ON TABLE analysis_feedback IS '商家分析结果反馈表';
COMMENT ON COLUMN analysis_feedback.merchant_id IS '商家ID';
COMMENT ON COLUMN analysis_feedback.analysis_type IS '分析类型：SENTIMENT / KEYWORD / ISSUE_ATTRIBUTION / COMPETITOR / BUSINESS_SUGGESTION / REVIEW_SUMMARY / HIGHLIGHT';
COMMENT ON COLUMN analysis_feedback.analysis_id IS '关联的分析记录ID（可为空，表示对某类分析的整体反馈）';
COMMENT ON COLUMN analysis_feedback.feedback_type IS '反馈类型：ACCURATE / INACCURATE';
COMMENT ON COLUMN analysis_feedback.content IS '具体问题说明（选填）';
COMMENT ON COLUMN analysis_feedback.created_by IS '提交反馈的商家用户ID';

-- 约束：反馈类型仅允许 ACCURATE 或 INACCURATE
ALTER TABLE analysis_feedback
    ADD CONSTRAINT ck_af_feedback_type
        CHECK (feedback_type IN ('ACCURATE', 'INACCURATE'));

-- 约束：分析类型仅允许预定义值
ALTER TABLE analysis_feedback
    ADD CONSTRAINT ck_af_analysis_type
        CHECK (analysis_type IN (
            'SENTIMENT',
            'KEYWORD',
            'ISSUE_ATTRIBUTION',
            'COMPETITOR',
            'BUSINESS_SUGGESTION',
            'REVIEW_SUMMARY',
            'HIGHLIGHT'
        ));

-- 唯一约束：同一商家对同一分析记录的同一分析类型只能有一条反馈
-- analysis_id 为 NULL 时表示对该类型的整体反馈（不做唯一约束）
CREATE UNIQUE INDEX IF NOT EXISTS uk_af_merchant_type_analysis
    ON analysis_feedback(merchant_id, analysis_type, analysis_id)
    WHERE analysis_id IS NOT NULL;

-- 索引
CREATE INDEX IF NOT EXISTS idx_af_merchant_type
    ON analysis_feedback(merchant_id, analysis_type, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_af_feedback_type
    ON analysis_feedback(feedback_type, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_af_created_by
    ON analysis_feedback(created_by, created_at DESC);
