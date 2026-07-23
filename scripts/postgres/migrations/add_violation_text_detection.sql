-- ============================================================
-- 违规文本识别功能
-- EPIC-03 故事3：违规文本识别
-- 创建日期：2026-07-23
-- ============================================================

-- 内容风险检测记录表
CREATE TABLE IF NOT EXISTS content_risk_records (
    id                      BIGSERIAL PRIMARY KEY,
    content_type            VARCHAR(30)     NOT NULL,
    content_id              BIGINT          NOT NULL,
    content_version         INTEGER         NOT NULL DEFAULT 1,
    rule_version            VARCHAR(30),
    risk_type               VARCHAR(30),
    risk_level              VARCHAR(20)     NOT NULL,
    risk_score              INTEGER         NOT NULL DEFAULT 0,
    matched_rules           JSONB,
    masked_excerpt          TEXT,
    detection_status        VARCHAR(20)     NOT NULL DEFAULT 'SUCCESS',
    model_name              VARCHAR(100),
    business_trace_id       VARCHAR(100),
    created_at              TIMESTAMPTZ     NOT NULL DEFAULT CURRENT_TIMESTAMP,

    -- 约束
    CONSTRAINT ck_crr_content_type
        CHECK (content_type IN ('REVIEW', 'REVIEW_FOLLOW_UP', 'CHAT_MESSAGE')),
    CONSTRAINT ck_crr_risk_type
        CHECK (risk_type IS NULL OR risk_type IN ('AD_SPAM', 'ABUSE', 'FALSE_AD', 'SPAM', 'OTHER')),
    CONSTRAINT ck_crr_risk_level
        CHECK (risk_level IN ('LOW', 'MEDIUM', 'HIGH')),
    CONSTRAINT ck_crr_risk_score
        CHECK (risk_score >= 0 AND risk_score <= 100),
    CONSTRAINT ck_crr_detection_status
        CHECK (detection_status IN ('SUCCESS', 'FALLBACK', 'ERROR', 'TIMEOUT'))
);

-- 索引
CREATE INDEX IF NOT EXISTS idx_crr_content
    ON content_risk_records(content_type, content_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_crr_detection
    ON content_risk_records(detection_status, risk_level, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_crr_trace
    ON content_risk_records(business_trace_id);

COMMENT ON TABLE content_risk_records IS '内容风险检测记录 — 违规文本识别结果';
COMMENT ON COLUMN content_risk_records.content_type IS '内容类型：REVIEW/REVIEW_FOLLOW_UP/CHAT_MESSAGE';
COMMENT ON COLUMN content_risk_records.content_id IS '内容主键ID（reviewId/messageId）';
COMMENT ON COLUMN content_risk_records.content_version IS '内容版本号';
COMMENT ON COLUMN content_risk_records.rule_version IS '检测规则版本，用于追踪规则变更';
COMMENT ON COLUMN content_risk_records.risk_type IS '风险类型：AD_SPAM/ABUSE/FALSE_AD/SPAM/OTHER';
COMMENT ON COLUMN content_risk_records.risk_level IS '风险等级：LOW/MEDIUM/HIGH';
COMMENT ON COLUMN content_risk_records.risk_score IS '风险分值 0-100';
COMMENT ON COLUMN content_risk_records.matched_rules IS '匹配到的规则列表，JSON数组 [{ruleCode, ruleName, riskType, confidence, evidenceExcerpt}]';
COMMENT ON COLUMN content_risk_records.masked_excerpt IS '脱敏后的违规文本摘要';
COMMENT ON COLUMN content_risk_records.detection_status IS '检测状态：SUCCESS/FALLBACK/ERROR/TIMEOUT';
COMMENT ON COLUMN content_risk_records.model_name IS '使用的AI模型名称';
COMMENT ON COLUMN content_risk_records.business_trace_id IS '业务追踪ID，关联 ai_call_logs';
