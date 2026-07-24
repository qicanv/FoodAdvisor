-- ============================================================
-- 敏感话题预警功能
-- EPIC-03 故事6：敏感话题预警
-- 创建日期：2026-07-24
-- ============================================================

-- 敏感预警主表
CREATE TABLE IF NOT EXISTS sensitive_alerts (
    id                      BIGSERIAL PRIMARY KEY,
    merchant_id             BIGINT          NOT NULL,
    topic_type              VARCHAR(30)     NOT NULL,
    risk_level              VARCHAR(20)     NOT NULL DEFAULT 'MEDIUM',
    review_count            INTEGER         NOT NULL DEFAULT 0,
    keywords                JSONB           NOT NULL DEFAULT '[]'::jsonb,
    first_occurred_at       TIMESTAMPTZ     NOT NULL,
    last_occurred_at        TIMESTAMPTZ     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    status                  VARCHAR(20)     NOT NULL DEFAULT 'PENDING',
    handled_by              BIGINT,
    handled_at              TIMESTAMPTZ,
    handled_username        VARCHAR(100),
    remark                  TEXT,
    created_at              TIMESTAMPTZ     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at              TIMESTAMPTZ     NOT NULL DEFAULT CURRENT_TIMESTAMP,

    -- 约束
    CONSTRAINT ck_sa_topic_type
        CHECK (topic_type IN ('FOOD_SAFETY', 'HYGIENE', 'CONCENTRATED_COMPLAINT', 'SERVICE_DISPUTE')),
    CONSTRAINT ck_sa_risk_level
        CHECK (risk_level IN ('LOW', 'MEDIUM', 'HIGH')),
    CONSTRAINT ck_sa_status
        CHECK (status IN ('PENDING', 'PROCESSING', 'RESOLVED', 'DISMISSED')),
    CONSTRAINT ck_sa_review_count
        CHECK (review_count >= 0)
);

-- 敏感预警关联评价表
CREATE TABLE IF NOT EXISTS sensitive_alert_reviews (
    id                      BIGSERIAL PRIMARY KEY,
    alert_id                BIGINT          NOT NULL,
    review_id               BIGINT          NOT NULL,
    review_version          INTEGER         NOT NULL DEFAULT 1,
    evidence_excerpt        TEXT,
    created_at              TIMESTAMPTZ     NOT NULL DEFAULT CURRENT_TIMESTAMP,

    -- 约束
    CONSTRAINT uk_sar_alert_review UNIQUE (alert_id, review_id)
);

-- 索引
CREATE INDEX IF NOT EXISTS idx_sa_merchant
    ON sensitive_alerts(merchant_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_sa_status
    ON sensitive_alerts(status, risk_level, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_sa_topic_type
    ON sensitive_alerts(topic_type, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_sa_merchant_topic_time
    ON sensitive_alerts(merchant_id, topic_type, last_occurred_at DESC);

CREATE INDEX IF NOT EXISTS idx_sar_alert
    ON sensitive_alert_reviews(alert_id, review_id);

CREATE INDEX IF NOT EXISTS idx_sar_review
    ON sensitive_alert_reviews(review_id);

-- 自动更新 updated_at
CREATE OR REPLACE FUNCTION update_sensitive_alerts_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_sensitive_alerts_updated_at ON sensitive_alerts;
CREATE TRIGGER trg_sensitive_alerts_updated_at
    BEFORE UPDATE ON sensitive_alerts
    FOR EACH ROW
    EXECUTE FUNCTION update_sensitive_alerts_updated_at();

-- 表注释
COMMENT ON TABLE sensitive_alerts IS '敏感话题预警 — 集中食品安全、卫生、投诉、服务纠纷等风险事件';
COMMENT ON COLUMN sensitive_alerts.topic_type IS '敏感话题类型：FOOD_SAFETY/HYGIENE/CONCENTRATED_COMPLAINT/SERVICE_DISPUTE';
COMMENT ON COLUMN sensitive_alerts.risk_level IS '风险等级：LOW/MEDIUM/HIGH';
COMMENT ON COLUMN sensitive_alerts.review_count IS '涉及评价数量';
COMMENT ON COLUMN sensitive_alerts.keywords IS '主要关键词，JSON数组';
COMMENT ON COLUMN sensitive_alerts.first_occurred_at IS '首次出现时间';
COMMENT ON COLUMN sensitive_alerts.last_occurred_at IS '最近一次出现时间';
COMMENT ON COLUMN sensitive_alerts.status IS '处理状态：PENDING/PROCESSING/RESOLVED/DISMISSED';
COMMENT ON COLUMN sensitive_alerts.handled_by IS '处理人用户ID';
COMMENT ON COLUMN sensitive_alerts.handled_at IS '处理时间';
COMMENT ON COLUMN sensitive_alerts.handled_username IS '处理人用户名';
COMMENT ON COLUMN sensitive_alerts.remark IS '处理备注';

COMMENT ON TABLE sensitive_alert_reviews IS '敏感预警关联的原始评价';
COMMENT ON COLUMN sensitive_alert_reviews.evidence_excerpt IS '评价中与敏感话题相关的摘录内容';
