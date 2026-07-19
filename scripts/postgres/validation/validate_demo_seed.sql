\set ON_ERROR_STOP on

DO $demo_validation$
DECLARE
    bad integer;
    sequence_table text;
    sequence_name text;
    table_max_id bigint;
    sequence_last_value bigint;
    sequence_is_called boolean;
BEGIN
    IF (SELECT count(*) FROM users) <> 10 THEN RAISE EXCEPTION 'Expected 10 users'; END IF;
    IF (SELECT count(*) FROM merchants) <> 24 THEN RAISE EXCEPTION 'Expected 24 merchants'; END IF;
    IF (SELECT count(*) FROM dishes) <> 96 THEN RAISE EXCEPTION 'Expected 96 dishes'; END IF;
    IF (SELECT count(*) FROM reviews) <> 120 THEN RAISE EXCEPTION 'Expected 120 reviews'; END IF;
    IF (SELECT count(DISTINCT region_code) FROM merchants) <> 6 THEN RAISE EXCEPTION 'Expected 6 regions'; END IF;
    IF EXISTS (SELECT 1 FROM merchants WHERE operation_status='OPEN') THEN RAISE EXCEPTION 'OPEN is forbidden'; END IF;
    IF EXISTS (SELECT 1 FROM users WHERE password_hash !~ '^[$]2[aby][$]' OR password_hash='Demo@123456')
       THEN RAISE EXCEPTION 'Invalid password storage'; END IF;
    IF EXISTS (SELECT 1 FROM users WHERE email NOT LIKE '%@example.invalid')
       THEN RAISE EXCEPTION 'Non-demo email found'; END IF;
    IF EXISTS (SELECT 1 FROM merchants WHERE cover_image_url ~ '^https?://')
       OR EXISTS (SELECT 1 FROM review_images WHERE image_url ~ '^https?://')
       THEN RAISE EXCEPTION 'External image URL found'; END IF;
    IF EXISTS (SELECT 1 FROM merchants m WHERE m.operation_status='OPERATING'
       AND NOT EXISTS (SELECT 1 FROM dishes d WHERE d.merchant_id=m.id AND d.status='ACTIVE'))
       THEN RAISE EXCEPTION 'Operating merchant without active dishes'; END IF;
    IF EXISTS (SELECT 1 FROM dishes d LEFT JOIN merchants m ON m.id=d.merchant_id WHERE m.id IS NULL)
       THEN RAISE EXCEPTION 'Orphan dish'; END IF;
    IF EXISTS (SELECT 1 FROM reviews f JOIN reviews p ON p.id=f.parent_review_id
       WHERE f.review_type='FOLLOW_UP' AND (f.user_id<>p.user_id OR f.merchant_id<>p.merchant_id
       OR p.review_type<>'ORIGINAL')) THEN RAISE EXCEPTION 'Invalid follow-up relation'; END IF;
    IF EXISTS (SELECT 1 FROM review_reply rr JOIN reviews r ON r.id=rr.review_id
       WHERE rr.merchant_id<>r.merchant_id) THEN RAISE EXCEPTION 'Cross-merchant reply'; END IF;
    IF EXISTS (SELECT review_id FROM review_reply WHERE status='VISIBLE' GROUP BY review_id HAVING count(*)>1)
       THEN RAISE EXCEPTION 'Duplicate visible reply'; END IF;
    IF EXISTS (SELECT 1 FROM notifications n JOIN reviews r ON r.id=n.review_id
       WHERE n.user_id<>r.user_id OR n.merchant_id<>r.merchant_id)
       THEN RAISE EXCEPTION 'Invalid notification recipient'; END IF;
    IF EXISTS (SELECT 1 FROM merchants m LEFT JOIN (
       SELECT merchant_id,count(*)::int c,round(avg(rating),2) a FROM reviews
       WHERE review_type='ORIGINAL' AND status='PUBLISHED' AND moderation_status='APPROVED'
       GROUP BY merchant_id) s ON s.merchant_id=m.id
       WHERE m.review_count<>coalesce(s.c,0) OR m.rating IS DISTINCT FROM s.a)
       THEN RAISE EXCEPTION 'Merchant aggregates mismatch'; END IF;
    IF EXISTS (SELECT 1 FROM merchant_summary_evidences e
       JOIN merchant_review_summaries s ON s.id=e.summary_id JOIN reviews r ON r.id=e.review_id
       WHERE s.merchant_id<>r.merchant_id) THEN RAISE EXCEPTION 'Summary evidence crosses merchant'; END IF;
    IF EXISTS (SELECT 1 FROM merchant_highlight_evidences e
       JOIN merchant_highlights h ON h.id=e.highlight_id JOIN reviews r ON r.id=e.review_id
       WHERE h.merchant_id<>r.merchant_id) THEN RAISE EXCEPTION 'Highlight evidence crosses merchant'; END IF;
    IF EXISTS (SELECT 1 FROM recommendation_items i JOIN merchants m ON m.id=i.merchant_id
       WHERE m.platform_status<>'ACTIVE' OR m.operation_status<>'OPERATING')
       THEN RAISE EXCEPTION 'Recommendation contains unavailable merchant'; END IF;
    IF EXISTS (SELECT 1 FROM recommendation_evidences e
       JOIN recommendation_items i ON i.id=e.recommendation_item_id
       LEFT JOIN reviews r ON r.id=e.review_id
       WHERE e.source_merchant_id<>i.merchant_id OR
       (r.id IS NOT NULL AND (r.merchant_id<>i.merchant_id OR r.status<>'PUBLISHED'
       OR r.moderation_status<>'APPROVED')))
       THEN RAISE EXCEPTION 'Invalid recommendation evidence'; END IF;
    IF EXISTS (
       SELECT session_id, request_id, role
         FROM chat_messages
        WHERE request_id IS NOT NULL
        GROUP BY session_id, request_id, role
       HAVING count(*) > 1
    )
       OR EXISTS (SELECT request_id FROM recommendations WHERE request_id IS NOT NULL GROUP BY request_id HAVING count(*)>1)
       THEN RAISE EXCEPTION 'Duplicate idempotency key'; END IF;
    IF (SELECT count(DISTINCT region_code) FROM region_hot_words WHERE status='ACTIVE')<>6
       THEN RAISE EXCEPTION 'Every region needs hot words'; END IF;
    IF EXISTS (SELECT 1 FROM region_hot_words h WHERE NOT EXISTS (
       SELECT 1 FROM reviews r JOIN merchants m ON m.id=r.merchant_id
       JOIN review_analysis a ON a.review_id=r.id
       WHERE m.region_code=h.region_code AND a.keywords ? h.word))
       THEN RAISE EXCEPTION 'Hot word lacks keyword evidence'; END IF;
    IF to_regclass('public.restaurants') IS NOT NULL THEN RAISE EXCEPTION 'restaurants must not exist'; END IF;
    IF EXISTS (SELECT 1 FROM model_configs WHERE encrypted_api_key !~ '^DEMO')
       THEN RAISE EXCEPTION 'Unexpected model key'; END IF;

    FOREACH sequence_table IN ARRAY ARRAY[
        'users', 'merchants', 'dishes', 'reviews', 'review_images',
        'review_tags', 'review_issue_categories', 'review_reply',
        'notifications', 'merchant_review_summaries',
        'merchant_highlights', 'region_hot_words', 'chat_sessions',
        'chat_messages', 'recommendations', 'recommendation_items'
    ]
    LOOP
        sequence_name :=
            pg_get_serial_sequence(sequence_table, 'id');
        EXECUTE format(
            'SELECT COALESCE(MAX(id), 0) FROM %I',
            sequence_table
        ) INTO table_max_id;
        EXECUTE format(
            'SELECT last_value, is_called FROM %s',
            sequence_name
        ) INTO sequence_last_value, sequence_is_called;

        IF (sequence_is_called
                AND sequence_last_value < table_max_id)
           OR (NOT sequence_is_called
                AND sequence_last_value <= table_max_id) THEN
            RAISE EXCEPTION
                '% sequence % is not synchronized: max(id)=%, last_value=%, is_called=%',
                sequence_table,
                sequence_name,
                table_max_id,
                sequence_last_value,
                sequence_is_called;
        END IF;
    END LOOP;

    SELECT count(*) INTO bad FROM pg_constraint WHERE contype='f' AND NOT convalidated;
    IF bad<>0 THEN RAISE EXCEPTION 'Unvalidated foreign keys'; END IF;
END
$demo_validation$;

SELECT 'users' table_name,count(*) row_count FROM users UNION ALL
SELECT 'merchants',count(*) FROM merchants UNION ALL SELECT 'dishes',count(*) FROM dishes UNION ALL
SELECT 'reviews',count(*) FROM reviews UNION ALL SELECT 'review_images',count(*) FROM review_images UNION ALL
SELECT 'review_analysis',count(*) FROM review_analysis UNION ALL SELECT 'review_reply',count(*) FROM review_reply UNION ALL
SELECT 'notifications',count(*) FROM notifications UNION ALL SELECT 'region_hot_words',count(*) FROM region_hot_words UNION ALL
SELECT 'chat_sessions',count(*) FROM chat_sessions UNION ALL SELECT 'chat_messages',count(*) FROM chat_messages UNION ALL
SELECT 'recommendations',count(*) FROM recommendations UNION ALL SELECT 'recommendation_items',count(*) FROM recommendation_items UNION ALL
SELECT 'recommendation_evidences',count(*) FROM recommendation_evidences UNION ALL
SELECT 'recommendation_feedback',count(*) FROM recommendation_feedback ORDER BY table_name;
