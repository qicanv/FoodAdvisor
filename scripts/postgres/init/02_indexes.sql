-- ============================================
-- FoodAdvisor 绱㈠紩鍒涘缓鑴氭湰 V0.3
-- 澶嶅悎绱㈠紩 + 閮ㄥ垎鍞竴绱㈠紩
-- ============================================

-- === users ===
CREATE INDEX IF NOT EXISTS idx_users_role_status
    ON users(role, status);

-- === merchants ===
CREATE INDEX IF NOT EXISTS idx_merchants_name
    ON merchants(name);

CREATE INDEX IF NOT EXISTS idx_merchants_category
    ON merchants(category);

CREATE INDEX IF NOT EXISTS idx_merchants_cuisine
    ON merchants(cuisine);

CREATE INDEX IF NOT EXISTS idx_merchants_region
    ON merchants(region_code);

CREATE INDEX IF NOT EXISTS idx_merchants_status_category_rating
    ON merchants(platform_status, operation_status, category, rating DESC);

CREATE INDEX IF NOT EXISTS idx_merchants_average_price
    ON merchants(average_price);

-- === merchant_members ===
CREATE INDEX IF NOT EXISTS idx_merchant_members_user
    ON merchant_members(user_id, status);

CREATE INDEX IF NOT EXISTS idx_merchant_members_merchant
    ON merchant_members(merchant_id, status);

-- === merchant_business_hours ===
CREATE INDEX IF NOT EXISTS idx_business_hours_merchant_day
    ON merchant_business_hours(merchant_id, day_of_week);

-- === dishes ===
CREATE INDEX IF NOT EXISTS idx_dishes_merchant_status
    ON dishes(merchant_id, status);

CREATE INDEX IF NOT EXISTS idx_dishes_name
    ON dishes(name);

CREATE UNIQUE INDEX IF NOT EXISTS uk_dishes_merchant_name_active
    ON dishes(merchant_id, name)
    WHERE status <> 'ARCHIVED';

-- === reviews (V0.3 鏇存柊) ===
CREATE INDEX IF NOT EXISTS idx_reviews_merchant_created
    ON reviews(merchant_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_reviews_user
    ON reviews(user_id);

CREATE INDEX IF NOT EXISTS idx_reviews_rating
    ON reviews(rating);

CREATE INDEX IF NOT EXISTS idx_reviews_status
    ON reviews(status, moderation_status);

CREATE INDEX IF NOT EXISTS idx_reviews_review_type
    ON reviews(review_type, status);

-- === review_analysis (V0.3 鏇存柊) ===
CREATE INDEX IF NOT EXISTS idx_review_analysis_review_version
    ON review_analysis(review_id, review_version DESC, analysis_version DESC);

CREATE INDEX IF NOT EXISTS idx_review_analysis_sentiment
    ON review_analysis(sentiment);

CREATE INDEX IF NOT EXISTS idx_review_analysis_created_at
    ON review_analysis(created_at DESC);

-- === review_versions (V0.3 鏂板) ===
CREATE INDEX IF NOT EXISTS idx_review_versions_review
    ON review_versions(review_id, version DESC);

-- === review_issue_relations (V0.3 鏂板) ===
CREATE INDEX IF NOT EXISTS idx_issue_relations_review
    ON review_issue_relations(review_id, review_version);

CREATE INDEX IF NOT EXISTS idx_issue_relations_category
    ON review_issue_relations(issue_category_id);

-- === merchant_highlights (V0.3 鏂板) ===
CREATE INDEX IF NOT EXISTS idx_highlights_merchant_status
    ON merchant_highlights(merchant_id, status, mention_count DESC);

-- === merchant_highlight_evidences (V0.3 鏂板) ===
CREATE INDEX IF NOT EXISTS idx_highlight_evidences_highlight
    ON merchant_highlight_evidences(highlight_id);

-- === merchant_reputation_statistics (V0.3 鏂板) ===
CREATE INDEX IF NOT EXISTS idx_reputation_stats_merchant_period
    ON merchant_reputation_statistics(merchant_id, period_type, period_start DESC);

-- === review_tag_relations ===
CREATE INDEX IF NOT EXISTS idx_review_tag_relations_tag_sentiment
    ON review_tag_relations(tag_id, sentiment);

CREATE INDEX IF NOT EXISTS idx_review_tag_relations_review
    ON review_tag_relations(review_id);

-- === chat_sessions ===
CREATE INDEX IF NOT EXISTS idx_chat_sessions_user_created
    ON chat_sessions(user_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_chat_sessions_status
    ON chat_sessions(status);

-- === chat_messages ===
CREATE INDEX IF NOT EXISTS idx_chat_messages_session_created
    ON chat_messages(session_id, created_at, id);

CREATE UNIQUE INDEX IF NOT EXISTS uk_chat_messages_session_request
    ON chat_messages(session_id, request_id, role)
    WHERE request_id IS NOT NULL;

-- === constraint_extractions ===
CREATE INDEX IF NOT EXISTS idx_constraint_extractions_session_created
    ON constraint_extractions(session_id, created_at);

-- === recommendations ===
CREATE INDEX IF NOT EXISTS idx_recommendations_user_created
    ON recommendations(user_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_recommendations_session_created
    ON recommendations(session_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_recommendations_status
    ON recommendations(status);

CREATE UNIQUE INDEX IF NOT EXISTS uk_recommendations_request
    ON recommendations(request_id)
    WHERE request_id IS NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uk_recommendations_trace
    ON recommendations(trace_id)
    WHERE trace_id IS NOT NULL;

-- === recommendation_items ===
CREATE INDEX IF NOT EXISTS idx_recommendation_items_recommendation
    ON recommendation_items(recommendation_id, rank_no);

CREATE INDEX IF NOT EXISTS idx_recommendation_items_merchant
    ON recommendation_items(merchant_id);

-- === recommendation_evidences ===
CREATE INDEX IF NOT EXISTS idx_recommendation_evidences_item
    ON recommendation_evidences(recommendation_item_id);

CREATE INDEX IF NOT EXISTS idx_recommendation_evidences_review
    ON recommendation_evidences(review_id);

CREATE INDEX IF NOT EXISTS idx_recommendation_evidences_merchant
    ON recommendation_evidences(source_merchant_id);

CREATE INDEX IF NOT EXISTS idx_recommendation_evidences_condition
    ON recommendation_evidences(condition_key);

-- === recommendation_feedback ===
CREATE INDEX IF NOT EXISTS idx_feedback_type_created
    ON recommendation_feedback(feedback_type, created_at DESC);

-- === merchant_review_summaries ===
CREATE INDEX IF NOT EXISTS idx_review_summaries_merchant_version
    ON merchant_review_summaries(merchant_id, version DESC);

CREATE INDEX IF NOT EXISTS idx_review_summaries_generated_at
    ON merchant_review_summaries(generated_at DESC);

-- === merchant_summary_evidences ===
CREATE INDEX IF NOT EXISTS idx_summary_evidences_summary
    ON merchant_summary_evidences(summary_id);

CREATE INDEX IF NOT EXISTS idx_summary_evidences_review
    ON merchant_summary_evidences(review_id);

CREATE INDEX IF NOT EXISTS idx_summary_evidences_merchant
    ON merchant_summary_evidences(source_merchant_id);

-- === import_tasks ===
CREATE INDEX IF NOT EXISTS idx_import_tasks_created_by
    ON import_tasks(created_by, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_import_tasks_status
    ON import_tasks(status, created_at DESC);

-- === import_task_items ===
CREATE INDEX IF NOT EXISTS idx_import_task_items_task_status
    ON import_task_items(task_id, status);

-- === ai_call_logs ===
CREATE INDEX IF NOT EXISTS idx_ai_call_logs_function_created
    ON ai_call_logs(function_type, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_ai_call_logs_status_created
    ON ai_call_logs(status, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_ai_call_logs_model
    ON ai_call_logs(model_name, created_at DESC);
-- Merchant reply lookup and one-visible-reply invariant.
CREATE UNIQUE INDEX IF NOT EXISTS uk_review_reply_visible
    ON review_reply(review_id)
    WHERE status = 'VISIBLE';

CREATE INDEX IF NOT EXISTS idx_review_reply_merchant_time
    ON review_reply(merchant_id, reply_time DESC);

-- User notification inbox and unread count.
CREATE INDEX IF NOT EXISTS idx_notifications_user_status_created
    ON notifications(user_id, status, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_notifications_review
    ON notifications(review_id);


CREATE INDEX IF NOT EXISTS idx_audit_logs_created_at
    ON audit_logs(created_at DESC);


CREATE INDEX IF NOT EXISTS idx_audit_logs_operator_user_id
    ON audit_logs(operator_user_id, created_at DESC);


CREATE INDEX IF NOT EXISTS idx_audit_logs_operator_username
    ON audit_logs(operator_username, created_at DESC);


CREATE INDEX IF NOT EXISTS idx_audit_logs_module
    ON audit_logs(module, created_at DESC);


CREATE INDEX IF NOT EXISTS idx_audit_logs_level
    ON audit_logs(level, created_at DESC);


CREATE INDEX IF NOT EXISTS idx_audit_logs_operation_type
    ON audit_logs(operation_type, created_at DESC);


CREATE INDEX IF NOT EXISTS idx_audit_logs_object
    ON audit_logs(object_type, object_id, created_at DESC);


CREATE UNIQUE INDEX IF NOT EXISTS uk_reviews_user_merchant_original
ON reviews(user_id, merchant_id)
WHERE user_id IS NOT NULL
  AND review_type = 'ORIGINAL'
  AND status <> 'DELETED';


CREATE UNIQUE INDEX IF NOT EXISTS uk_reviews_parent_follow_up
ON reviews(parent_review_id)
WHERE parent_review_id IS NOT NULL
  AND review_type = 'FOLLOW_UP'
  AND status <> 'DELETED';


CREATE UNIQUE INDEX IF NOT EXISTS uk_reviews_user_idempotency
ON reviews(user_id, idempotency_key)
WHERE user_id IS NOT NULL
  AND idempotency_key IS NOT NULL;


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


CREATE INDEX IF NOT EXISTS idx_model_configs_status
    ON model_configs(status);


CREATE INDEX IF NOT EXISTS idx_model_scene_bindings_config
    ON model_scene_bindings(model_config_id, status);


CREATE INDEX IF NOT EXISTS idx_hot_words_region_heat
    ON region_hot_words(region_code, status, heat_score DESC);


CREATE INDEX IF NOT EXISTS idx_hot_words_region_category
    ON region_hot_words(region_code, category, status, heat_score DESC);


CREATE INDEX IF NOT EXISTS idx_hot_words_period
    ON region_hot_words(period_type, period_end, status);


CREATE INDEX IF NOT EXISTS idx_hot_words_version
    ON region_hot_words(version, status);


CREATE INDEX IF NOT EXISTS idx_hot_words_created
    ON region_hot_words(created_at DESC);


CREATE UNIQUE INDEX IF NOT EXISTS uk_review_images_review_sort_active
    ON review_images(review_id, sort_order)
    WHERE status = 'ACTIVE';


CREATE INDEX IF NOT EXISTS idx_review_images_review
    ON review_images(review_id, status, sort_order);
