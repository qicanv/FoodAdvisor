\set ON_ERROR_STOP on

-- =========================================================
-- 专题管理和食客管理扩展脚本
-- 执行顺序: 在 01_schema.sql 之后执行
-- =========================================================

BEGIN;

-- =========================================================
-- 1. 内容标签表 (content_tags)
-- =========================================================

CREATE TABLE IF NOT EXISTS content_tags (
    id BIGSERIAL PRIMARY KEY,
    code VARCHAR(50) NOT NULL,
    name VARCHAR(100) NOT NULL,
    category VARCHAR(50) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_content_tags_code UNIQUE (code),
    CONSTRAINT ck_content_tags_category CHECK (category IN ('category', 'cuisine', 'scene', 'environment', 'price')),
    CONSTRAINT ck_content_tags_status CHECK (status IN ('ACTIVE', 'DISABLED'))
);

-- =========================================================
-- 2. 标签与商家关联表 (merchant_tag_relations)
-- =========================================================

CREATE TABLE IF NOT EXISTS merchant_tag_relations (
    id BIGSERIAL PRIMARY KEY,
    merchant_id BIGINT NOT NULL,
    tag_id BIGINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_merchant_tag_relations_merchant FOREIGN KEY (merchant_id) REFERENCES merchants(id) ON DELETE CASCADE,
    CONSTRAINT fk_merchant_tag_relations_tag FOREIGN KEY (tag_id) REFERENCES content_tags(id) ON DELETE CASCADE,
    CONSTRAINT uk_merchant_tag_relations UNIQUE (merchant_id, tag_id)
);

-- =========================================================
-- 3. 专题表 (topics)
-- =========================================================

CREATE TABLE IF NOT EXISTS topics (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(200) NOT NULL,
    description TEXT,
    cover_url VARCHAR(1000),
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    start_at TIMESTAMPTZ,
    end_at TIMESTAMPTZ,
    created_by BIGINT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_topics_status CHECK (status IN ('DRAFT', 'PUBLISHED', 'ARCHIVED', 'OFFLINE')),
    CONSTRAINT fk_topics_created_by FOREIGN KEY (created_by) REFERENCES users(id) ON DELETE SET NULL
);

-- =========================================================
-- 4. 专题与商家关联表 (topic_merchants)
-- =========================================================

CREATE TABLE IF NOT EXISTS topic_merchants (
    id BIGSERIAL PRIMARY KEY,
    topic_id BIGINT NOT NULL,
    merchant_id BIGINT NOT NULL,
    sort_order INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_topic_merchants_topic FOREIGN KEY (topic_id) REFERENCES topics(id) ON DELETE CASCADE,
    CONSTRAINT fk_topic_merchants_merchant FOREIGN KEY (merchant_id) REFERENCES merchants(id) ON DELETE CASCADE,
    CONSTRAINT uk_topic_merchants UNIQUE (topic_id, merchant_id)
);

CREATE TABLE IF NOT EXISTS topic_tags (
    id BIGSERIAL PRIMARY KEY,
    topic_id BIGINT NOT NULL,
    tag_id BIGINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_topic_tags_topic FOREIGN KEY (topic_id) REFERENCES topics(id) ON DELETE CASCADE,
    CONSTRAINT fk_topic_tags_tag FOREIGN KEY (tag_id) REFERENCES content_tags(id) ON DELETE CASCADE,
    CONSTRAINT uk_topic_tags UNIQUE (topic_id, tag_id)
);

-- =========================================================
-- 添加索引
-- =========================================================

CREATE INDEX IF NOT EXISTS idx_content_tags_category ON content_tags (category);
CREATE INDEX IF NOT EXISTS idx_content_tags_status ON content_tags (status);
CREATE INDEX IF NOT EXISTS idx_merchant_tag_relations_tag ON merchant_tag_relations (tag_id);
CREATE INDEX IF NOT EXISTS idx_merchant_tag_relations_merchant ON merchant_tag_relations (merchant_id);
CREATE INDEX IF NOT EXISTS idx_topics_status ON topics (status);
CREATE INDEX IF NOT EXISTS idx_topics_created_by ON topics (created_by);
CREATE INDEX IF NOT EXISTS idx_topic_merchants_topic ON topic_merchants (topic_id);
CREATE INDEX IF NOT EXISTS idx_topic_merchants_merchant ON topic_merchants (merchant_id);
CREATE INDEX IF NOT EXISTS idx_topic_tags_topic ON topic_tags (topic_id);
CREATE INDEX IF NOT EXISTS idx_topic_tags_tag ON topic_tags (tag_id);

COMMIT;

-- =========================================================
-- 种子数据
-- =========================================================

BEGIN;

-- =========================================================
-- 内容标签种子数据
-- =========================================================

INSERT INTO content_tags (code, name, category, status) VALUES
    -- 餐饮类型 (category)
    ('cat-bbq', '烧烤', 'category', 'ACTIVE'),
    ('cat-hotpot', '火锅', 'category', 'ACTIVE'),
    ('cat-fastfood', '快餐', 'category', 'ACTIVE'),
    ('cat-seafood', '海鲜', 'category', 'ACTIVE'),
    ('cat-dessert', '甜品', 'category', 'ACTIVE'),
    ('cat-noodle', '面食', 'category', 'ACTIVE'),
    ('cat-rice', '米饭', 'category', 'ACTIVE'),
    ('cat-value', '性价比', 'category', 'ACTIVE'),
    
    -- 菜系 (cuisine)
    ('cui-sichuan', '川菜', 'cuisine', 'ACTIVE'),
    ('cui-guangdong', '粤菜', 'cuisine', 'ACTIVE'),
    ('cui-shandong', '鲁菜', 'cuisine', 'ACTIVE'),
    ('cui-jiangsu', '苏菜', 'cuisine', 'ACTIVE'),
    ('cui-zhejiang', '浙菜', 'cuisine', 'ACTIVE'),
    ('cui-fujian', '闽菜', 'cuisine', 'ACTIVE'),
    ('cui-hunan', '湘菜', 'cuisine', 'ACTIVE'),
    ('cui-anhui', '徽菜', 'cuisine', 'ACTIVE'),
    ('cui-japanese', '日料', 'cuisine', 'ACTIVE'),
    ('cui-korean', '韩式', 'cuisine', 'ACTIVE'),
    ('cui-western', '西餐', 'cuisine', 'ACTIVE'),
    
    -- 消费场景 (scene)
    ('sce-dinner', '夜宵', 'scene', 'ACTIVE'),
    ('sce-date', '约会', 'scene', 'ACTIVE'),
    ('sce-family', '家庭聚餐', 'scene', 'ACTIVE'),
    ('sce-business', '商务宴请', 'scene', 'ACTIVE'),
    ('sce-party', '朋友聚会', 'scene', 'ACTIVE'),
    
    -- 环境特点 (environment)
    ('env-cozy', '环境优雅', 'environment', 'ACTIVE'),
    ('env-instagram', '网红', 'environment', 'ACTIVE'),
    ('env-private', '私密', 'environment', 'ACTIVE'),
    ('env-noisy', '热闹', 'environment', 'ACTIVE'),
    
    -- 价格区间 (price)
    ('pri-budget', '人均50以下', 'price', 'ACTIVE'),
    ('pri-mid', '人均50-100', 'price', 'ACTIVE'),
    ('pri-premium', '人均100-200', 'price', 'ACTIVE'),
    ('pri-luxury', '人均200以上', 'price', 'ACTIVE')
ON CONFLICT DO NOTHING;

-- =========================================================
-- 标签与商家关联（根据商家的category和cuisine字段自动关联）
-- =========================================================

INSERT INTO merchant_tag_relations (merchant_id, tag_id)
SELECT m.id, t.id
FROM merchants m
CROSS JOIN content_tags t
WHERE 
    (t.category = 'category' AND m.category = t.name)
    OR (t.category = 'cuisine' AND m.cuisine = t.name)
    OR (t.category = 'scene' AND m.category IN ('烧烤', '火锅') AND t.name = '夜宵')
    OR (t.category = 'price' AND (
        (m.average_price < 50 AND t.name = '人均50以下')
        OR (m.average_price >= 50 AND m.average_price < 100 AND t.name = '人均50-100')
        OR (m.average_price >= 100 AND m.average_price < 200 AND t.name = '人均100-200')
        OR (m.average_price >= 200 AND t.name = '人均200以上')
    ))
ON CONFLICT DO NOTHING;

-- =========================================================
-- 专题种子数据
-- =========================================================

INSERT INTO topics (name, description, cover_url, status, created_by) VALUES
    (
        '夜宵好去处',
        '深夜食堂推荐，探索城市夜晚的美味，让你的夜宵时光不再单调。',
        'https://trae-api-cn.mchost.guru/api/ide/v1/text_to_image?prompt=night%20food%20street%20with%20delicious%20snacks&image_size=landscape_16_9',
        'PUBLISHED',
        (SELECT id FROM users WHERE username = 'demo_admin' LIMIT 1)
    ),
    (
        '网红打卡餐厅',
        '刷爆朋友圈的高颜值餐厅，每一处都是拍照打卡点。',
        'https://trae-api-cn.mchost.guru/api/ide/v1/text_to_image?prompt=instagrammable%20restaurant%20beautiful%20decor&image_size=landscape_16_9',
        'PUBLISHED',
        (SELECT id FROM users WHERE username = 'demo_admin' LIMIT 1)
    ),
    (
        '浪漫约会餐厅',
        '浪漫氛围，甜蜜约会首选，让每一次约会都充满惊喜。',
        'https://trae-api-cn.mchost.guru/api/ide/v1/text_to_image?prompt=romantic%20dinner%20restaurant%20candlelight&image_size=landscape_16_9',
        'DRAFT',
        (SELECT id FROM users WHERE username = 'demo_admin' LIMIT 1)
    ),
    (
        '商务宴请精选',
        '高端大气，商务聚餐首选，彰显品味与格调。',
        'https://trae-api-cn.mchost.guru/api/ide/v1/text_to_image?prompt=luxury%20business%20dining%20elegant&image_size=landscape_16_9',
        'OFFLINE',
        (SELECT id FROM users WHERE username = 'demo_admin' LIMIT 1)
    ),
    (
        '高性价比美食',
        '性价比超高的美食推荐，花小钱享受大美味，让你的每一餐都物超所值。',
        'https://trae-api-cn.mchost.guru/api/ide/v1/text_to_image?prompt=affordable%20delicious%20food%20selection%20good%20value&image_size=landscape_16_9',
        'PUBLISHED',
        (SELECT id FROM users WHERE username = 'demo_admin' LIMIT 1)
    )
ON CONFLICT DO NOTHING;

-- =========================================================
-- 专题与商家关联
-- =========================================================

INSERT INTO topic_merchants (topic_id, merchant_id, sort_order) VALUES
    ((SELECT id FROM topics WHERE name = '夜宵好去处'), (SELECT id FROM merchants WHERE category = '火锅' LIMIT 1), 1),
    ((SELECT id FROM topics WHERE name = '夜宵好去处'), (SELECT id FROM merchants WHERE category = '火锅' LIMIT 1 OFFSET 1), 2),
    ((SELECT id FROM topics WHERE name = '夜宵好去处'), (SELECT id FROM merchants WHERE category = '火锅' LIMIT 1 OFFSET 2), 3),
    
    ((SELECT id FROM topics WHERE name = '网红打卡餐厅'), (SELECT id FROM merchants WHERE category = '休闲餐饮' LIMIT 1), 1),
    ((SELECT id FROM topics WHERE name = '网红打卡餐厅'), (SELECT id FROM merchants WHERE category = '休闲餐饮' LIMIT 1 OFFSET 1), 2),
    
    ((SELECT id FROM topics WHERE name = '浪漫约会餐厅'), (SELECT id FROM merchants WHERE category = '休闲餐饮' LIMIT 1), 1),
    ((SELECT id FROM topics WHERE name = '浪漫约会餐厅'), (SELECT id FROM merchants WHERE cuisine = '粤菜' LIMIT 1), 2),
    ((SELECT id FROM topics WHERE name = '浪漫约会餐厅'), (SELECT id FROM merchants WHERE category = '休闲餐饮' LIMIT 1 OFFSET 1), 3),
    
    ((SELECT id FROM topics WHERE name = '商务宴请精选'), (SELECT id FROM merchants WHERE cuisine = '粤菜' LIMIT 1), 1),
    ((SELECT id FROM topics WHERE name = '商务宴请精选'), (SELECT id FROM merchants WHERE cuisine = '粤菜' LIMIT 1 OFFSET 1), 2),
    
    ((SELECT id FROM topics WHERE name = '高性价比美食'), (SELECT id FROM merchants WHERE average_price < 50 LIMIT 1), 1),
    ((SELECT id FROM topics WHERE name = '高性价比美食'), (SELECT id FROM merchants WHERE average_price < 50 LIMIT 1 OFFSET 1), 2),
    ((SELECT id FROM topics WHERE name = '高性价比美食'), (SELECT id FROM merchants WHERE average_price < 50 LIMIT 1 OFFSET 2), 3),
    ((SELECT id FROM topics WHERE name = '高性价比美食'), (SELECT id FROM merchants WHERE average_price < 50 LIMIT 1 OFFSET 3), 4)
ON CONFLICT DO NOTHING;

INSERT INTO topic_tags (topic_id, tag_id) VALUES
    ((SELECT id FROM topics WHERE name = '夜宵好去处'), (SELECT id FROM content_tags WHERE name = '夜宵')),
    ((SELECT id FROM topics WHERE name = '夜宵好去处'), (SELECT id FROM content_tags WHERE name = '火锅')),
    ((SELECT id FROM topics WHERE name = '夜宵好去处'), (SELECT id FROM content_tags WHERE name = '川菜')),
    ((SELECT id FROM topics WHERE name = '夜宵好去处'), (SELECT id FROM content_tags WHERE name = '人均50-100')),
    
    ((SELECT id FROM topics WHERE name = '网红打卡餐厅'), (SELECT id FROM content_tags WHERE name = '网红')),
    ((SELECT id FROM topics WHERE name = '网红打卡餐厅'), (SELECT id FROM content_tags WHERE name = '环境优雅')),
    ((SELECT id FROM topics WHERE name = '网红打卡餐厅'), (SELECT id FROM content_tags WHERE name = '人均50-100')),
    
    ((SELECT id FROM topics WHERE name = '浪漫约会餐厅'), (SELECT id FROM content_tags WHERE name = '约会')),
    ((SELECT id FROM topics WHERE name = '浪漫约会餐厅'), (SELECT id FROM content_tags WHERE name = '环境优雅')),
    ((SELECT id FROM topics WHERE name = '浪漫约会餐厅'), (SELECT id FROM content_tags WHERE name = '粤菜')),
    ((SELECT id FROM topics WHERE name = '浪漫约会餐厅'), (SELECT id FROM content_tags WHERE name = '人均100-200')),
    
    ((SELECT id FROM topics WHERE name = '商务宴请精选'), (SELECT id FROM content_tags WHERE name = '商务宴请')),
    ((SELECT id FROM topics WHERE name = '商务宴请精选'), (SELECT id FROM content_tags WHERE name = '粤菜')),
    ((SELECT id FROM topics WHERE name = '商务宴请精选'), (SELECT id FROM content_tags WHERE name = '环境优雅')),
    ((SELECT id FROM topics WHERE name = '商务宴请精选'), (SELECT id FROM content_tags WHERE name = '人均100-200')),
    
    ((SELECT id FROM topics WHERE name = '高性价比美食'), (SELECT id FROM content_tags WHERE name = '性价比')),
    ((SELECT id FROM topics WHERE name = '高性价比美食'), (SELECT id FROM content_tags WHERE name = '川菜')),
    ((SELECT id FROM topics WHERE name = '高性价比美食'), (SELECT id FROM content_tags WHERE name = '火锅')),
    ((SELECT id FROM topics WHERE name = '高性价比美食'), (SELECT id FROM content_tags WHERE name = '人均50以下'))
ON CONFLICT DO NOTHING;

-- =========================================================
-- 食客用户种子数据
-- 密码: Demo@123456 (BCrypt加密)
-- =========================================================

INSERT INTO users (username, password_hash, nickname, email, phone, role, status, failed_login_count, locked_until, last_login_at, password_changed_at, created_at, updated_at) VALUES
    ('demo_diner_1', '$2a$10$Gbdx0A6ZGPTgMXKzDJkOyOCgnF5Gs1LyT3PoR7PnQPZvD8312/7p6', '美食达人小王', 'diner1@example.invalid', '19900000002', 'USER', 'ACTIVE', 0, NULL, '2026-07-20 14:30:00+08', NULL, '2026-01-01 00:00:00+08', '2026-07-20 14:30:00+08'),
    ('demo_diner_2', '$2a$10$Gbdx0A6ZGPTgMXKzDJkOyOCgnF5Gs1LyT3PoR7PnQPZvD8312/7p6', '评价小王子', 'diner2@example.invalid', '19900000003', 'USER', 'ACTIVE', 0, NULL, '2026-07-19 10:15:00+08', NULL, '2026-01-05 00:00:00+08', '2026-07-19 10:15:00+08'),
    ('demo_diner_3', '$2a$10$Gbdx0A6ZGPTgMXKzDJkOyOCgnF5Gs1LyT3PoR7PnQPZvD8312/7p6', '馋嘴猫', 'diner3@example.invalid', '19900000004', 'USER', 'ACTIVE', 0, NULL, '2026-07-18 16:45:00+08', NULL, '2026-01-10 00:00:00+08', '2026-07-18 16:45:00+08'),
    ('demo_diner_4', '$2a$10$Gbdx0A6ZGPTgMXKzDJkOyOCgnF5Gs1LyT3PoR7PnQPZvD8312/7p6', '晚餐爱好者', 'diner4@example.invalid', '19900000005', 'USER', 'ACTIVE', 0, NULL, '2026-07-17 09:20:00+08', NULL, '2026-01-15 00:00:00+08', '2026-07-17 09:20:00+08'),
    ('demo_diner_5', '$2a$10$Gbdx0A6ZGPTgMXKzDJkOyOCgnF5Gs1LyT3PoR7PnQPZvD8312/7p6', '美食评论家', 'diner5@example.invalid', '19900000006', 'USER', 'ACTIVE', 0, NULL, '2026-07-16 20:00:00+08', NULL, '2026-01-20 00:00:00+08', '2026-07-16 20:00:00+08'),
    ('demo_diner_6', '$2a$10$Gbdx0A6ZGPTgMXKzDJkOyOCgnF5Gs1LyT3PoR7PnQPZvD8312/7p6', '味蕾探险家', 'diner6@example.invalid', '19900000007', 'USER', 'ACTIVE', 0, NULL, '2026-07-15 11:30:00+08', NULL, '2026-02-01 00:00:00+08', '2026-07-15 11:30:00+08'),
    ('demo_diner_7', '$2a$10$Gbdx0A6ZGPTgMXKzDJkOyOCgnF5Gs1LyT3PoR7PnQPZvD8312/7p6', '美食女孩', 'diner7@example.invalid', '19900000008', 'USER', 'DISABLED', 0, NULL, NULL, NULL, '2026-02-10 00:00:00+08', '2026-06-01 00:00:00+08'),
    ('demo_diner_8', '$2a$10$Gbdx0A6ZGPTgMXKzDJkOyOCgnF5Gs1LyT3PoR7PnQPZvD8312/7p6', '吃货猎人', 'diner8@example.invalid', '19900000009', 'USER', 'DISABLED', 0, NULL, NULL, NULL, '2026-03-01 00:00:00+08', '2026-05-15 00:00:00+08'),
    ('demo_disabled', '$2a$10$Gbdx0A6ZGPTgMXKzDJkOyOCgnF5Gs1LyT3PoR7PnQPZvD8312/7p6', '测试禁用账号', 'disabled@example.invalid', '19900000010', 'USER', 'LOCKED', 5, '2026-08-01 00:00:00+08', NULL, NULL, '2026-03-15 00:00:00+08', '2026-07-01 00:00:00+08'),
    ('demo_locked', '$2a$10$Gbdx0A6ZGPTgMXKzDJkOyOCgnF5Gs1LyT3PoR7PnQPZvD8312/7p6', '测试锁定账号', 'locked@example.invalid', '19900000011', 'USER', 'LOCKED', 5, '2026-08-15 00:00:00+08', NULL, NULL, '2026-04-01 00:00:00+08', '2026-07-10 00:00:00+08')
ON CONFLICT DO NOTHING;

COMMIT;

-- =========================================================
-- 5. 用户关注商家表 (user_follows)
-- =========================================================

BEGIN;

CREATE TABLE IF NOT EXISTS user_follows (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    merchant_id BIGINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_user_follows UNIQUE (user_id, merchant_id),
    CONSTRAINT fk_user_follows_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_user_follows_merchant FOREIGN KEY (merchant_id) REFERENCES merchants(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS review_likes (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    review_id BIGINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_review_likes UNIQUE (user_id, review_id),
    CONSTRAINT fk_review_likes_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_review_likes_review FOREIGN KEY (review_id) REFERENCES reviews(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS user_activities (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    activity_type VARCHAR(50) NOT NULL,
    target_type VARCHAR(50),
    target_id BIGINT,
    content TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_user_activities_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_user_activities_user ON user_activities (user_id);
CREATE INDEX IF NOT EXISTS idx_user_activities_type ON user_activities (activity_type);
CREATE INDEX IF NOT EXISTS idx_user_follows_user ON user_follows (user_id);
CREATE INDEX IF NOT EXISTS idx_review_likes_user ON review_likes (user_id);

COMMIT;

-- =========================================================
-- 食客统计种子数据
-- =========================================================

BEGIN;

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

COMMIT;

-- =========================================================
-- 脚本执行完毕
-- =========================================================
-- 执行命令: psql -d foodadvisor -f 02_topic_and_diner_schema.sql
-- =========================================================