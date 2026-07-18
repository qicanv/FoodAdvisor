-- ============================================
-- FoodAdvisor 区域热词模块建表脚本
-- 功能：基于各区域商家评价内容，自动提取高频热词
-- ============================================

-- ============================================
-- 8.1 区域热词表 region_hot_words
-- ============================================
CREATE TABLE IF NOT EXISTS region_hot_words (
    id BIGSERIAL PRIMARY KEY,

    -- 区域编码，关联 merchants.region_code，如 "310100"（上海市）
    region_code VARCHAR(50) NOT NULL,

    -- 热词文本，如 "麻辣鲜香"、"排队久"、"性价比高"
    word VARCHAR(100) NOT NULL,

    -- 热词分类：TASTE(口味) / SERVICE(服务) / ENVIRONMENT(环境) /
    --          PRICE(价格) / SPEED(速度) / GENERAL(综合)
    category VARCHAR(30) NOT NULL DEFAULT 'GENERAL',

    -- 情感倾向：POSITIVE / NEUTRAL / NEGATIVE
    sentiment VARCHAR(20),

    -- 热度分数 = 提及频次权重 + 时间衰减因子 + 情感权重
    -- 范围 0.00 ~ 100.00，越大越"热"
    heat_score NUMERIC(6, 2) NOT NULL DEFAULT 0,

    -- 该热词在统计周期内的提及次数
    mention_count INTEGER NOT NULL DEFAULT 0,

    -- 该热词关联的评价数量（去重）
    review_count INTEGER NOT NULL DEFAULT 0,

    -- 该热词关联的商家数量（去重）
    merchant_count INTEGER NOT NULL DEFAULT 0,

    -- 正面评价占比 0.00 ~ 1.00
    positive_ratio NUMERIC(5, 4),

    -- 数据来源类型：AI_TAG(基于AI标签) / KEYWORD_EXTRACT(基于关键词提取)
    source_type VARCHAR(30) NOT NULL DEFAULT 'AI_TAG',

    -- 统计周期类型：DAILY / WEEKLY / MONTHLY
    period_type VARCHAR(10) NOT NULL DEFAULT 'WEEKLY',

    -- 统计起始日期
    period_start DATE NOT NULL,

    -- 统计截止日期
    period_end DATE NOT NULL,

    -- 热词生成的快照版本号（用于区分不同批次的生成结果）
    version INTEGER NOT NULL DEFAULT 1,

    -- 状态：ACTIVE / OUTDATED
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',

    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    -- 同一区域 + 同一热词 + 同一周期 + 同一版本的唯一约束
    CONSTRAINT uk_region_hot_word_period
        UNIQUE (region_code, word, period_type, period_start, period_end, version),

    CONSTRAINT ck_hot_word_category
        CHECK (category IN (
            'TASTE', 'SERVICE', 'ENVIRONMENT', 'PRICE',
            'SPEED', 'GENERAL'
        )),

    CONSTRAINT ck_hot_word_sentiment
        CHECK (sentiment IS NULL OR sentiment IN ('POSITIVE', 'NEUTRAL', 'NEGATIVE', 'MIXED')),

    CONSTRAINT ck_hot_word_heat_score
        CHECK (heat_score >= 0 AND heat_score <= 100),

    CONSTRAINT ck_hot_word_mention_count
        CHECK (mention_count >= 0),

    CONSTRAINT ck_hot_word_review_count
        CHECK (review_count >= 0),

    CONSTRAINT ck_hot_word_merchant_count
        CHECK (merchant_count >= 0),

    CONSTRAINT ck_hot_word_positive_ratio
        CHECK (positive_ratio IS NULL OR positive_ratio BETWEEN 0 AND 1),

    CONSTRAINT ck_hot_word_source_type
        CHECK (source_type IN ('AI_TAG', 'KEYWORD_EXTRACT')),

    CONSTRAINT ck_hot_word_period_type
        CHECK (period_type IN ('DAILY', 'WEEKLY', 'MONTHLY')),

    CONSTRAINT ck_hot_word_status
        CHECK (status IN ('ACTIVE', 'OUTDATED'))
);

-- ============================================
-- 8.2 区域热词索引
-- ============================================
-- 按区域 + 热度查询（最常用）
CREATE INDEX IF NOT EXISTS idx_hot_words_region_heat
    ON region_hot_words(region_code, status, heat_score DESC);

-- 按区域 + 分类查询
CREATE INDEX IF NOT EXISTS idx_hot_words_region_category
    ON region_hot_words(region_code, category, status, heat_score DESC);

-- 按周期查询（用于清理过期数据）
CREATE INDEX IF NOT EXISTS idx_hot_words_period
    ON region_hot_words(period_type, period_end, status);

-- 按版本过滤
CREATE INDEX IF NOT EXISTS idx_hot_words_version
    ON region_hot_words(version, status);

-- 按创建时间排序
CREATE INDEX IF NOT EXISTS idx_hot_words_created
    ON region_hot_words(created_at DESC);
