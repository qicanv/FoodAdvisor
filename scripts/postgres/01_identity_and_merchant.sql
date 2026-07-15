BEGIN;

-- =========================================================
-- 1. 用户表
-- =========================================================

CREATE TABLE IF NOT EXISTS users (
    id BIGSERIAL PRIMARY KEY,

    username VARCHAR(50) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,

    nickname VARCHAR(100),
    email VARCHAR(255),
    phone VARCHAR(50),

    role VARCHAR(20) NOT NULL DEFAULT 'USER',
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',

    failed_login_count INTEGER NOT NULL DEFAULT 0,
    locked_until TIMESTAMPTZ,
    last_login_at TIMESTAMPTZ,
    password_changed_at TIMESTAMPTZ,

    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMPTZ,

    CONSTRAINT uk_users_username
        UNIQUE (username),

    CONSTRAINT ck_users_role
        CHECK (
            role IN (
                'USER',
                'MERCHANT',
                'OPERATOR',
                'ADMIN'
            )
        ),

    CONSTRAINT ck_users_status
        CHECK (
            status IN (
                'ACTIVE',
                'DISABLED',
                'LOCKED'
            )
        ),

    CONSTRAINT ck_users_failed_login_count
        CHECK (failed_login_count >= 0)
);


-- =========================================================
-- 2. 登录会话表
-- =========================================================

CREATE TABLE IF NOT EXISTS auth_sessions (
    id BIGSERIAL PRIMARY KEY,

    user_id BIGINT NOT NULL,

    session_token_hash VARCHAR(255) NOT NULL,
    refresh_token_hash VARCHAR(255),

    device_info VARCHAR(500),
    ip_address VARCHAR(64),

    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',

    expires_at TIMESTAMPTZ NOT NULL,
    last_active_at TIMESTAMPTZ,

    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    revoked_at TIMESTAMPTZ,

    CONSTRAINT fk_auth_sessions_user
        FOREIGN KEY (user_id)
        REFERENCES users(id)
        ON DELETE CASCADE,

    CONSTRAINT uk_auth_sessions_token_hash
        UNIQUE (session_token_hash),

    CONSTRAINT ck_auth_sessions_status
        CHECK (
            status IN (
                'ACTIVE',
                'REVOKED',
                'EXPIRED'
            )
        )
);


-- =========================================================
-- 3. 登录尝试表
-- =========================================================

CREATE TABLE IF NOT EXISTS login_attempts (
    id BIGSERIAL PRIMARY KEY,

    username VARCHAR(50) NOT NULL,
    user_id BIGINT,

    ip_address VARCHAR(64),
    success BOOLEAN NOT NULL,
    failure_reason VARCHAR(100),

    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_login_attempts_user
        FOREIGN KEY (user_id)
        REFERENCES users(id)
        ON DELETE SET NULL
);


-- =========================================================
-- 4. 商家表
-- =========================================================

CREATE TABLE IF NOT EXISTS merchants (
    id BIGSERIAL PRIMARY KEY,

    merchant_code VARCHAR(50) NOT NULL,
    name VARCHAR(200) NOT NULL,

    category VARCHAR(100) NOT NULL,
    cuisine VARCHAR(100),

    rating NUMERIC(3, 2),
    average_price NUMERIC(10, 2),
    review_count INTEGER NOT NULL DEFAULT 0,

    address VARCHAR(500) NOT NULL,
    region_code VARCHAR(50),

    longitude NUMERIC(10, 6),
    latitude NUMERIC(10, 6),

    phone VARCHAR(50),
    contact_email VARCHAR(255),

    description TEXT,
    cover_image_url VARCHAR(1000),

    environment_tags JSONB NOT NULL DEFAULT '[]'::jsonb,

    platform_status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    operation_status VARCHAR(30) NOT NULL DEFAULT 'OPERATING',

    status_changed_at TIMESTAMPTZ,

    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMPTZ,

    CONSTRAINT uk_merchants_code
        UNIQUE (merchant_code),

    CONSTRAINT ck_merchants_rating
        CHECK (
            rating IS NULL
            OR rating BETWEEN 0 AND 5
        ),

    CONSTRAINT ck_merchants_average_price
        CHECK (
            average_price IS NULL
            OR average_price >= 0
        ),

    CONSTRAINT ck_merchants_review_count
        CHECK (review_count >= 0),

    CONSTRAINT ck_merchants_longitude
        CHECK (
            longitude IS NULL
            OR longitude BETWEEN -180 AND 180
        ),

    CONSTRAINT ck_merchants_latitude
        CHECK (
            latitude IS NULL
            OR latitude BETWEEN -90 AND 90
        ),

    CONSTRAINT ck_merchants_platform_status
        CHECK (
            platform_status IN (
                'ACTIVE',
                'DISABLED',
                'ARCHIVED'
            )
        ),

    CONSTRAINT ck_merchants_operation_status
        CHECK (
            operation_status IN (
                'OPERATING',
                'SUSPENDED',
                'CLOSED_PERMANENTLY'
            )
        )
);


-- =========================================================
-- 5. 商家成员关系表
-- =========================================================

CREATE TABLE IF NOT EXISTS merchant_members (
    id BIGSERIAL PRIMARY KEY,

    merchant_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,

    member_role VARCHAR(20) NOT NULL DEFAULT 'OWNER',
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',

    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_merchant_members_merchant
        FOREIGN KEY (merchant_id)
        REFERENCES merchants(id)
        ON DELETE RESTRICT,

    CONSTRAINT fk_merchant_members_user
        FOREIGN KEY (user_id)
        REFERENCES users(id)
        ON DELETE RESTRICT,

    CONSTRAINT uk_merchant_members
        UNIQUE (merchant_id, user_id),

    CONSTRAINT ck_merchant_members_role
        CHECK (
            member_role IN (
                'OWNER',
                'MANAGER',
                'STAFF'
            )
        ),

    CONSTRAINT ck_merchant_members_status
        CHECK (
            status IN (
                'ACTIVE',
                'DISABLED'
            )
        )
);


-- =========================================================
-- 6. 商家营业时间表
-- =========================================================

CREATE TABLE IF NOT EXISTS merchant_business_hours (
    id BIGSERIAL PRIMARY KEY,

    merchant_id BIGINT NOT NULL,

    day_of_week SMALLINT NOT NULL,
    open_time TIME,
    close_time TIME,

    is_closed BOOLEAN NOT NULL DEFAULT FALSE,
    crosses_midnight BOOLEAN NOT NULL DEFAULT FALSE,

    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_business_hours_merchant
        FOREIGN KEY (merchant_id)
        REFERENCES merchants(id)
        ON DELETE CASCADE,

    CONSTRAINT ck_business_hours_day
        CHECK (day_of_week BETWEEN 1 AND 7),

    CONSTRAINT ck_business_hours_value
        CHECK (
            (
                is_closed = TRUE
                AND open_time IS NULL
                AND close_time IS NULL
            )
            OR
            (
                is_closed = FALSE
                AND open_time IS NOT NULL
                AND close_time IS NOT NULL
            )
        )
);


-- =========================================================
-- 7. 当前模块索引
-- =========================================================

CREATE INDEX IF NOT EXISTS idx_users_role_status
    ON users(role, status);

CREATE UNIQUE INDEX IF NOT EXISTS uk_users_email_active
    ON users(lower(email))
    WHERE email IS NOT NULL
      AND deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_auth_sessions_user_status
    ON auth_sessions(user_id, status);

CREATE INDEX IF NOT EXISTS idx_auth_sessions_expires_at
    ON auth_sessions(expires_at);

CREATE INDEX IF NOT EXISTS idx_login_attempts_username_created
    ON login_attempts(username, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_login_attempts_ip_created
    ON login_attempts(ip_address, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_merchants_name
    ON merchants(name);

CREATE INDEX IF NOT EXISTS idx_merchants_region
    ON merchants(region_code);

CREATE INDEX IF NOT EXISTS idx_merchants_category
    ON merchants(category);

CREATE INDEX IF NOT EXISTS idx_merchants_cuisine
    ON merchants(cuisine);

CREATE INDEX IF NOT EXISTS idx_merchants_status_category_rating
    ON merchants(
        platform_status,
        operation_status,
        category,
        rating DESC
    );

CREATE INDEX IF NOT EXISTS idx_merchant_members_user
    ON merchant_members(user_id, status);

CREATE INDEX IF NOT EXISTS idx_merchant_members_merchant
    ON merchant_members(merchant_id, status);

CREATE INDEX IF NOT EXISTS idx_business_hours_merchant_day
    ON merchant_business_hours(merchant_id, day_of_week);

CREATE UNIQUE INDEX IF NOT EXISTS uk_business_hours_open_period
    ON merchant_business_hours(
        merchant_id,
        day_of_week,
        open_time
    )
    WHERE is_closed = FALSE;

CREATE UNIQUE INDEX IF NOT EXISTS uk_business_hours_closed_day
    ON merchant_business_hours(
        merchant_id,
        day_of_week
    )
    WHERE is_closed = TRUE;

COMMIT;