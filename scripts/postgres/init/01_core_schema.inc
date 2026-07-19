-- ============================================
-- FoodAdvisor 閺佺増宓佹惔鎾崇紦鐞涖劏鍓奸張顒婄礄V0.3閿?
-- 濞夘煉绱皍sers / merchants / merchant_members / merchant_business_hours
--     瀹歌尙些閼?01_identity_and_merchant.sql閿涘本顒濇径鍕瑝閸愬秹鍣告径宥呯暰娑?
-- ============================================

-- ============================================
-- 5.5 閼挎粌鎼х悰?dishes
-- ============================================
CREATE TABLE IF NOT EXISTS dishes (
    id BIGSERIAL PRIMARY KEY,
    merchant_id BIGINT NOT NULL,
    name VARCHAR(200) NOT NULL,
    price NUMERIC(10, 2),
    category VARCHAR(100),
    description TEXT,
    taste_tags JSONB NOT NULL DEFAULT '[]'::jsonb,
    image_url VARCHAR(1000),
    recommended BOOLEAN NOT NULL DEFAULT FALSE,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMPTZ,

    CONSTRAINT fk_dishes_merchant
        FOREIGN KEY (merchant_id) REFERENCES merchants(id)
        ON DELETE RESTRICT,

    CONSTRAINT ck_dishes_price
        CHECK (price IS NULL OR price >= 0),

    CONSTRAINT ck_dishes_status
        CHECK (status IN ('ACTIVE', 'OFF_SHELF', 'ARCHIVED'))
);

-- ============================================
-- 5.6 鐠囧嫯顔戠悰?reviews閿涘湸0.3 閺囧瓨鏌婇敍?
-- ============================================
CREATE TABLE IF NOT EXISTS reviews (
    id BIGSERIAL PRIMARY KEY,
    merchant_id BIGINT NOT NULL,
    user_id BIGINT,
    import_task_id BIGINT,
    review_type VARCHAR(20) NOT NULL DEFAULT 'ORIGINAL',
    parent_review_id BIGINT,
    rating SMALLINT,
    taste_rating SMALLINT,
    environment_rating SMALLINT,
    service_rating SMALLINT,
    average_spend NUMERIC(10, 2),
    consumption_date DATE,
    content TEXT NOT NULL,
    source VARCHAR(30) NOT NULL DEFAULT 'SYSTEM',
    review_time TIMESTAMPTZ,
    external_id VARCHAR(200),
    source_user_key VARCHAR(200),
    idempotency_key VARCHAR(100),
    current_version INTEGER NOT NULL DEFAULT 1,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    moderation_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    risk_level VARCHAR(20),
    published_at TIMESTAMPTZ,
    edited_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMPTZ,

    CONSTRAINT fk_reviews_merchant
        FOREIGN KEY (merchant_id) REFERENCES merchants(id)
        ON DELETE RESTRICT,

    CONSTRAINT fk_reviews_user
        FOREIGN KEY (user_id) REFERENCES users(id)
        ON DELETE SET NULL,

    CONSTRAINT fk_reviews_parent
        FOREIGN KEY (parent_review_id) REFERENCES reviews(id)
        ON DELETE SET NULL,

    CONSTRAINT ck_reviews_review_type
        CHECK (review_type IN ('ORIGINAL', 'FOLLOW_UP')),

    CONSTRAINT ck_reviews_parent_type
        CHECK (
            (review_type = 'ORIGINAL' AND parent_review_id IS NULL)
            OR
            (review_type = 'FOLLOW_UP' AND parent_review_id IS NOT NULL)
        ),

    CONSTRAINT ck_reviews_rating
        CHECK (rating IS NULL OR rating BETWEEN 1 AND 5),

    CONSTRAINT ck_reviews_taste_rating
        CHECK (taste_rating IS NULL OR taste_rating BETWEEN 1 AND 5),

    CONSTRAINT ck_reviews_environment_rating
        CHECK (environment_rating IS NULL OR environment_rating BETWEEN 1 AND 5),

    CONSTRAINT ck_reviews_service_rating
        CHECK (service_rating IS NULL OR service_rating BETWEEN 1 AND 5),

    CONSTRAINT ck_reviews_average_spend
        CHECK (average_spend IS NULL OR average_spend >= 0),

    CONSTRAINT ck_reviews_content_length
        CHECK (char_length(btrim(content)) BETWEEN 10 AND 2000),

    CONSTRAINT ck_reviews_current_version
        CHECK (current_version >= 1),

    CONSTRAINT ck_reviews_status
        CHECK (status IN ('PENDING', 'PUBLISHED', 'HIDDEN', 'DELETED')),

    CONSTRAINT ck_reviews_moderation_status
        CHECK (moderation_status IN ('PENDING', 'APPROVED', 'REJECTED')),

    CONSTRAINT ck_reviews_risk_level
        CHECK (risk_level IS NULL OR risk_level IN ('LOW', 'MEDIUM', 'HIGH'))
);

-- 閸烆垯绔寸槐銏犵穿閿涙碍鐦￠悽銊﹀煕濮ｅ繐鏅㈢€硅泛褰ч張澶夌閺夆剝婀侀弫鍫濆斧鐠囧嫪鐜?
-- 閸烆垯绔寸槐銏犵穿閿涙碍鐦￠弶鈥冲斧鐠囧嫪鐜張鈧径姘閺夆剝婀侀弫鍫ｆ嫹鐠?
-- 閸烆垯绔寸槐銏犵穿閿涙艾绠撶粵澶愭暛
-- ============================================
-- 5.7 鐠囧嫯顔戦崚鍡樼€界悰?review_analysis閿涘湸0.3 閺囧瓨鏌?閳?閺€顖涘瘮閻楀牊婀伴崠鏍у瀻閺嬫劧绱?
-- ============================================
CREATE TABLE IF NOT EXISTS review_analysis (
    id BIGSERIAL PRIMARY KEY,
    review_id BIGINT NOT NULL,
    review_version INTEGER NOT NULL DEFAULT 1,
    analysis_version INTEGER NOT NULL DEFAULT 1,
    sentiment VARCHAR(20) NOT NULL,
    confidence NUMERIC(5, 4),
    low_confidence BOOLEAN NOT NULL DEFAULT FALSE,
    keywords JSONB NOT NULL DEFAULT '[]'::jsonb,
    aspects JSONB NOT NULL DEFAULT '[]'::jsonb,
    negative_reason VARCHAR(100),
    model_name VARCHAR(100),
    model_version VARCHAR(100),
    business_trace_id VARCHAR(100),
    status VARCHAR(20) NOT NULL DEFAULT 'SUCCESS',
    error_message TEXT,
    started_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uk_review_analysis_version
        UNIQUE (review_id, review_version, analysis_version),

    CONSTRAINT fk_review_analysis_review
        FOREIGN KEY (review_id) REFERENCES reviews(id)
        ON DELETE CASCADE,

    CONSTRAINT ck_review_analysis_sentiment
        CHECK (sentiment IN ('POSITIVE', 'NEUTRAL', 'NEGATIVE', 'MIXED')),

    CONSTRAINT ck_review_analysis_confidence
        CHECK (confidence IS NULL OR confidence BETWEEN 0 AND 1),

    CONSTRAINT ck_review_analysis_status
        CHECK (status IN ('PENDING', 'SUCCESS', 'FAILED')),

    CONSTRAINT ck_review_analysis_complete
        CHECK (status <> 'SUCCESS' OR sentiment IS NOT NULL)
);

-- ============================================
-- 5.8 鐠囧嫯顔戦弽鍥╊劮鐎涙鍚€鐞?review_tags
-- ============================================
CREATE TABLE IF NOT EXISTS review_tags (
    id BIGSERIAL PRIMARY KEY,
    code VARCHAR(50) NOT NULL,
    name VARCHAR(100) NOT NULL,
    category VARCHAR(50) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uk_review_tags_code UNIQUE (code),

    CONSTRAINT ck_review_tags_status
        CHECK (status IN ('ACTIVE', 'DISABLED'))
);

-- ============================================
-- 5.9 鐠囧嫯顔戦弽鍥╊劮閸忓疇浠堢悰?review_tag_relations閿涘湸0.3 閺囧瓨鏌婇敍?
-- ============================================
CREATE TABLE IF NOT EXISTS review_tag_relations (
    id BIGSERIAL PRIMARY KEY,
    review_id BIGINT NOT NULL,
    review_version INTEGER NOT NULL DEFAULT 1,
    tag_id BIGINT NOT NULL,
    sentiment VARCHAR(20) NOT NULL,
    confidence NUMERIC(5, 4),
    evidence_text TEXT,
    model_name VARCHAR(100),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_review_tag_relation_review
        FOREIGN KEY (review_id) REFERENCES reviews(id)
        ON DELETE CASCADE,

    CONSTRAINT fk_review_tag_relation_tag
        FOREIGN KEY (tag_id) REFERENCES review_tags(id)
        ON DELETE RESTRICT,

    CONSTRAINT uk_review_tag_relation
        UNIQUE (review_id, review_version, tag_id),

    CONSTRAINT ck_review_tag_sentiment
        CHECK (sentiment IN ('POSITIVE', 'NEUTRAL', 'NEGATIVE')),

    CONSTRAINT ck_review_tag_confidence
        CHECK (confidence IS NULL OR confidence BETWEEN 0 AND 1)
);

-- ============================================
-- 5.10 鐎电鐦芥导姘崇樈鐞?chat_sessions
-- ============================================
CREATE TABLE IF NOT EXISTS chat_sessions (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    title VARCHAR(200),
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    closed_at TIMESTAMPTZ,

    CONSTRAINT fk_chat_sessions_user
        FOREIGN KEY (user_id) REFERENCES users(id)
        ON DELETE RESTRICT,

    CONSTRAINT ck_chat_sessions_status
        CHECK (status IN ('ACTIVE', 'CLOSED', 'ARCHIVED'))
);

-- ============================================
-- 5.11 鐎电鐦藉☉鍫熶紖鐞?chat_messages
-- ============================================
CREATE TABLE IF NOT EXISTS chat_messages (
    id BIGSERIAL PRIMARY KEY,
    session_id BIGINT NOT NULL,
    role VARCHAR(20) NOT NULL,
    content TEXT NOT NULL,
    message_type VARCHAR(30) NOT NULL DEFAULT 'TEXT',
    request_id VARCHAR(100),
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_chat_messages_session
        FOREIGN KEY (session_id) REFERENCES chat_sessions(id)
        ON DELETE CASCADE,

    CONSTRAINT ck_chat_messages_role
        CHECK (role IN ('USER', 'ASSISTANT', 'SYSTEM')),

    CONSTRAINT ck_chat_messages_type
        CHECK (message_type IN (
            'TEXT', 'QUESTION', 'RECOMMENDATION', 'ERROR', 'SYSTEM_NOTICE'
        ))
);

-- ============================================
-- 5.12 娴兼俺鐦介悩鑸碘偓浣姐€?chat_session_states
-- ============================================
CREATE TABLE IF NOT EXISTS chat_session_states (
    id BIGSERIAL PRIMARY KEY,
    session_id BIGINT NOT NULL,
    current_constraints JSONB NOT NULL DEFAULT '{}'::jsonb,
    missing_fields JSONB NOT NULL DEFAULT '[]'::jsonb,
    rejected_fields JSONB NOT NULL DEFAULT '[]'::jsonb,
    pending_confirmation JSONB,
    conversation_stage VARCHAR(30) NOT NULL DEFAULT 'COLLECTING',
    version INTEGER NOT NULL DEFAULT 1,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_chat_session_states_session
        FOREIGN KEY (session_id) REFERENCES chat_sessions(id)
        ON DELETE CASCADE,

    CONSTRAINT uk_chat_session_states_session UNIQUE (session_id),

    CONSTRAINT ck_chat_session_stage
        CHECK (conversation_stage IN (
            'COLLECTING', 'CONFIRMING', 'SEARCHING', 'RECOMMENDED', 'COMPLETED'
        )),

    CONSTRAINT ck_chat_session_version
        CHECK (version >= 1)
);

-- ============================================
-- 5.13 閺夆€叉閹绘劕褰囬崢鍡楀蕉鐞?constraint_extractions
-- ============================================
CREATE TABLE IF NOT EXISTS constraint_extractions (
    id BIGSERIAL PRIMARY KEY,
    session_id BIGINT NOT NULL,
    message_id BIGINT NOT NULL,
    extracted_constraints JSONB NOT NULL DEFAULT '{}'::jsonb,
    merged_constraints JSONB NOT NULL DEFAULT '{}'::jsonb,
    changed_fields JSONB NOT NULL DEFAULT '[]'::jsonb,
    conflict_fields JSONB NOT NULL DEFAULT '[]'::jsonb,
    model_name VARCHAR(100),
    model_version VARCHAR(100),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_constraint_extractions_session
        FOREIGN KEY (session_id) REFERENCES chat_sessions(id)
        ON DELETE CASCADE,

    CONSTRAINT fk_constraint_extractions_message
        FOREIGN KEY (message_id) REFERENCES chat_messages(id)
        ON DELETE CASCADE,

    CONSTRAINT uk_constraint_extraction_message UNIQUE (message_id)
);

-- ============================================
-- 5.14 閹恒劏宕樼拋鏉跨秿鐞?recommendations
-- ============================================
CREATE TABLE IF NOT EXISTS recommendations (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    session_id BIGINT NOT NULL,
    user_message_id BIGINT,
    assistant_message_id BIGINT,
    request_id VARCHAR(100),
    trace_id VARCHAR(100),
    query_text TEXT NOT NULL,
    parsed_constraints JSONB NOT NULL DEFAULT '{}'::jsonb,
    reply_text TEXT,
    algorithm_version VARCHAR(50),
    weight_snapshot JSONB,
    model_name VARCHAR(100),
    model_version VARCHAR(100),
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    result_count INTEGER NOT NULL DEFAULT 0,
    error_code VARCHAR(100),
    error_message TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMPTZ,

    CONSTRAINT fk_recommendations_user
        FOREIGN KEY (user_id) REFERENCES users(id)
        ON DELETE RESTRICT,

    CONSTRAINT fk_recommendations_session
        FOREIGN KEY (session_id) REFERENCES chat_sessions(id)
        ON DELETE CASCADE,

    CONSTRAINT fk_recommendations_user_message
        FOREIGN KEY (user_message_id) REFERENCES chat_messages(id)
        ON DELETE SET NULL,

    CONSTRAINT fk_recommendations_assistant_message
        FOREIGN KEY (assistant_message_id) REFERENCES chat_messages(id)
        ON DELETE SET NULL,

    CONSTRAINT ck_recommendations_status
        CHECK (status IN ('PENDING', 'SUCCESS', 'NO_MATCH', 'FAILED')),

    CONSTRAINT ck_recommendations_result_count
        CHECK (result_count >= 0)
);

-- ============================================
-- 5.15 閹恒劏宕橀崯鍡楊啀閺勫海绮忕悰?recommendation_items
-- ============================================
CREATE TABLE IF NOT EXISTS recommendation_items (
    id BIGSERIAL PRIMARY KEY,
    recommendation_id BIGINT NOT NULL,
    merchant_id BIGINT NOT NULL,
    rank_no INTEGER NOT NULL,
    score NUMERIC(8, 6),
    score_details JSONB NOT NULL DEFAULT '{}'::jsonb,
    matched_conditions JSONB NOT NULL DEFAULT '[]'::jsonb,
    unmatched_conditions JSONB NOT NULL DEFAULT '[]'::jsonb,
    reason TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_recommendation_items_recommendation
        FOREIGN KEY (recommendation_id) REFERENCES recommendations(id)
        ON DELETE CASCADE,

    CONSTRAINT fk_recommendation_items_merchant
        FOREIGN KEY (merchant_id) REFERENCES merchants(id)
        ON DELETE RESTRICT,

    CONSTRAINT uk_recommendation_merchant
        UNIQUE (recommendation_id, merchant_id),

    CONSTRAINT uk_recommendation_rank
        UNIQUE (recommendation_id, rank_no),

    CONSTRAINT ck_recommendation_rank
        CHECK (rank_no > 0),

    CONSTRAINT ck_recommendation_score
        CHECK (score IS NULL OR score BETWEEN 0 AND 1)
);

-- ============================================
-- 5.16 閹恒劏宕樻笟婵囧祦鐞?recommendation_evidences
-- ============================================
CREATE TABLE IF NOT EXISTS recommendation_evidences (
    id BIGSERIAL PRIMARY KEY,
    recommendation_item_id BIGINT NOT NULL,
    source_type VARCHAR(30) NOT NULL,
    source_merchant_id BIGINT NOT NULL,
    review_id BIGINT,
    dish_id BIGINT,
    knowledge_document_id VARCHAR(200),
    evidence_excerpt TEXT,
    source_text_snapshot TEXT,
    condition_key VARCHAR(100),
    relevance_score NUMERIC(8, 6),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_evidence_item
        FOREIGN KEY (recommendation_item_id)
        REFERENCES recommendation_items(id)
        ON DELETE CASCADE,

    CONSTRAINT fk_evidence_merchant
        FOREIGN KEY (source_merchant_id) REFERENCES merchants(id)
        ON DELETE RESTRICT,

    CONSTRAINT fk_evidence_review
        FOREIGN KEY (review_id) REFERENCES reviews(id)
        ON DELETE SET NULL,

    CONSTRAINT fk_evidence_dish
        FOREIGN KEY (dish_id) REFERENCES dishes(id)
        ON DELETE SET NULL,

    CONSTRAINT ck_evidence_source_type
        CHECK (source_type IN ('MERCHANT', 'REVIEW', 'DISH', 'KNOWLEDGE_CHUNK')),

    CONSTRAINT ck_evidence_relevance_score
        CHECK (relevance_score IS NULL OR relevance_score BETWEEN 0 AND 1)
);

-- ============================================
-- 5.17 閹恒劏宕橀崣宥夘洯鐞?recommendation_feedback
-- ============================================
CREATE TABLE IF NOT EXISTS recommendation_feedback (
    id BIGSERIAL PRIMARY KEY,
    recommendation_id BIGINT NOT NULL,
    feedback_type VARCHAR(20) NOT NULL,
    content TEXT,
    reason_category VARCHAR(100),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_recommendation_feedback_recommendation
        FOREIGN KEY (recommendation_id) REFERENCES recommendations(id)
        ON DELETE CASCADE,

    CONSTRAINT uk_feedback_recommendation UNIQUE (recommendation_id),

    CONSTRAINT ck_feedback_type
        CHECK (feedback_type IN ('SATISFIED', 'DISSATISFIED'))
);

-- ============================================
-- 5.18 閸熷棗顔嶇拠鍕啈閹芥顩︾悰?merchant_review_summaries
-- ============================================
CREATE TABLE IF NOT EXISTS merchant_review_summaries (
    id BIGSERIAL PRIMARY KEY,
    merchant_id BIGINT NOT NULL,
    version INTEGER NOT NULL,
    summary_text TEXT,
    advantages JSONB NOT NULL DEFAULT '[]'::jsonb,
    disadvantages JSONB NOT NULL DEFAULT '[]'::jsonb,
    recommended_dishes JSONB NOT NULL DEFAULT '[]'::jsonb,
    environment_summary JSONB NOT NULL DEFAULT '{}'::jsonb,
    service_summary JSONB NOT NULL DEFAULT '{}'::jsonb,
    recent_changes JSONB NOT NULL DEFAULT '[]'::jsonb,
    review_count INTEGER NOT NULL DEFAULT 0,
    source_start_time TIMESTAMPTZ,
    source_end_time TIMESTAMPTZ,
    status VARCHAR(20) NOT NULL DEFAULT 'SUCCESS',
    model_name VARCHAR(100),
    model_version VARCHAR(100),
    error_message TEXT,
    generated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_review_summary_merchant
        FOREIGN KEY (merchant_id) REFERENCES merchants(id)
        ON DELETE RESTRICT,

    CONSTRAINT uk_review_summary_version
        UNIQUE (merchant_id, version),

    CONSTRAINT ck_review_summary_version
        CHECK (version >= 1),

    CONSTRAINT ck_review_summary_count
        CHECK (review_count >= 0),

    CONSTRAINT ck_review_summary_status
        CHECK (status IN ('SUCCESS', 'INSUFFICIENT_DATA', 'FAILED'))
);

-- ============================================
-- 5.19 鐠囧嫯顔戦幗妯款洣娓氭繃宓佺悰?merchant_summary_evidences
-- ============================================
CREATE TABLE IF NOT EXISTS merchant_summary_evidences (
    id BIGSERIAL PRIMARY KEY,
    summary_id BIGINT NOT NULL,
    review_id BIGINT NOT NULL,
    source_type VARCHAR(30) NOT NULL DEFAULT 'REVIEW',
    source_merchant_id BIGINT NOT NULL,
    review_version INTEGER,
    evidence_type VARCHAR(50) NOT NULL,
    evidence_excerpt TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_summary_evidence_summary
        FOREIGN KEY (summary_id) REFERENCES merchant_review_summaries(id)
        ON DELETE CASCADE,

    CONSTRAINT fk_summary_evidence_review
        FOREIGN KEY (review_id) REFERENCES reviews(id)
        ON DELETE RESTRICT,

    CONSTRAINT fk_summary_evidence_merchant
        FOREIGN KEY (source_merchant_id) REFERENCES merchants(id)
        ON DELETE RESTRICT,

    CONSTRAINT ck_summary_evidence_source_type
        CHECK (source_type IN ('REVIEW')),

    CONSTRAINT ck_summary_evidence_type
        CHECK (evidence_type IN (
            'REVIEW',
            'ADVANTAGE',
            'DISADVANTAGE',
            'DISH',
            'ENVIRONMENT',
            'SERVICE',
            'RECENT_CHANGE'
        )),

    CONSTRAINT ck_summary_evidence_review_version
        CHECK (review_version IS NULL OR review_version >= 1),

    CONSTRAINT uk_summary_evidence
        UNIQUE (summary_id, review_id, evidence_type)
);

-- ============================================
-- 5.20 鐎电厧鍙嗘禒璇插鐞?import_tasks
-- ============================================
CREATE TABLE IF NOT EXISTS import_tasks (
    id BIGSERIAL PRIMARY KEY,
    task_type VARCHAR(30) NOT NULL,
    original_filename VARCHAR(255) NOT NULL,
    file_hash VARCHAR(64),
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    total_count INTEGER NOT NULL DEFAULT 0,
    success_count INTEGER NOT NULL DEFAULT 0,
    failure_count INTEGER NOT NULL DEFAULT 0,
    created_by BIGINT NOT NULL,
    started_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_import_tasks_user
        FOREIGN KEY (created_by) REFERENCES users(id)
        ON DELETE RESTRICT,

    CONSTRAINT ck_import_task_type
        CHECK (task_type IN ('MERCHANT', 'DISH', 'REVIEW')),

    CONSTRAINT ck_import_task_status
        CHECK (status IN (
            'PENDING', 'PROCESSING', 'PARTIAL_SUCCESS', 'SUCCESS', 'FAILED'
        )),

    CONSTRAINT ck_import_task_counts
        CHECK (
            total_count >= 0
            AND success_count >= 0
            AND failure_count >= 0
        )
);

-- ============================================
-- 5.21 鐎电厧鍙嗘禒璇插閺勫海绮忕悰?import_task_items
-- ============================================
CREATE TABLE IF NOT EXISTS import_task_items (
    id BIGSERIAL PRIMARY KEY,
    task_id BIGINT NOT NULL,
    row_no INTEGER NOT NULL,
    external_key VARCHAR(200),
    raw_data JSONB NOT NULL DEFAULT '{}'::jsonb,
    status VARCHAR(20) NOT NULL,
    target_id BIGINT,
    error_code VARCHAR(100),
    error_message TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_import_items_task
        FOREIGN KEY (task_id) REFERENCES import_tasks(id)
        ON DELETE CASCADE,

    CONSTRAINT uk_import_task_row
        UNIQUE (task_id, row_no),

    CONSTRAINT ck_import_item_row
        CHECK (row_no > 0),

    CONSTRAINT ck_import_item_status
        CHECK (status IN ('SUCCESS', 'FAILED', 'DUPLICATE'))
);

-- ============================================
-- 5.22 AI 鐠嬪啰鏁ら弮銉ョ箶鐞?ai_call_logs
-- ============================================
CREATE TABLE IF NOT EXISTS ai_call_logs (
    id BIGSERIAL PRIMARY KEY,
    trace_id VARCHAR(100) NOT NULL,
    user_id BIGINT,
    session_id BIGINT,
    function_type VARCHAR(50) NOT NULL,
    provider VARCHAR(50),
    model_name VARCHAR(100),
    model_version VARCHAR(100),
    status VARCHAR(20) NOT NULL,
    latency_ms INTEGER,
    input_tokens INTEGER,
    output_tokens INTEGER,
    total_tokens INTEGER,
    estimated_cost NUMERIC(12, 6),
    error_type VARCHAR(100),
    error_message TEXT,
    request_summary JSONB NOT NULL DEFAULT '{}'::jsonb,
    response_summary JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uk_ai_call_trace UNIQUE (trace_id),

    CONSTRAINT fk_ai_call_logs_user
        FOREIGN KEY (user_id) REFERENCES users(id)
        ON DELETE SET NULL,

    CONSTRAINT fk_ai_call_logs_session
        FOREIGN KEY (session_id) REFERENCES chat_sessions(id)
        ON DELETE SET NULL,

    CONSTRAINT ck_ai_call_status
        CHECK (status IN ('SUCCESS', 'FAILED', 'TIMEOUT')),

    CONSTRAINT ck_ai_call_latency
        CHECK (latency_ms IS NULL OR latency_ms >= 0),

    CONSTRAINT ck_ai_call_tokens
        CHECK (
            (input_tokens IS NULL OR input_tokens >= 0)
            AND (output_tokens IS NULL OR output_tokens >= 0)
            AND (total_tokens IS NULL OR total_tokens >= 0)
        ),

    CONSTRAINT ck_ai_call_cost
        CHECK (estimated_cost IS NULL OR estimated_cost >= 0)
);

-- ============================================
-- 5.23 鐠囧嫪鐜悧鍫熸拱閸樺棗褰剁悰?review_versions閿涘湸0.3 閺傛澘顤冮敍?
-- ============================================
CREATE TABLE IF NOT EXISTS review_versions (
    id BIGSERIAL PRIMARY KEY,
    review_id BIGINT NOT NULL,
    version INTEGER NOT NULL,
    rating SMALLINT,
    taste_rating SMALLINT,
    environment_rating SMALLINT,
    service_rating SMALLINT,
    average_spend NUMERIC(10, 2),
    consumption_date DATE,
    content TEXT,
    image_snapshot JSONB,
    status_snapshot VARCHAR(20),
    moderation_status_snapshot VARCHAR(20),
    changed_by BIGINT,
    change_type VARCHAR(20) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_review_versions_review
        FOREIGN KEY (review_id) REFERENCES reviews(id)
        ON DELETE CASCADE,

    CONSTRAINT ck_review_versions_version
        CHECK (version >= 1),

    CONSTRAINT ck_review_versions_change_type
        CHECK (change_type IN ('CREATE', 'EDIT', 'FOLLOW_UP', 'MODERATE', 'RESTORE')),

    CONSTRAINT uk_review_versions
        UNIQUE (review_id, version)
);

-- ============================================
-- 5.24 瀹割喛鐦庤ぐ鎺戞礈缁鍩嗙悰?review_issue_categories閿涘湸0.3 閺傛澘顤冮敍?
-- ============================================
CREATE TABLE IF NOT EXISTS review_issue_categories (
    id BIGSERIAL PRIMARY KEY,
    code VARCHAR(50) NOT NULL,
    name VARCHAR(100) NOT NULL,
    description TEXT,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uk_issue_categories_code UNIQUE (code),

    CONSTRAINT ck_issue_categories_status
        CHECK (status IN ('ACTIVE', 'DISABLED'))
);

-- 閸愬懐鐤嗚ぐ鎺戞礈缁鍩嗙粔宥呯摍閺佺増宓?
-- ============================================
-- 5.25 瀹割喛鐦庤ぐ鎺戞礈閸忓疇浠堢悰?review_issue_relations閿涘湸0.3 閺傛澘顤冮敍?
-- ============================================
CREATE TABLE IF NOT EXISTS review_issue_relations (
    id BIGSERIAL PRIMARY KEY,
    review_id BIGINT NOT NULL,
    review_version INTEGER NOT NULL DEFAULT 1,
    issue_category_id BIGINT NOT NULL,
    confidence NUMERIC(5, 4),
    evidence_text TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_issue_relations_review
        FOREIGN KEY (review_id) REFERENCES reviews(id)
        ON DELETE CASCADE,

    CONSTRAINT fk_issue_relations_category
        FOREIGN KEY (issue_category_id) REFERENCES review_issue_categories(id)
        ON DELETE RESTRICT,

    CONSTRAINT uk_issue_relations
        UNIQUE (review_id, review_version, issue_category_id),

    CONSTRAINT ck_issue_relations_confidence
        CHECK (confidence IS NULL OR confidence BETWEEN 0 AND 1)
);

-- ============================================
-- 5.26 閸熷棗顔嶆禍顔惧仯鐞?merchant_highlights閿涘湸0.3 閺傛澘顤冮敍?
-- ============================================
CREATE TABLE IF NOT EXISTS merchant_highlights (
    id BIGSERIAL PRIMARY KEY,
    merchant_id BIGINT NOT NULL,
    highlight_type VARCHAR(50) NOT NULL,
    title VARCHAR(200) NOT NULL,
    description TEXT,
    mention_count INTEGER NOT NULL DEFAULT 1,
    positive_ratio NUMERIC(5, 4),
    version INTEGER NOT NULL DEFAULT 1,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    generated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_highlights_merchant
        FOREIGN KEY (merchant_id) REFERENCES merchants(id)
        ON DELETE CASCADE,

    CONSTRAINT ck_highlights_mention_count
        CHECK (mention_count >= 1),

    CONSTRAINT ck_highlights_positive_ratio
        CHECK (positive_ratio IS NULL OR positive_ratio BETWEEN 0 AND 1),

    CONSTRAINT ck_highlights_status
        CHECK (status IN ('ACTIVE', 'OUTDATED', 'DISABLED'))
);

-- ============================================
-- 5.27 閸熷棗顔嶆禍顔惧仯娓氭繃宓佺悰?merchant_highlight_evidences閿涘湸0.3 閺傛澘顤冮敍?
-- ============================================
CREATE TABLE IF NOT EXISTS merchant_highlight_evidences (
    id BIGSERIAL PRIMARY KEY,
    highlight_id BIGINT NOT NULL,
    review_id BIGINT NOT NULL,
    review_version INTEGER NOT NULL DEFAULT 1,
    evidence_excerpt TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_highlight_evidences_highlight
        FOREIGN KEY (highlight_id) REFERENCES merchant_highlights(id)
        ON DELETE CASCADE,

    CONSTRAINT fk_highlight_evidences_review
        FOREIGN KEY (review_id) REFERENCES reviews(id)
        ON DELETE RESTRICT,

    CONSTRAINT uk_highlight_evidences
        UNIQUE (highlight_id, review_id)
);

-- ============================================
-- 5.28 閸熷棗顔嶉崣锝囶暥缂佺喕顓哥悰?merchant_reputation_statistics閿涘湸0.3 閺傛澘顤冮敍?
-- ============================================
CREATE TABLE IF NOT EXISTS merchant_reputation_statistics (
    id BIGSERIAL PRIMARY KEY,
    merchant_id BIGINT NOT NULL,
    period_type VARCHAR(10) NOT NULL,
    period_start DATE NOT NULL,
    period_end DATE NOT NULL,
    average_rating NUMERIC(5, 2),
    positive_count INTEGER NOT NULL DEFAULT 0,
    neutral_count INTEGER NOT NULL DEFAULT 0,
    negative_count INTEGER NOT NULL DEFAULT 0,
    total_review_count INTEGER NOT NULL DEFAULT 0,
    positive_ratio NUMERIC(5, 4),
    negative_ratio NUMERIC(5, 4),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_reputation_stats_merchant
        FOREIGN KEY (merchant_id) REFERENCES merchants(id)
        ON DELETE CASCADE,

    CONSTRAINT ck_reputation_period_type
        CHECK (period_type IN ('DAY', 'WEEK', 'MONTH')),

    CONSTRAINT ck_reputation_counts
        CHECK (
            positive_count >= 0
            AND neutral_count >= 0
            AND negative_count >= 0
            AND total_review_count >= 0
        ),

    CONSTRAINT ck_reputation_ratios
        CHECK (
            (positive_ratio IS NULL OR positive_ratio BETWEEN 0 AND 1)
            AND (negative_ratio IS NULL OR negative_ratio BETWEEN 0 AND 1)
        ),

    CONSTRAINT uk_reputation_stats
        UNIQUE (merchant_id, period_type, period_start, period_end)
);
