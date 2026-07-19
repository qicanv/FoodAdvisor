SELECT setval(
    pg_get_serial_sequence(sequence_target.table_name, 'id')::regclass,
    GREATEST(sequence_target.next_id, 1),
    false
)
FROM (
    SELECT 'users' AS table_name,
           COALESCE(MAX(id), 0) + 1 AS next_id FROM users
    UNION ALL
    SELECT 'merchants', COALESCE(MAX(id), 0) + 1 FROM merchants
    UNION ALL
    SELECT 'dishes', COALESCE(MAX(id), 0) + 1 FROM dishes
    UNION ALL
    SELECT 'reviews', COALESCE(MAX(id), 0) + 1 FROM reviews
    UNION ALL
    SELECT 'review_images', COALESCE(MAX(id), 0) + 1
      FROM review_images
    UNION ALL
    SELECT 'review_tags', COALESCE(MAX(id), 0) + 1
      FROM review_tags
    UNION ALL
    SELECT 'review_issue_categories', COALESCE(MAX(id), 0) + 1
      FROM review_issue_categories
    UNION ALL
    SELECT 'review_reply', COALESCE(MAX(id), 0) + 1
      FROM review_reply
    UNION ALL
    SELECT 'notifications', COALESCE(MAX(id), 0) + 1
      FROM notifications
    UNION ALL
    SELECT 'merchant_review_summaries', COALESCE(MAX(id), 0) + 1
      FROM merchant_review_summaries
    UNION ALL
    SELECT 'merchant_highlights', COALESCE(MAX(id), 0) + 1
      FROM merchant_highlights
    UNION ALL
    SELECT 'region_hot_words', COALESCE(MAX(id), 0) + 1
      FROM region_hot_words
    UNION ALL
    SELECT 'chat_sessions', COALESCE(MAX(id), 0) + 1
      FROM chat_sessions
    UNION ALL
    SELECT 'chat_messages', COALESCE(MAX(id), 0) + 1
      FROM chat_messages
    UNION ALL
    SELECT 'recommendations', COALESCE(MAX(id), 0) + 1
      FROM recommendations
    UNION ALL
    SELECT 'recommendation_items', COALESCE(MAX(id), 0) + 1
      FROM recommendation_items
) AS sequence_target;
