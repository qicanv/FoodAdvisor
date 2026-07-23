\set ON_ERROR_STOP on

BEGIN;

-- ============================================================
-- 1. Demo merchants
-- ============================================================

-- Update existing active demo merchants so rerunning this seed also repairs
-- previously inserted demo data.
WITH merchant_seed (
    merchant_code,
    name,
    category,
    cuisine,
    average_price,
    address,
    region_code,
    longitude,
    latitude,
    description,
    environment_tags,
    platform_status,
    operation_status
) AS (
    VALUES
        (
            'V2-BBQ-NIGHT',
            'V2星河夜烤',
            '烧烤',
            '烧烤',
            88,
            '成都市演示路V2号',
            'CD-JJ',
            104.0810,
            30.6510,
            '虚构演示商家：夜宵烧烤，适合朋友聚餐与拍照。',
            '[
                "热闹",
                "适合拍照",
                "氛围感",
                "适合聚餐",
                "朋友聚会"
            ]'::jsonb,
            'ACTIVE',
            'OPERATING'
        ),
        (
            'V2-DATE-CAFE',
            'V2微光咖啡',
            '咖啡甜品',
            '咖啡甜品',
            96,
            '成都市演示路V2-2号',
            'CD-JJ',
            104.0820,
            30.6520,
            '虚构演示商家：适合约会与拍照。',
            '[
                "安静",
                "适合拍照",
                "氛围感",
                "约会"
            ]'::jsonb,
            'ACTIVE',
            'OPERATING'
        ),
        (
            'V2-FILTER-SUSPENDED',
            'V2停业样本',
            '中餐',
            '川菜',
            62,
            '成都市演示路V2-3号',
            'CD-JJ',
            104.0830,
            30.6530,
            '仅用于过滤测试。',
            '[
                "家庭聚餐",
                "商务宴请"
            ]'::jsonb,
            'ACTIVE',
            'SUSPENDED'
        ),
        (
            'V2-FILTER-DISABLED',
            'V2禁用样本',
            '中餐',
            '川菜',
            58,
            '成都市演示路V2-4号',
            'CD-JJ',
            104.0840,
            30.6540,
            '仅用于平台禁用过滤测试。',
            '[
                "家庭聚餐"
            ]'::jsonb,
            'DISABLED',
            'OPERATING'
        ),
        (
            'V2-FILTER-ARCHIVED',
            'V2归档样本',
            '西餐',
            '西餐',
            120,
            '成都市演示路V2-5号',
            'CD-JJ',
            104.0850,
            30.6550,
            '仅用于归档过滤测试。',
            '[
                "商务宴请"
            ]'::jsonb,
            'ARCHIVED',
            'CLOSED_PERMANENTLY'
        )
)
UPDATE merchants AS m
SET name = seed.name,
    category = seed.category,
    cuisine = seed.cuisine,
    average_price = seed.average_price,
    address = seed.address,
    region_code = seed.region_code,
    longitude = seed.longitude,
    latitude = seed.latitude,
    description = seed.description,
    environment_tags = seed.environment_tags,
    platform_status = seed.platform_status,
    operation_status = seed.operation_status,
    deleted_at = NULL,
    updated_at = CURRENT_TIMESTAMP
FROM merchant_seed AS seed
WHERE m.merchant_code = seed.merchant_code;


-- Insert active demo merchants that do not yet exist.
WITH merchant_seed (
    merchant_code,
    name,
    category,
    cuisine,
    average_price,
    address,
    region_code,
    longitude,
    latitude,
    description,
    environment_tags,
    platform_status,
    operation_status
) AS (
    VALUES
        (
            'V2-BBQ-NIGHT',
            'V2星河夜烤',
            '烧烤',
            '烧烤',
            88,
            '成都市演示路V2号',
            'CD-JJ',
            104.0810,
            30.6510,
            '虚构演示商家：夜宵烧烤，适合朋友聚餐与拍照。',
            '[
                "热闹",
                "适合拍照",
                "氛围感",
                "适合聚餐",
                "朋友聚会"
            ]'::jsonb,
            'ACTIVE',
            'OPERATING'
        ),
        (
            'V2-DATE-CAFE',
            'V2微光咖啡',
            '咖啡甜品',
            '咖啡甜品',
            96,
            '成都市演示路V2-2号',
            'CD-JJ',
            104.0820,
            30.6520,
            '虚构演示商家：适合约会与拍照。',
            '[
                "安静",
                "适合拍照",
                "氛围感",
                "约会"
            ]'::jsonb,
            'ACTIVE',
            'OPERATING'
        ),
        (
            'V2-FILTER-SUSPENDED',
            'V2停业样本',
            '中餐',
            '川菜',
            62,
            '成都市演示路V2-3号',
            'CD-JJ',
            104.0830,
            30.6530,
            '仅用于过滤测试。',
            '[
                "家庭聚餐",
                "商务宴请"
            ]'::jsonb,
            'ACTIVE',
            'SUSPENDED'
        ),
        (
            'V2-FILTER-DISABLED',
            'V2禁用样本',
            '中餐',
            '川菜',
            58,
            '成都市演示路V2-4号',
            'CD-JJ',
            104.0840,
            30.6540,
            '仅用于平台禁用过滤测试。',
            '[
                "家庭聚餐"
            ]'::jsonb,
            'DISABLED',
            'OPERATING'
        ),
        (
            'V2-FILTER-ARCHIVED',
            'V2归档样本',
            '西餐',
            '西餐',
            120,
            '成都市演示路V2-5号',
            'CD-JJ',
            104.0850,
            30.6550,
            '仅用于归档过滤测试。',
            '[
                "商务宴请"
            ]'::jsonb,
            'ARCHIVED',
            'CLOSED_PERMANENTLY'
        )
)
INSERT INTO merchants (
    merchant_code,
    name,
    category,
    cuisine,
    average_price,
    address,
    region_code,
    longitude,
    latitude,
    description,
    environment_tags,
    platform_status,
    operation_status,
    created_at,
    updated_at
)
SELECT
    seed.merchant_code,
    seed.name,
    seed.category,
    seed.cuisine,
    seed.average_price,
    seed.address,
    seed.region_code,
    seed.longitude,
    seed.latitude,
    seed.description,
    seed.environment_tags,
    seed.platform_status,
    seed.operation_status,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
FROM merchant_seed AS seed
WHERE NOT EXISTS (
    SELECT 1
    FROM merchants AS existing
    WHERE existing.merchant_code = seed.merchant_code
);


-- Update the soft-deleted filtering fixture when it already exists.
UPDATE merchants
SET name = 'V2软删除样本',
    category = '中餐',
    cuisine = '粤菜',
    average_price = 75,
    address = '成都市演示路V2-6号',
    region_code = 'CD-JJ',
    longitude = 104.0860,
    latitude = 30.6560,
    description = '仅用于软删除过滤测试。',
    environment_tags = '["家庭聚餐"]'::jsonb,
    platform_status = 'ACTIVE',
    operation_status = 'OPERATING',
    deleted_at = COALESCE(deleted_at, CURRENT_TIMESTAMP),
    updated_at = CURRENT_TIMESTAMP
WHERE merchant_code = 'V2-FILTER-DELETED';


-- Insert the soft-deleted filtering fixture when it does not exist.
INSERT INTO merchants (
    merchant_code,
    name,
    category,
    cuisine,
    average_price,
    address,
    region_code,
    longitude,
    latitude,
    description,
    environment_tags,
    platform_status,
    operation_status,
    deleted_at,
    created_at,
    updated_at
)
SELECT
    'V2-FILTER-DELETED',
    'V2软删除样本',
    '中餐',
    '粤菜',
    75,
    '成都市演示路V2-6号',
    'CD-JJ',
    104.0860,
    30.6560,
    '仅用于软删除过滤测试。',
    '["家庭聚餐"]'::jsonb,
    'ACTIVE',
    'OPERATING',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
WHERE NOT EXISTS (
    SELECT 1
    FROM merchants
    WHERE merchant_code = 'V2-FILTER-DELETED'
);


-- ============================================================
-- 2. Business hours
-- ============================================================

-- Update existing business-hour rows.
WITH target_hours AS (
    SELECT
        m.id AS merchant_id,
        gs.day_no AS day_of_week
    FROM merchants AS m
    CROSS JOIN generate_series(1, 7) AS gs(day_no)
    WHERE m.merchant_code = 'V2-BBQ-NIGHT'
)
UPDATE merchant_business_hours AS h
SET open_time = '18:00'::time,
    close_time = '02:00'::time,
    is_closed = false,
    crosses_midnight = true,
    updated_at = CURRENT_TIMESTAMP
FROM target_hours AS target
WHERE h.merchant_id = target.merchant_id
  AND h.day_of_week = target.day_of_week;


-- Insert missing business-hour rows.
WITH target_hours AS (
    SELECT
        m.id AS merchant_id,
        gs.day_no AS day_of_week
    FROM merchants AS m
    CROSS JOIN generate_series(1, 7) AS gs(day_no)
    WHERE m.merchant_code = 'V2-BBQ-NIGHT'
)
INSERT INTO merchant_business_hours (
    merchant_id,
    day_of_week,
    open_time,
    close_time,
    is_closed,
    crosses_midnight,
    created_at,
    updated_at
)
SELECT
    target.merchant_id,
    target.day_of_week,
    '18:00'::time,
    '02:00'::time,
    false,
    true,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
FROM target_hours AS target
WHERE NOT EXISTS (
    SELECT 1
    FROM merchant_business_hours AS existing
    WHERE existing.merchant_id = target.merchant_id
      AND existing.day_of_week = target.day_of_week
);


-- ============================================================
-- 3. Demo dish
-- ============================================================

UPDATE dishes AS d
SET price = 38,
    category = '烧烤',
    description = '与夜宵烧烤定位一致的虚构菜品',
    taste_tags = '["咸香"]'::jsonb,
    recommended = true,
    status = 'ACTIVE',
    updated_at = CURRENT_TIMESTAMP
FROM merchants AS m
WHERE d.merchant_id = m.id
  AND m.merchant_code = 'V2-BBQ-NIGHT'
  AND d.name = '炭烤牛肉串';


INSERT INTO dishes (
    merchant_id,
    name,
    price,
    category,
    description,
    taste_tags,
    recommended,
    status,
    created_at,
    updated_at
)
SELECT
    m.id,
    '炭烤牛肉串',
    38,
    '烧烤',
    '与夜宵烧烤定位一致的虚构菜品',
    '["咸香"]'::jsonb,
    true,
    'ACTIVE',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
FROM merchants AS m
WHERE m.merchant_code = 'V2-BBQ-NIGHT'
  AND NOT EXISTS (
      SELECT 1
      FROM dishes AS existing
      WHERE existing.merchant_id = m.id
        AND existing.name = '炭烤牛肉串'
  );


-- ============================================================
-- 4. Review visibility fixtures
-- Only V2-REVIEW-PUBLISHED may participate in public evidence
-- and merchant rating aggregation.
-- ============================================================

-- Repair an existing published fixture.
UPDATE reviews AS r
SET merchant_id = m.id,
    rating = 5,
    content = '夜间营业时间准确，烧烤香气足，朋友聚餐氛围很热闹。',
    source = 'SYSTEM',
    status = 'PUBLISHED',
    moderation_status = 'APPROVED',
    risk_level = 'LOW',
    deleted_at = NULL,
    published_at = COALESCE(r.published_at, CURRENT_TIMESTAMP),
    review_time = COALESCE(r.review_time, CURRENT_TIMESTAMP)
FROM merchants AS m
WHERE r.external_id = 'V2-REVIEW-PUBLISHED'
  AND m.merchant_code = 'V2-BBQ-NIGHT';


INSERT INTO reviews (
    merchant_id,
    rating,
    content,
    source,
    external_id,
    status,
    moderation_status,
    risk_level,
    published_at,
    review_time
)
SELECT
    m.id,
    5,
    '夜间营业时间准确，烧烤香气足，朋友聚餐氛围很热闹。',
    'SYSTEM',
    'V2-REVIEW-PUBLISHED',
    'PUBLISHED',
    'APPROVED',
    'LOW',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
FROM merchants AS m
WHERE m.merchant_code = 'V2-BBQ-NIGHT'
  AND NOT EXISTS (
      SELECT 1
      FROM reviews AS existing
      WHERE existing.external_id = 'V2-REVIEW-PUBLISHED'
  );


-- Repair an existing hidden fixture.
UPDATE reviews AS r
SET merchant_id = m.id,
    rating = 3,
    content = '该评价仅用于验证隐藏内容不会进入推荐证据。',
    source = 'SYSTEM',
    status = 'HIDDEN',
    moderation_status = 'APPROVED',
    risk_level = 'LOW',
    deleted_at = NULL,
    review_time = COALESCE(r.review_time, CURRENT_TIMESTAMP)
FROM merchants AS m
WHERE r.external_id = 'V2-REVIEW-HIDDEN'
  AND m.merchant_code = 'V2-BBQ-NIGHT';


INSERT INTO reviews (
    merchant_id,
    rating,
    content,
    source,
    external_id,
    status,
    moderation_status,
    risk_level,
    review_time
)
SELECT
    m.id,
    3,
    '该评价仅用于验证隐藏内容不会进入推荐证据。',
    'SYSTEM',
    'V2-REVIEW-HIDDEN',
    'HIDDEN',
    'APPROVED',
    'LOW',
    CURRENT_TIMESTAMP
FROM merchants AS m
WHERE m.merchant_code = 'V2-BBQ-NIGHT'
  AND NOT EXISTS (
      SELECT 1
      FROM reviews AS existing
      WHERE existing.external_id = 'V2-REVIEW-HIDDEN'
  );


-- Repair an existing pending-review fixture.
UPDATE reviews AS r
SET merchant_id = m.id,
    rating = 4,
    content = '该评价仅用于验证待审核内容不会进入推荐证据。',
    source = 'SYSTEM',
    status = 'PENDING',
    moderation_status = 'PENDING',
    deleted_at = NULL,
    review_time = COALESCE(r.review_time, CURRENT_TIMESTAMP)
FROM merchants AS m
WHERE r.external_id = 'V2-REVIEW-PENDING'
  AND m.merchant_code = 'V2-DATE-CAFE';


INSERT INTO reviews (
    merchant_id,
    rating,
    content,
    source,
    external_id,
    status,
    moderation_status,
    review_time
)
SELECT
    m.id,
    4,
    '该评价仅用于验证待审核内容不会进入推荐证据。',
    'SYSTEM',
    'V2-REVIEW-PENDING',
    'PENDING',
    'PENDING',
    CURRENT_TIMESTAMP
FROM merchants AS m
WHERE m.merchant_code = 'V2-DATE-CAFE'
  AND NOT EXISTS (
      SELECT 1
      FROM reviews AS existing
      WHERE existing.external_id = 'V2-REVIEW-PENDING'
  );


-- Repair an existing soft-deleted review fixture.
UPDATE reviews AS r
SET merchant_id = m.id,
    rating = 2,
    content = '该评价仅用于验证软删除内容不会进入推荐证据。',
    source = 'SYSTEM',
    status = 'DELETED',
    moderation_status = 'APPROVED',
    deleted_at = COALESCE(r.deleted_at, CURRENT_TIMESTAMP),
    review_time = COALESCE(r.review_time, CURRENT_TIMESTAMP)
FROM merchants AS m
WHERE r.external_id = 'V2-REVIEW-DELETED'
  AND m.merchant_code = 'V2-DATE-CAFE';


INSERT INTO reviews (
    merchant_id,
    rating,
    content,
    source,
    external_id,
    status,
    moderation_status,
    deleted_at,
    review_time
)
SELECT
    m.id,
    2,
    '该评价仅用于验证软删除内容不会进入推荐证据。',
    'SYSTEM',
    'V2-REVIEW-DELETED',
    'DELETED',
    'APPROVED',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
FROM merchants AS m
WHERE m.merchant_code = 'V2-DATE-CAFE'
  AND NOT EXISTS (
      SELECT 1
      FROM reviews AS existing
      WHERE existing.external_id = 'V2-REVIEW-DELETED'
  );


-- ============================================================
-- 5. Recalculate merchant review aggregates
-- Only published, approved and non-deleted reviews are counted.
-- ============================================================

UPDATE merchants AS m
SET rating = (
        SELECT ROUND(AVG(r.rating)::numeric, 1)
        FROM reviews AS r
        WHERE r.merchant_id = m.id
          AND r.status = 'PUBLISHED'
          AND r.moderation_status = 'APPROVED'
          AND r.deleted_at IS NULL
    ),
    review_count = (
        SELECT COUNT(*)::integer
        FROM reviews AS r
        WHERE r.merchant_id = m.id
          AND r.status = 'PUBLISHED'
          AND r.moderation_status = 'APPROVED'
          AND r.deleted_at IS NULL
    ),
    updated_at = CURRENT_TIMESTAMP
WHERE m.merchant_code IN (
    'V2-BBQ-NIGHT',
    'V2-DATE-CAFE'
);


-- ============================================================
-- 6. OpenSearch synchronization outbox
-- ============================================================

INSERT INTO opensearch_sync_tasks (
    source_type,
    source_id,
    operation_type,
    status,
    content_version
)
SELECT
    'MERCHANT',
    m.id,
    'UPSERT',
    'PENDING',
    1
FROM merchants AS m
WHERE m.merchant_code IN (
    'V2-BBQ-NIGHT',
    'V2-DATE-CAFE'
)
  AND NOT EXISTS (
      SELECT 1
      FROM opensearch_sync_tasks AS existing
      WHERE existing.source_type = 'MERCHANT'
        AND existing.source_id = m.id
        AND existing.operation_type = 'UPSERT'
        AND existing.status IN ('PENDING', 'PROCESSING')
  );


INSERT INTO opensearch_sync_tasks (
    source_type,
    source_id,
    operation_type,
    status,
    content_version
)
SELECT
    'REVIEW',
    r.id,
    'UPSERT',
    'PENDING',
    r.current_version
FROM reviews AS r
WHERE r.external_id IN (
    'V2-REVIEW-PUBLISHED',
    'V2-REVIEW-HIDDEN',
    'V2-REVIEW-PENDING',
    'V2-REVIEW-DELETED'
)
  AND NOT EXISTS (
      SELECT 1
      FROM opensearch_sync_tasks AS existing
      WHERE existing.source_type = 'REVIEW'
        AND existing.source_id = r.id
        AND existing.operation_type = 'UPSERT'
        AND existing.status IN ('PENDING', 'PROCESSING')
  );


COMMIT;