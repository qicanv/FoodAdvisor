-- ============================================================
-- 经营改进建议生成 (EPIC-02 Story 8)
-- 为商家生成基于口碑数据的阶段性经营改进建议
-- ============================================================

-- 经营改进建议主表
CREATE TABLE IF NOT EXISTS business_suggestions (
    id              BIGSERIAL       PRIMARY KEY,
    merchant_id     BIGINT          NOT NULL,
    version         INT             NOT NULL DEFAULT 1,
    title           VARCHAR(500)    NOT NULL,
    description     TEXT            NOT NULL,
    category        VARCHAR(50)     NOT NULL,
    priority        VARCHAR(20)     NOT NULL DEFAULT 'MEDIUM',
    timeframe       VARCHAR(20)     NOT NULL DEFAULT 'SHORT_TERM',
    expected_effect TEXT,
    data_basis_type VARCHAR(50),
    data_basis_summary TEXT,
    metric_name     VARCHAR(200),
    metric_value    VARCHAR(200),
    confidence      VARCHAR(20)     NOT NULL DEFAULT 'MEDIUM',
    status          VARCHAR(20)     NOT NULL DEFAULT 'ACTIVE',
    generated_at    TIMESTAMPTZ     NOT NULL DEFAULT now()
);

COMMENT ON TABLE business_suggestions IS '经营改进建议主表';
COMMENT ON COLUMN business_suggestions.category IS 'REPUTATION_TREND / NEGATIVE_ISSUE / HIGHLIGHT_GAP / COMPETITOR_GAP';
COMMENT ON COLUMN business_suggestions.priority IS 'HIGH / MEDIUM / LOW';
COMMENT ON COLUMN business_suggestions.timeframe IS 'SHORT_TERM / LONG_TERM';
COMMENT ON COLUMN business_suggestions.confidence IS 'HIGH / MEDIUM / LOW';
COMMENT ON COLUMN business_suggestions.status IS 'ACTIVE / OUTDATED / DISABLED';
COMMENT ON COLUMN business_suggestions.data_basis_type IS 'REPUTATION_TREND / NEGATIVE_ISSUE / HIGHLIGHT / COMPETITOR';

-- 经营改进建议依据关联表
CREATE TABLE IF NOT EXISTS business_suggestion_evidences (
    id               BIGSERIAL    PRIMARY KEY,
    suggestion_id    BIGINT       NOT NULL,
    source_type      VARCHAR(50)  NOT NULL,
    source_id        BIGINT,
    review_id        BIGINT,
    metric_snapshot  JSONB,
    evidence_excerpt TEXT,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),

    FOREIGN KEY (suggestion_id) REFERENCES business_suggestions(id)
);

COMMENT ON TABLE business_suggestion_evidences IS '经营改进建议依据关联表';
COMMENT ON COLUMN business_suggestion_evidences.source_type IS 'REPUTATION_TREND / NEGATIVE_ISSUE / HIGHLIGHT / COMPETITOR / REVIEW';

-- 索引
CREATE INDEX IF NOT EXISTS idx_bs_merchant_status
    ON business_suggestions(merchant_id, status, generated_at DESC);

CREATE INDEX IF NOT EXISTS idx_bse_suggestion
    ON business_suggestion_evidences(suggestion_id);
