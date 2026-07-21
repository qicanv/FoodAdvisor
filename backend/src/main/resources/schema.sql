CREATE TABLE IF NOT EXISTS content_tags (
    id BIGSERIAL PRIMARY KEY,
    code VARCHAR(50) UNIQUE NOT NULL,
    name VARCHAR(100) NOT NULL,
    category VARCHAR(50) NOT NULL,
    status VARCHAR(20) DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS merchant_tag_relations (
    id BIGSERIAL PRIMARY KEY,
    merchant_id BIGINT NOT NULL,
    tag_id BIGINT NOT NULL,
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_merchant_tag UNIQUE (merchant_id, tag_id)
);

CREATE TABLE IF NOT EXISTS topics (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(200) NOT NULL,
    description TEXT,
    cover_url VARCHAR(1000),
    status VARCHAR(20) DEFAULT 'DRAFT',
    start_at TIMESTAMPTZ,
    end_at TIMESTAMPTZ,
    created_by BIGINT,
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS topic_merchants (
    id BIGSERIAL PRIMARY KEY,
    topic_id BIGINT NOT NULL,
    merchant_id BIGINT NOT NULL,
    sort_order INTEGER DEFAULT 0,
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_topic_merchant UNIQUE (topic_id, merchant_id)
);

INSERT INTO content_tags (code, name, category, status) VALUES ('CAT_HOTPOT', '火锅', 'category', 'ACTIVE') ON CONFLICT (code) DO NOTHING;
INSERT INTO content_tags (code, name, category, status) VALUES ('CAT_WESTERN', '西餐', 'category', 'ACTIVE') ON CONFLICT (code) DO NOTHING;
INSERT INTO content_tags (code, name, category, status) VALUES ('CAT_JAPANESE', '日料', 'category', 'ACTIVE') ON CONFLICT (code) DO NOTHING;
INSERT INTO content_tags (code, name, category, status) VALUES ('CAT_SICHUAN', '麻辣', 'category', 'ACTIVE') ON CONFLICT (code) DO NOTHING;
INSERT INTO content_tags (code, name, category, status) VALUES ('CAT_CANTONESE', '粤菜', 'category', 'ACTIVE') ON CONFLICT (code) DO NOTHING;
INSERT INTO content_tags (code, name, category, status) VALUES ('CAT_KOREAN', '韩式', 'category', 'ACTIVE') ON CONFLICT (code) DO NOTHING;
INSERT INTO content_tags (code, name, category, status) VALUES ('CAT_DESSERT', '甜品', 'category', 'ACTIVE') ON CONFLICT (code) DO NOTHING;
INSERT INTO content_tags (code, name, category, status) VALUES ('CAT_SEAFOOD', '海鲜', 'category', 'ACTIVE') ON CONFLICT (code) DO NOTHING;

INSERT INTO content_tags (code, name, category, status) VALUES ('CUISINE_JING', '京菜', 'cuisine', 'ACTIVE') ON CONFLICT (code) DO NOTHING;
INSERT INTO content_tags (code, name, category, status) VALUES ('CUISINE_SICHUAN', '川菜', 'cuisine', 'ACTIVE') ON CONFLICT (code) DO NOTHING;
INSERT INTO content_tags (code, name, category, status) VALUES ('CUISINE_GUANGDONG', '粤菜', 'cuisine', 'ACTIVE') ON CONFLICT (code) DO NOTHING;
INSERT INTO content_tags (code, name, category, status) VALUES ('CUISINE_JAPANESE', '日式', 'cuisine', 'ACTIVE') ON CONFLICT (code) DO NOTHING;
INSERT INTO content_tags (code, name, category, status) VALUES ('CUISINE_KOREAN', '韩式', 'cuisine', 'ACTIVE') ON CONFLICT (code) DO NOTHING;
INSERT INTO content_tags (code, name, category, status) VALUES ('CUISINE_FRENCH', '法式', 'cuisine', 'ACTIVE') ON CONFLICT (code) DO NOTHING;
INSERT INTO content_tags (code, name, category, status) VALUES ('CUISINE_ITALIAN', '意式', 'cuisine', 'ACTIVE') ON CONFLICT (code) DO NOTHING;

INSERT INTO content_tags (code, name, category, status) VALUES ('SCENE_DATE', '约会', 'scene', 'ACTIVE') ON CONFLICT (code) DO NOTHING;
INSERT INTO content_tags (code, name, category, status) VALUES ('SCENE_FRIENDS', '朋友聚会', 'scene', 'ACTIVE') ON CONFLICT (code) DO NOTHING;
INSERT INTO content_tags (code, name, category, status) VALUES ('SCENE_FAMILY', '家庭聚餐', 'scene', 'ACTIVE') ON CONFLICT (code) DO NOTHING;
INSERT INTO content_tags (code, name, category, status) VALUES ('SCENE_LATE_NIGHT', '夜宵', 'scene', 'ACTIVE') ON CONFLICT (code) DO NOTHING;
INSERT INTO content_tags (code, name, category, status) VALUES ('SCENE_BUSINESS', '商务宴请', 'scene', 'ACTIVE') ON CONFLICT (code) DO NOTHING;

INSERT INTO content_tags (code, name, category, status) VALUES ('ENV_ROMANTIC', '浪漫', 'environment', 'ACTIVE') ON CONFLICT (code) DO NOTHING;
INSERT INTO content_tags (code, name, category, status) VALUES ('ENV_COZY', '温馨', 'environment', 'ACTIVE') ON CONFLICT (code) DO NOTHING;
INSERT INTO content_tags (code, name, category, status) VALUES ('ENV_NOISY', '热闹', 'environment', 'ACTIVE') ON CONFLICT (code) DO NOTHING;
INSERT INTO content_tags (code, name, category, status) VALUES ('ENV_QUIET', '安静', 'environment', 'ACTIVE') ON CONFLICT (code) DO NOTHING;

INSERT INTO content_tags (code, name, category, status) VALUES ('PRICE_BUDGET', '实惠', 'price', 'ACTIVE') ON CONFLICT (code) DO NOTHING;
INSERT INTO content_tags (code, name, category, status) VALUES ('PRICE_MID', '中档', 'price', 'ACTIVE') ON CONFLICT (code) DO NOTHING;
INSERT INTO content_tags (code, name, category, status) VALUES ('PRICE_HIGH', '高档', 'price', 'ACTIVE') ON CONFLICT (code) DO NOTHING;

CREATE TABLE IF NOT EXISTS user_behavior_logs (
    id BIGSERIAL PRIMARY KEY,
    event_id VARCHAR(64) UNIQUE NOT NULL,
    user_id BIGINT,
    event_type VARCHAR(50) NOT NULL,
    search_keyword VARCHAR(500),
    merchant_id BIGINT,
    scene_type VARCHAR(50),
    topic_id BIGINT,
    tag_code VARCHAR(100),
    feedback_type VARCHAR(50),
    feedback_score INT,
    page_url VARCHAR(1000),
    referrer_url VARCHAR(1000),
    user_agent VARCHAR(1000),
    ip_address VARCHAR(100),
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_behavior_event_type ON user_behavior_logs (event_type);
CREATE INDEX IF NOT EXISTS idx_behavior_user_id ON user_behavior_logs (user_id);
CREATE INDEX IF NOT EXISTS idx_behavior_created_at ON user_behavior_logs (created_at);
CREATE INDEX IF NOT EXISTS idx_behavior_merchant_id ON user_behavior_logs (merchant_id);

-- ==================== 评价举报（EPIC-08 故事5） ====================

CREATE TABLE IF NOT EXISTS review_reports (
    id BIGSERIAL PRIMARY KEY,
    reporter_user_id BIGINT NOT NULL,
    reported_review_id BIGINT NOT NULL,
    merchant_id BIGINT NOT NULL,
    reason VARCHAR(30) NOT NULL CHECK (reason IN ('ADVERTISING','FALSE_REVIEW','MALICIOUS_ATTACK','SEXUAL_OR_VULGAR','PRIVACY_LEAK','OTHER')),
    description TEXT,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING','RESOLVED','REJECTED')),
    handled_by BIGINT,
    handled_at TIMESTAMPTZ,
    resolution TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_review_reports_pending
ON review_reports(reporter_user_id, reported_review_id)
WHERE status = 'PENDING';

CREATE INDEX IF NOT EXISTS idx_review_reports_reporter
ON review_reports(reporter_user_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_review_reports_status
ON review_reports(status, created_at DESC);

-- AI 请求追踪（V0.3）
CREATE TABLE IF NOT EXISTS ai_request_traces (
    id BIGSERIAL PRIMARY KEY,
    trace_id VARCHAR(100) NOT NULL,
    request_id VARCHAR(100),
    session_id BIGINT,
    user_id BIGINT,
    scene VARCHAR(100) NOT NULL,
    intent VARCHAR(100),
    structured_conditions JSONB NOT NULL DEFAULT '{}'::jsonb,
    provider VARCHAR(100),
    model_name VARCHAR(200),
    model_version VARCHAR(100),
    prompt_version VARCHAR(100),
    status VARCHAR(20) NOT NULL,
    final_output_summary JSONB NOT NULL DEFAULT '{}'::jsonb,
    error_code VARCHAR(100),
    error_message VARCHAR(500),
    started_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMPTZ,
    total_duration_ms BIGINT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_ai_request_traces_trace UNIQUE (trace_id),
    CONSTRAINT ck_ai_request_traces_status CHECK (status IN ('RUNNING','SUCCESS','FAILED','FALLBACK')),
    CONSTRAINT ck_ai_request_traces_duration CHECK (total_duration_ms IS NULL OR total_duration_ms >= 0)
);

CREATE TABLE IF NOT EXISTS ai_request_trace_stages (
    id BIGSERIAL PRIMARY KEY,
    trace_id VARCHAR(100) NOT NULL,
    stage_name VARCHAR(100) NOT NULL,
    sequence_no INTEGER NOT NULL,
    attempt_no INTEGER NOT NULL DEFAULT 1,
    status VARCHAR(20) NOT NULL,
    input_summary JSONB NOT NULL DEFAULT '{}'::jsonb,
    output_summary JSONB NOT NULL DEFAULT '{}'::jsonb,
    provider VARCHAR(100),
    model_name VARCHAR(200),
    model_version VARCHAR(100),
    prompt_version VARCHAR(100),
    duration_ms BIGINT,
    error_code VARCHAR(100),
    error_message VARCHAR(500),
    started_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_ai_request_trace_stage UNIQUE (trace_id, sequence_no, attempt_no),
    CONSTRAINT ck_ai_request_trace_stages_status CHECK (status IN ('RUNNING','SUCCESS','FAILED','FALLBACK','SKIPPED')),
    CONSTRAINT ck_ai_request_trace_stages_sequence CHECK (sequence_no > 0),
    CONSTRAINT ck_ai_request_trace_stages_attempt CHECK (attempt_no > 0),
    CONSTRAINT ck_ai_request_trace_stages_duration CHECK (duration_ms IS NULL OR duration_ms >= 0)
);

CREATE TABLE IF NOT EXISTS ai_trace_retrieval_sources (
    id BIGSERIAL PRIMARY KEY,
    trace_id VARCHAR(100) NOT NULL,
    stage_id BIGINT,
    source_type VARCHAR(50) NOT NULL,
    source_id VARCHAR(100),
    document_id VARCHAR(200),
    chunk_id VARCHAR(200),
    merchant_id BIGINT,
    merchant_name VARCHAR(200),
    summary VARCHAR(500),
    rank_no INTEGER,
    relevance_score NUMERIC(12, 8),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_ai_trace_sources_rank CHECK (rank_no IS NULL OR rank_no > 0)
);