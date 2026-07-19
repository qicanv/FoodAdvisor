-- ============================================================
-- EPIC-02 故事7：评价辅助回复 — review_reply_draft 表
-- ============================================================
-- 商家用户可以通过 AI 生成评价回复建议，系统根据好评/差评采用
-- 不同的回复策略。生成的回复以草稿形式保存，商家必须确认或编辑
-- 后才能发布为正式回复，系统不自动发布。
-- ============================================================

CREATE TABLE review_reply_draft (
    -- 主键
    id BIGSERIAL PRIMARY KEY,

    -- 关联的评价 ID（来自 reviews 表）
    review_id BIGINT NOT NULL,

    -- 关联的商家 ID（来自 merchants 表），用于权限校验
    merchant_id BIGINT NOT NULL,

    -- AI 生成的原始回复建议内容（不可为空，AI 必须返回结果）
    generated_content TEXT NOT NULL,

    -- 商家编辑后的回复内容（可为 NULL，NULL 表示未编辑）
    -- 发布时优先使用编辑后的内容
    edited_content TEXT,

    -- 回复策略：
    --   POSITIVE — 好评策略（感谢 + 回应具体优点）
    --   NEGATIVE — 差评策略（道歉 + 问题说明 + 改进承诺）
    strategy VARCHAR(20) NOT NULL,

    -- 草稿状态：
    --   DRAFT     — 待处理（商家可编辑、发布或丢弃）
    --   PUBLISHED — 已发布为正式回复
    --   DISCARDED — 已丢弃（商家选择不使用）
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',

    -- AI 生成回复的时间
    generated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    -- 商家确认发布为正式回复的时间
    published_at TIMESTAMPTZ,

    -- 确认发布的商家用户 ID（来自 merchant_members 表）
    confirmed_by BIGINT,

    -- AI 服务调用的追踪 ID，用于排查问题和审计
    ai_trace_id VARCHAR(100),

    -- 生成该回复所使用的 AI 模型名称
    model_name VARCHAR(100),

    -- 记录创建和更新时间
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    -- ========== 外键约束 ==========
    CONSTRAINT fk_reply_draft_review
        FOREIGN KEY (review_id) REFERENCES reviews(id) ON DELETE CASCADE,

    CONSTRAINT fk_reply_draft_merchant
        FOREIGN KEY (merchant_id) REFERENCES merchants(id) ON DELETE RESTRICT,

    CONSTRAINT fk_reply_draft_confirmed_by
        FOREIGN KEY (confirmed_by) REFERENCES merchant_members(id) ON DELETE SET NULL,

    -- ========== CHECK 约束 ==========
    CONSTRAINT ck_reply_draft_status
        CHECK (status IN ('DRAFT', 'PUBLISHED', 'DISCARDED')),

    CONSTRAINT ck_reply_draft_strategy
        CHECK (strategy IN ('POSITIVE', 'NEGATIVE'))
);

-- ========== 索引 ==========

-- 按评价 ID 快速查找草稿（最常用查询）
CREATE INDEX idx_reply_draft_review_id ON review_reply_draft(review_id);

-- 按商家 ID 查询（用于管理后台统计）
CREATE INDEX idx_reply_draft_merchant_id ON review_reply_draft(merchant_id);

-- 按状态筛选（查找活跃草稿或已发布记录）
CREATE INDEX idx_reply_draft_status ON review_reply_draft(status);

-- 复合索引：商家 + 状态（用于商家查看自己的草稿列表）
CREATE INDEX idx_reply_draft_merchant_status ON review_reply_draft(merchant_id, status);
