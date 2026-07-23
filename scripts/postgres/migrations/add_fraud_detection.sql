-- ============================================================
-- 刷评检测（疑似刷评识别）功能
-- EPIC-03 故事4：疑似刷评识别
-- 创建日期：2026-07-23
-- ============================================================

-- 刷评案例表
CREATE TABLE IF NOT EXISTS review_fraud_cases (
    id                      BIGSERIAL PRIMARY KEY,
    merchant_id             BIGINT          NOT NULL,
    rule_type               VARCHAR(50)     NOT NULL,
    risk_level              VARCHAR(20)     NOT NULL,
    status                  VARCHAR(30)     NOT NULL DEFAULT 'SUSPICIOUS',
    matched_rule_snapshot   JSONB           NOT NULL,
    matched_review_ids      JSONB           NOT NULL,
    summary                 TEXT,
    detected_at             TIMESTAMPTZ,
    reviewed_by             BIGINT,
    reviewed_at             TIMESTAMPTZ,
    review_conclusion       VARCHAR(30),
    review_remark           TEXT,
    created_at              TIMESTAMPTZ     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at              TIMESTAMPTZ     NOT NULL DEFAULT CURRENT_TIMESTAMP,

    -- 约束
    CONSTRAINT ck_fraud_cases_rule_type
        CHECK (rule_type IN ('CONCENTRATION','SIMILARITY','FREQUENCY','RATING_ANOMALY')),
    CONSTRAINT ck_fraud_cases_risk_level
        CHECK (risk_level IN ('LOW','MEDIUM','HIGH')),
    CONSTRAINT ck_fraud_cases_status
        CHECK (status IN ('SUSPICIOUS','PENDING_REVIEW','REVIEWED','DISMISSED')),
    CONSTRAINT ck_fraud_cases_conclusion
        CHECK (review_conclusion IS NULL
               OR review_conclusion IN ('CONFIRMED_FRAUD','DISMISSED','NEED_FURTHER_CHECK'))
);

-- 索引
CREATE INDEX IF NOT EXISTS idx_fraud_cases_merchant
    ON review_fraud_cases(merchant_id, detected_at DESC);

CREATE INDEX IF NOT EXISTS idx_fraud_cases_status
    ON review_fraud_cases(status, risk_level);

CREATE INDEX IF NOT EXISTS idx_fraud_cases_rule
    ON review_fraud_cases(rule_type, detected_at DESC);

-- 自动更新 updated_at
CREATE OR REPLACE FUNCTION update_fraud_cases_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_fraud_cases_updated_at ON review_fraud_cases;
CREATE TRIGGER trg_fraud_cases_updated_at
    BEFORE UPDATE ON review_fraud_cases
    FOR EACH ROW
    EXECUTE FUNCTION update_fraud_cases_updated_at();
