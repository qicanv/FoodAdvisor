-- 对齐 reviews 表与 Review 实体字段

ALTER TABLE reviews
    ADD COLUMN IF NOT EXISTS review_time TIMESTAMPTZ;

-- 新发表评价允许不填写 review_time，
-- 主要使用 published_at / created_at
ALTER TABLE reviews
    ALTER COLUMN review_time DROP NOT NULL;

ALTER TABLE reviews
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ;

ALTER TABLE reviews
    ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMPTZ;

-- 旧数据兼容：补充原始评价时间
UPDATE reviews
SET review_time = COALESCE(review_time, published_at, created_at)
WHERE review_time IS NULL
  AND published_at IS NOT NULL;

-- 旧数据兼容：补充更新时间
UPDATE reviews
SET updated_at = COALESCE(updated_at, edited_at, created_at)
WHERE updated_at IS NULL;