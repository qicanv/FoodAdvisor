BEGIN;

CREATE TABLE IF NOT EXISTS review_images (
    id BIGSERIAL PRIMARY KEY,
    review_id BIGINT NOT NULL,
    original_filename VARCHAR(255),
    storage_provider VARCHAR(30) NOT NULL DEFAULT 'LOCAL',
    storage_key VARCHAR(500) NOT NULL,
    image_url VARCHAR(1000) NOT NULL,
    thumbnail_url VARCHAR(1000),
    mime_type VARCHAR(100) NOT NULL,
    file_size BIGINT NOT NULL,
    width INTEGER,
    height INTEGER,
    content_hash VARCHAR(64),
    sort_order SMALLINT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMPTZ,

    CONSTRAINT fk_review_images_review
        FOREIGN KEY (review_id) REFERENCES reviews(id)
        ON DELETE CASCADE,

    CONSTRAINT ck_review_images_file_size
        CHECK (file_size > 0 AND file_size <= 10485760),

    CONSTRAINT ck_review_images_sort_order
        CHECK (sort_order BETWEEN 1 AND 9),

    CONSTRAINT ck_review_images_mime_type
        CHECK (mime_type IN ('image/jpeg', 'image/png', 'image/webp')),

    CONSTRAINT ck_review_images_status
        CHECK (status IN ('ACTIVE', 'DELETED'))
);

COMMIT;
