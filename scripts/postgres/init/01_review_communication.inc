-- Review replies and user notifications are first-class production tables.
CREATE TABLE review_reply (
    id BIGSERIAL PRIMARY KEY,
    review_id BIGINT NOT NULL,
    merchant_id BIGINT NOT NULL,
    reply_content TEXT NOT NULL,
    reply_time TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    status VARCHAR(20) NOT NULL DEFAULT 'VISIBLE',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_review_reply_review
        FOREIGN KEY (review_id) REFERENCES reviews(id) ON DELETE CASCADE,
    CONSTRAINT fk_review_reply_merchant
        FOREIGN KEY (merchant_id) REFERENCES merchants(id) ON DELETE RESTRICT,
    CONSTRAINT ck_review_reply_status
        CHECK (status IN ('VISIBLE', 'HIDDEN'))
);

CREATE TABLE notifications (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    review_id BIGINT NOT NULL,
    merchant_id BIGINT NOT NULL,
    type VARCHAR(20) NOT NULL DEFAULT 'REVIEW_REPLY',
    title VARCHAR(200) NOT NULL,
    review_summary TEXT,
    reply_summary TEXT,
    merchant_name VARCHAR(200),
    status VARCHAR(20) NOT NULL DEFAULT 'UNREAD',
    notified BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_notifications_user
        FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_notifications_review
        FOREIGN KEY (review_id) REFERENCES reviews(id) ON DELETE CASCADE,
    CONSTRAINT fk_notifications_merchant
        FOREIGN KEY (merchant_id) REFERENCES merchants(id) ON DELETE RESTRICT,
    CONSTRAINT ck_notifications_type
        CHECK (type IN ('REVIEW_REPLY')),
    CONSTRAINT ck_notifications_status
        CHECK (status IN ('UNREAD', 'READ'))
);
