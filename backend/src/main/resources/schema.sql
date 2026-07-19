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