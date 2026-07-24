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
CREATE INDEX IF NOT EXISTS idx_behavior_scene_type ON user_behavior_logs (scene_type);
CREATE INDEX IF NOT EXISTS idx_behavior_search_keyword ON user_behavior_logs (search_keyword);

-- ==================== 商家测试数据 ====================

INSERT INTO merchants (id, merchant_code, name, category, cuisine, rating, average_price, review_count, address, region_code, platform_status, operation_status) VALUES 
(1, 'MERCHANT_001', '蜀大侠火锅', '火锅', '川菜', 4.8, 128, 2560, '成都市锦江区春熙路', 'CD-JJ', 'ACTIVE', 'OPERATING') ON CONFLICT DO NOTHING;
INSERT INTO merchants (id, merchant_code, name, category, cuisine, rating, average_price, review_count, address, region_code, platform_status, operation_status) VALUES 
(2, 'MERCHANT_002', '九锅一堂', '川菜', '川菜', 4.6, 88, 1890, '成都市武侯区玉林路', 'CD-WH', 'ACTIVE', 'OPERATING') ON CONFLICT DO NOTHING;
INSERT INTO merchants (id, merchant_code, name, category, cuisine, rating, average_price, review_count, address, region_code, platform_status, operation_status) VALUES 
(3, 'MERCHANT_003', '元气寿司', '日料', '日式', 4.7, 158, 1230, '成都市高新区天府大道', 'CD-GX', 'ACTIVE', 'OPERATING') ON CONFLICT DO NOTHING;
INSERT INTO merchants (id, merchant_code, name, category, cuisine, rating, average_price, review_count, address, region_code, platform_status, operation_status) VALUES 
(4, 'MERCHANT_004', '老码头火锅', '火锅', '川菜', 4.9, 168, 3420, '成都市青羊区宽窄巷子', 'CD-QY', 'ACTIVE', 'OPERATING') ON CONFLICT DO NOTHING;
INSERT INTO merchants (id, merchant_code, name, category, cuisine, rating, average_price, review_count, address, region_code, platform_status, operation_status) VALUES 
(5, 'MERCHANT_005', '大蓉和', '川菜', '川菜', 4.5, 138, 2100, '成都市成华区建设路', 'CD-CH', 'ACTIVE', 'OPERATING') ON CONFLICT DO NOTHING;

-- ==================== 用户行为日志测试数据 ====================

INSERT INTO user_behavior_logs (event_id, user_id, event_type, search_keyword, created_at) VALUES ('ev_001', 2, 'SEARCH', '火锅', NOW() - INTERVAL '2 days') ON CONFLICT DO NOTHING;
INSERT INTO user_behavior_logs (event_id, user_id, event_type, search_keyword, created_at) VALUES ('ev_002', 2, 'SEARCH', '日料', NOW() - INTERVAL '1 day') ON CONFLICT DO NOTHING;
INSERT INTO user_behavior_logs (event_id, user_id, event_type, search_keyword, created_at) VALUES ('ev_003', 3, 'SEARCH', '火锅', NOW() - INTERVAL '3 days') ON CONFLICT DO NOTHING;
INSERT INTO user_behavior_logs (event_id, user_id, event_type, search_keyword, created_at) VALUES ('ev_004', 3, 'SEARCH', '西餐', NOW() - INTERVAL '2 days') ON CONFLICT DO NOTHING;
INSERT INTO user_behavior_logs (event_id, user_id, event_type, search_keyword, created_at) VALUES ('ev_005', 4, 'SEARCH', '川菜', NOW() - INTERVAL '4 days') ON CONFLICT DO NOTHING;
INSERT INTO user_behavior_logs (event_id, user_id, event_type, search_keyword, created_at) VALUES ('ev_006', 4, 'SEARCH', '火锅', NOW() - INTERVAL '1 day') ON CONFLICT DO NOTHING;
INSERT INTO user_behavior_logs (event_id, user_id, event_type, search_keyword, created_at) VALUES ('ev_007', 5, 'SEARCH', '甜品', NOW() - INTERVAL '5 days') ON CONFLICT DO NOTHING;
INSERT INTO user_behavior_logs (event_id, user_id, event_type, search_keyword, created_at) VALUES ('ev_008', 5, 'SEARCH', '海鲜', NOW() - INTERVAL '3 days') ON CONFLICT DO NOTHING;
INSERT INTO user_behavior_logs (event_id, user_id, event_type, search_keyword, created_at) VALUES ('ev_009', 6, 'SEARCH', '粤菜', NOW() - INTERVAL '2 days') ON CONFLICT DO NOTHING;
INSERT INTO user_behavior_logs (event_id, user_id, event_type, search_keyword, created_at) VALUES ('ev_010', 6, 'SEARCH', '火锅', NOW() - INTERVAL '1 day') ON CONFLICT DO NOTHING;
INSERT INTO user_behavior_logs (event_id, user_id, event_type, search_keyword, created_at) VALUES ('ev_011', 7, 'SEARCH', '韩式料理', NOW() - INTERVAL '4 days') ON CONFLICT DO NOTHING;
INSERT INTO user_behavior_logs (event_id, user_id, event_type, search_keyword, created_at) VALUES ('ev_012', 7, 'SEARCH', '日料', NOW() - INTERVAL '2 days') ON CONFLICT DO NOTHING;

INSERT INTO user_behavior_logs (event_id, user_id, event_type, merchant_id, created_at) VALUES ('ev_013', 2, 'MERCHANT_CLICK', 1, NOW() - INTERVAL '2 days') ON CONFLICT DO NOTHING;
INSERT INTO user_behavior_logs (event_id, user_id, event_type, merchant_id, created_at) VALUES ('ev_014', 2, 'MERCHANT_CLICK', 2, NOW() - INTERVAL '1 day') ON CONFLICT DO NOTHING;
INSERT INTO user_behavior_logs (event_id, user_id, event_type, merchant_id, created_at) VALUES ('ev_015', 3, 'MERCHANT_CLICK', 1, NOW() - INTERVAL '3 days') ON CONFLICT DO NOTHING;
INSERT INTO user_behavior_logs (event_id, user_id, event_type, merchant_id, created_at) VALUES ('ev_016', 3, 'MERCHANT_CLICK', 3, NOW() - INTERVAL '2 days') ON CONFLICT DO NOTHING;
INSERT INTO user_behavior_logs (event_id, user_id, event_type, merchant_id, created_at) VALUES ('ev_017', 4, 'MERCHANT_CLICK', 2, NOW() - INTERVAL '4 days') ON CONFLICT DO NOTHING;
INSERT INTO user_behavior_logs (event_id, user_id, event_type, merchant_id, created_at) VALUES ('ev_018', 4, 'MERCHANT_CLICK', 4, NOW() - INTERVAL '1 day') ON CONFLICT DO NOTHING;
INSERT INTO user_behavior_logs (event_id, user_id, event_type, merchant_id, created_at) VALUES ('ev_019', 5, 'MERCHANT_CLICK', 3, NOW() - INTERVAL '5 days') ON CONFLICT DO NOTHING;
INSERT INTO user_behavior_logs (event_id, user_id, event_type, merchant_id, created_at) VALUES ('ev_020', 5, 'MERCHANT_CLICK', 5, NOW() - INTERVAL '3 days') ON CONFLICT DO NOTHING;
INSERT INTO user_behavior_logs (event_id, user_id, event_type, merchant_id, created_at) VALUES ('ev_021', 6, 'MERCHANT_CLICK', 1, NOW() - INTERVAL '2 days') ON CONFLICT DO NOTHING;
INSERT INTO user_behavior_logs (event_id, user_id, event_type, merchant_id, created_at) VALUES ('ev_022', 6, 'MERCHANT_CLICK', 2, NOW() - INTERVAL '1 day') ON CONFLICT DO NOTHING;
INSERT INTO user_behavior_logs (event_id, user_id, event_type, merchant_id, created_at) VALUES ('ev_023', 7, 'MERCHANT_CLICK', 4, NOW() - INTERVAL '4 days') ON CONFLICT DO NOTHING;
INSERT INTO user_behavior_logs (event_id, user_id, event_type, merchant_id, created_at) VALUES ('ev_024', 7, 'MERCHANT_CLICK', 5, NOW() - INTERVAL '2 days') ON CONFLICT DO NOTHING;
INSERT INTO user_behavior_logs (event_id, user_id, event_type, merchant_id, created_at) VALUES ('ev_025', 2, 'MERCHANT_CLICK', 3, NOW() - INTERVAL '1 day') ON CONFLICT DO NOTHING;
INSERT INTO user_behavior_logs (event_id, user_id, event_type, merchant_id, created_at) VALUES ('ev_026', 3, 'MERCHANT_CLICK', 2, NOW() - INTERVAL '1 day') ON CONFLICT DO NOTHING;
INSERT INTO user_behavior_logs (event_id, user_id, event_type, merchant_id, created_at) VALUES ('ev_027', 4, 'MERCHANT_CLICK', 1, NOW() - INTERVAL '3 days') ON CONFLICT DO NOTHING;

INSERT INTO user_behavior_logs (event_id, user_id, event_type, scene_type, created_at) VALUES ('ev_028', 2, 'SCENE_ENTRY', 'DATE', NOW() - INTERVAL '2 days') ON CONFLICT DO NOTHING;
INSERT INTO user_behavior_logs (event_id, user_id, event_type, scene_type, created_at) VALUES ('ev_029', 2, 'SCENE_ENTRY', 'FRIENDS', NOW() - INTERVAL '1 day') ON CONFLICT DO NOTHING;
INSERT INTO user_behavior_logs (event_id, user_id, event_type, scene_type, created_at) VALUES ('ev_030', 3, 'SCENE_ENTRY', 'FAMILY', NOW() - INTERVAL '3 days') ON CONFLICT DO NOTHING;
INSERT INTO user_behavior_logs (event_id, user_id, event_type, scene_type, created_at) VALUES ('ev_031', 3, 'SCENE_ENTRY', 'DATE', NOW() - INTERVAL '2 days') ON CONFLICT DO NOTHING;
INSERT INTO user_behavior_logs (event_id, user_id, event_type, scene_type, created_at) VALUES ('ev_032', 4, 'SCENE_ENTRY', 'BUSINESS', NOW() - INTERVAL '4 days') ON CONFLICT DO NOTHING;
INSERT INTO user_behavior_logs (event_id, user_id, event_type, scene_type, created_at) VALUES ('ev_033', 4, 'SCENE_ENTRY', 'FRIENDS', NOW() - INTERVAL '1 day') ON CONFLICT DO NOTHING;
INSERT INTO user_behavior_logs (event_id, user_id, event_type, scene_type, created_at) VALUES ('ev_034', 5, 'SCENE_ENTRY', 'LATE_NIGHT', NOW() - INTERVAL '5 days') ON CONFLICT DO NOTHING;
INSERT INTO user_behavior_logs (event_id, user_id, event_type, scene_type, created_at) VALUES ('ev_035', 5, 'SCENE_ENTRY', 'FAMILY', NOW() - INTERVAL '3 days') ON CONFLICT DO NOTHING;
INSERT INTO user_behavior_logs (event_id, user_id, event_type, scene_type, created_at) VALUES ('ev_036', 6, 'SCENE_ENTRY', 'DATE', NOW() - INTERVAL '2 days') ON CONFLICT DO NOTHING;
INSERT INTO user_behavior_logs (event_id, user_id, event_type, scene_type, created_at) VALUES ('ev_037', 6, 'SCENE_ENTRY', 'FRIENDS', NOW() - INTERVAL '1 day') ON CONFLICT DO NOTHING;
INSERT INTO user_behavior_logs (event_id, user_id, event_type, scene_type, created_at) VALUES ('ev_038', 7, 'SCENE_ENTRY', 'LATE_NIGHT', NOW() - INTERVAL '4 days') ON CONFLICT DO NOTHING;
INSERT INTO user_behavior_logs (event_id, user_id, event_type, scene_type, created_at) VALUES ('ev_039', 7, 'SCENE_ENTRY', 'BUSINESS', NOW() - INTERVAL '2 days') ON CONFLICT DO NOTHING;

INSERT INTO user_behavior_logs (event_id, user_id, event_type, tag_code, created_at) VALUES ('ev_040', 2, 'TAG_CLICK', 'CAT_HOTPOT', NOW() - INTERVAL '2 days') ON CONFLICT DO NOTHING;
INSERT INTO user_behavior_logs (event_id, user_id, event_type, tag_code, created_at) VALUES ('ev_041', 2, 'TAG_CLICK', 'CAT_JAPANESE', NOW() - INTERVAL '1 day') ON CONFLICT DO NOTHING;
INSERT INTO user_behavior_logs (event_id, user_id, event_type, tag_code, created_at) VALUES ('ev_042', 3, 'TAG_CLICK', 'CAT_HOTPOT', NOW() - INTERVAL '3 days') ON CONFLICT DO NOTHING;
INSERT INTO user_behavior_logs (event_id, user_id, event_type, tag_code, created_at) VALUES ('ev_043', 3, 'TAG_CLICK', 'CAT_WESTERN', NOW() - INTERVAL '2 days') ON CONFLICT DO NOTHING;
INSERT INTO user_behavior_logs (event_id, user_id, event_type, tag_code, created_at) VALUES ('ev_044', 4, 'TAG_CLICK', 'CAT_SICHUAN', NOW() - INTERVAL '4 days') ON CONFLICT DO NOTHING;
INSERT INTO user_behavior_logs (event_id, user_id, event_type, tag_code, created_at) VALUES ('ev_045', 4, 'TAG_CLICK', 'CAT_HOTPOT', NOW() - INTERVAL '1 day') ON CONFLICT DO NOTHING;
INSERT INTO user_behavior_logs (event_id, user_id, event_type, tag_code, created_at) VALUES ('ev_046', 5, 'TAG_CLICK', 'CAT_DESSERT', NOW() - INTERVAL '5 days') ON CONFLICT DO NOTHING;
INSERT INTO user_behavior_logs (event_id, user_id, event_type, tag_code, created_at) VALUES ('ev_047', 5, 'TAG_CLICK', 'CAT_SEAFOOD', NOW() - INTERVAL '3 days') ON CONFLICT DO NOTHING;
INSERT INTO user_behavior_logs (event_id, user_id, event_type, tag_code, created_at) VALUES ('ev_048', 6, 'TAG_CLICK', 'CAT_CANTONESE', NOW() - INTERVAL '2 days') ON CONFLICT DO NOTHING;
INSERT INTO user_behavior_logs (event_id, user_id, event_type, tag_code, created_at) VALUES ('ev_049', 6, 'TAG_CLICK', 'CAT_HOTPOT', NOW() - INTERVAL '1 day') ON CONFLICT DO NOTHING;
INSERT INTO user_behavior_logs (event_id, user_id, event_type, tag_code, created_at) VALUES ('ev_050', 7, 'TAG_CLICK', 'CAT_KOREAN', NOW() - INTERVAL '4 days') ON CONFLICT DO NOTHING;

INSERT INTO user_behavior_logs (event_id, user_id, event_type, feedback_type, feedback_score, merchant_id, created_at) VALUES ('ev_051', 2, 'FEEDBACK', 'LIKE', 5, 1, NOW() - INTERVAL '2 days') ON CONFLICT DO NOTHING;
INSERT INTO user_behavior_logs (event_id, user_id, event_type, feedback_type, feedback_score, merchant_id, created_at) VALUES ('ev_052', 2, 'FEEDBACK', 'DISLIKE', 2, 2, NOW() - INTERVAL '1 day') ON CONFLICT DO NOTHING;
INSERT INTO user_behavior_logs (event_id, user_id, event_type, feedback_type, feedback_score, merchant_id, created_at) VALUES ('ev_053', 3, 'FEEDBACK', 'LIKE', 4, 1, NOW() - INTERVAL '3 days') ON CONFLICT DO NOTHING;
INSERT INTO user_behavior_logs (event_id, user_id, event_type, feedback_type, feedback_score, merchant_id, created_at) VALUES ('ev_054', 3, 'FEEDBACK', 'LIKE', 5, 3, NOW() - INTERVAL '2 days') ON CONFLICT DO NOTHING;
INSERT INTO user_behavior_logs (event_id, user_id, event_type, feedback_type, feedback_score, merchant_id, created_at) VALUES ('ev_055', 4, 'FEEDBACK', 'DISLIKE', 1, 2, NOW() - INTERVAL '4 days') ON CONFLICT DO NOTHING;
INSERT INTO user_behavior_logs (event_id, user_id, event_type, feedback_type, feedback_score, merchant_id, created_at) VALUES ('ev_056', 4, 'FEEDBACK', 'LIKE', 5, 4, NOW() - INTERVAL '1 day') ON CONFLICT DO NOTHING;
INSERT INTO user_behavior_logs (event_id, user_id, event_type, feedback_type, feedback_score, merchant_id, created_at) VALUES ('ev_057', 5, 'FEEDBACK', 'LIKE', 4, 3, NOW() - INTERVAL '5 days') ON CONFLICT DO NOTHING;
INSERT INTO user_behavior_logs (event_id, user_id, event_type, feedback_type, feedback_score, merchant_id, created_at) VALUES ('ev_058', 5, 'FEEDBACK', 'LIKE', 5, 5, NOW() - INTERVAL '3 days') ON CONFLICT DO NOTHING;
INSERT INTO user_behavior_logs (event_id, user_id, event_type, feedback_type, feedback_score, merchant_id, created_at) VALUES ('ev_059', 6, 'FEEDBACK', 'DISLIKE', 2, 1, NOW() - INTERVAL '2 days') ON CONFLICT DO NOTHING;
INSERT INTO user_behavior_logs (event_id, user_id, event_type, feedback_type, feedback_score, merchant_id, created_at) VALUES ('ev_060', 6, 'FEEDBACK', 'LIKE', 4, 2, NOW() - INTERVAL '1 day') ON CONFLICT DO NOTHING;

INSERT INTO user_behavior_logs (event_id, user_id, event_type, topic_id, created_at) VALUES ('ev_061', 2, 'TOPIC_CLICK', 1, NOW() - INTERVAL '2 days') ON CONFLICT DO NOTHING;
INSERT INTO user_behavior_logs (event_id, user_id, event_type, topic_id, created_at) VALUES ('ev_062', 3, 'TOPIC_CLICK', 1, NOW() - INTERVAL '3 days') ON CONFLICT DO NOTHING;
INSERT INTO user_behavior_logs (event_id, user_id, event_type, topic_id, created_at) VALUES ('ev_063', 4, 'TOPIC_CLICK', 2, NOW() - INTERVAL '4 days') ON CONFLICT DO NOTHING;
INSERT INTO user_behavior_logs (event_id, user_id, event_type, topic_id, created_at) VALUES ('ev_064', 5, 'TOPIC_CLICK', 1, NOW() - INTERVAL '5 days') ON CONFLICT DO NOTHING;
INSERT INTO user_behavior_logs (event_id, user_id, event_type, topic_id, created_at) VALUES ('ev_065', 6, 'TOPIC_CLICK', 2, NOW() - INTERVAL '2 days') ON CONFLICT DO NOTHING;

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

CREATE INDEX IF NOT EXISTS idx_ai_request_traces_scene ON ai_request_traces (scene);
CREATE INDEX IF NOT EXISTS idx_ai_request_traces_status ON ai_request_traces (status);
CREATE INDEX IF NOT EXISTS idx_ai_request_traces_started_at ON ai_request_traces (started_at);
CREATE INDEX IF NOT EXISTS idx_ai_request_traces_model_name ON ai_request_traces (model_name);

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

-- ==================== 食客统计相关表 ====================

CREATE TABLE IF NOT EXISTS user_follows (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    merchant_id BIGINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_user_follows UNIQUE (user_id, merchant_id)
);

CREATE TABLE IF NOT EXISTS review_likes (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    review_id BIGINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_review_likes UNIQUE (user_id, review_id)
);

CREATE TABLE IF NOT EXISTS user_activities (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    activity_type VARCHAR(50) NOT NULL,
    target_type VARCHAR(50),
    target_id BIGINT,
    content TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_user_activities_user ON user_activities (user_id);
CREATE INDEX IF NOT EXISTS idx_user_activities_type ON user_activities (activity_type);
CREATE INDEX IF NOT EXISTS idx_user_follows_user ON user_follows (user_id);
CREATE INDEX IF NOT EXISTS idx_review_likes_user ON review_likes (user_id);

-- ==================== 食客统计种子数据 ====================

INSERT INTO user_follows (user_id, merchant_id) VALUES (2, 1) ON CONFLICT DO NOTHING;
INSERT INTO user_follows (user_id, merchant_id) VALUES (2, 2) ON CONFLICT DO NOTHING;
INSERT INTO user_follows (user_id, merchant_id) VALUES (2, 3) ON CONFLICT DO NOTHING;
INSERT INTO user_follows (user_id, merchant_id) VALUES (3, 1) ON CONFLICT DO NOTHING;
INSERT INTO user_follows (user_id, merchant_id) VALUES (3, 4) ON CONFLICT DO NOTHING;
INSERT INTO user_follows (user_id, merchant_id) VALUES (4, 2) ON CONFLICT DO NOTHING;
INSERT INTO user_follows (user_id, merchant_id) VALUES (4, 5) ON CONFLICT DO NOTHING;
INSERT INTO user_follows (user_id, merchant_id) VALUES (5, 3) ON CONFLICT DO NOTHING;

INSERT INTO user_activities (user_id, activity_type, target_type, target_id, content) VALUES (2, 'REVIEW', 'MERCHANT', 1, '发布了评价') ON CONFLICT DO NOTHING;
INSERT INTO user_activities (user_id, activity_type, target_type, target_id, content) VALUES (2, 'FOLLOW', 'MERCHANT', 2, '关注了商家') ON CONFLICT DO NOTHING;
INSERT INTO user_activities (user_id, activity_type, target_type, target_id, content) VALUES (2, 'LIKE', 'REVIEW', 1, '点赞了评价') ON CONFLICT DO NOTHING;
INSERT INTO user_activities (user_id, activity_type, target_type, target_id, content) VALUES (2, 'REVIEW', 'MERCHANT', 2, '发布了评价') ON CONFLICT DO NOTHING;
INSERT INTO user_activities (user_id, activity_type, target_type, target_id, content) VALUES (2, 'SEARCH', NULL, NULL, '搜索了火锅') ON CONFLICT DO NOTHING;
INSERT INTO user_activities (user_id, activity_type, target_type, target_id, content) VALUES (3, 'REVIEW', 'MERCHANT', 1, '发布了评价') ON CONFLICT DO NOTHING;
INSERT INTO user_activities (user_id, activity_type, target_type, target_id, content) VALUES (3, 'FOLLOW', 'MERCHANT', 4, '关注了商家') ON CONFLICT DO NOTHING;
INSERT INTO user_activities (user_id, activity_type, target_type, target_id, content) VALUES (3, 'LIKE', 'REVIEW', 2, '点赞了评价') ON CONFLICT DO NOTHING;
INSERT INTO user_activities (user_id, activity_type, target_type, target_id, content) VALUES (4, 'REVIEW', 'MERCHANT', 2, '发布了评价') ON CONFLICT DO NOTHING;
INSERT INTO user_activities (user_id, activity_type, target_type, target_id, content) VALUES (4, 'FOLLOW', 'MERCHANT', 5, '关注了商家') ON CONFLICT DO NOTHING;