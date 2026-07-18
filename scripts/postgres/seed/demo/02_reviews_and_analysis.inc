INSERT INTO reviews (
 id, merchant_id, user_id, review_type, rating, taste_rating,
 environment_rating, service_rating, average_spend, consumption_date,
 content, source, review_time, idempotency_key, current_version, status,
 moderation_status, risk_level, published_at, created_at, updated_at
)
SELECT g, ((g - 1) % 24) + 1, 2 + ((g - 1) / 24), 'ORIGINAL',
       1 + ((g - 1) % 5), 1 + ((g + 1) % 5), 1 + ((g + 2) % 5),
       1 + ((g + 3) % 5), 30 + (g % 8) * 10,
       DATE '2026-03-01' + (g % 20),
       CASE WHEN g % 5 IN (0,4) THEN '演示评价：味道鲜香，服务热情，环境整洁，适合再次到访。'
            WHEN g % 5 = 3 THEN '演示评价：整体表现中规中矩，价格和分量基本符合预期。'
            ELSE '演示评价：等候时间较长，服务和卫生细节仍有改进空间。' END,
       'USER', CASE WHEN g % 10 = 0 THEN NULL ELSE '2026-03-01 12:00+08'::timestamptz + g * interval '1 hour' END,
       'DEMO-REVIEW-' || g, 1,
       CASE WHEN g <= 72 THEN 'PUBLISHED' WHEN g <= 80 THEN 'PENDING'
            WHEN g <= 88 THEN 'HIDDEN' ELSE 'DELETED' END,
       CASE WHEN g <= 80 THEN CASE WHEN g <= 72 THEN 'APPROVED' ELSE 'PENDING' END ELSE 'REJECTED' END,
       CASE WHEN g <= 72 THEN 'LOW' WHEN g <= 80 THEN 'HIGH' ELSE 'MEDIUM' END,
       CASE WHEN g <= 72 THEN '2026-03-02 12:00+08'::timestamptz + g * interval '1 hour' END,
       '2026-03-01 12:00+08'::timestamptz + g * interval '1 hour',
       '2026-03-01 12:00+08'::timestamptz + g * interval '1 hour'
FROM generate_series(1, 96) g;

INSERT INTO reviews (
 id, merchant_id, user_id, review_type, parent_review_id, rating,
 consumption_date, content, source, review_time, idempotency_key,
 current_version, status, moderation_status, risk_level, published_at,
 created_at, updated_at
)
SELECT 96 + g, r.merchant_id, r.user_id, 'FOLLOW_UP', r.id,
       3 + (g % 3), DATE '2026-04-01' + (g % 15),
       '演示追评：再次到店后体验稳定，补充记录本次服务情况。',
       'USER', '2026-04-01 12:00+08'::timestamptz + g * interval '1 hour',
       'DEMO-FOLLOWUP-' || g, 1, 'PUBLISHED', 'APPROVED', 'LOW',
       '2026-04-01 13:00+08'::timestamptz + g * interval '1 hour',
       '2026-04-01 12:00+08'::timestamptz + g * interval '1 hour',
       '2026-04-01 12:00+08'::timestamptz + g * interval '1 hour'
FROM generate_series(1, 24) g JOIN reviews r ON r.id = g;

INSERT INTO review_images (
 id, review_id, original_filename, storage_provider, storage_key,
 image_url, mime_type, file_size, sort_order, status, created_at
)
SELECT g, g, 'demo-' || g || '.webp', 'LOCAL', 'demo/reviews/' || g || '.webp',
       '/uploads/demo/reviews/' || g || '.webp', 'image/webp', 1024, 1,
       'ACTIVE', '2026-03-10 10:00+08'
FROM generate_series(1, 12) g;

INSERT INTO review_versions (
 review_id, version, rating, content, status_snapshot,
 moderation_status_snapshot, changed_by, change_type, created_at
)
SELECT id, 1, rating, content, status, moderation_status, user_id,
       CASE WHEN review_type = 'FOLLOW_UP' THEN 'FOLLOW_UP' ELSE 'CREATE' END,
       created_at FROM reviews;

INSERT INTO review_tags (id, code, name, category, status, created_at, updated_at) VALUES
(1,'TASTE','味道','TASTE','ACTIVE','2026-01-01','2026-01-01'),
(2,'SERVICE','服务','SERVICE','ACTIVE','2026-01-01','2026-01-01'),
(3,'ENVIRONMENT','环境','ENVIRONMENT','ACTIVE','2026-01-01','2026-01-01'),
(4,'PRICE','价格','PRICE','ACTIVE','2026-01-01','2026-01-01'),
(5,'PORTION','分量','GENERAL','ACTIVE','2026-01-01','2026-01-01'),
(6,'QUEUE','排队','SPEED','ACTIVE','2026-01-01','2026-01-01'),
(7,'HYGIENE','卫生','ENVIRONMENT','ACTIVE','2026-01-01','2026-01-01'),
(8,'TRAFFIC','交通','GENERAL','ACTIVE','2026-01-01','2026-01-01');

INSERT INTO review_issue_categories (id, code, name, description, status, created_at, updated_at)
SELECT id, code, name, '稳定的虚构演示问题分类', 'ACTIVE', '2026-01-01', '2026-01-01'
FROM (VALUES (1,'TASTE','味道'),(2,'SERVICE','服务'),(3,'ENVIRONMENT','环境'),
 (4,'PRICE','价格'),(5,'PORTION','分量'),(6,'QUEUE','排队'),
 (7,'HYGIENE','卫生'),(8,'TRAFFIC','交通')) v(id,code,name);

INSERT INTO review_analysis (
 review_id, review_version, analysis_version, sentiment, confidence,
 keywords, aspects, status, completed_at, created_at, updated_at
)
SELECT id, 1, 1,
       CASE WHEN rating >= 4 THEN 'POSITIVE' WHEN rating = 3 THEN 'NEUTRAL' ELSE 'NEGATIVE' END,
       0.9000,
       CASE ((merchant_id - 1) % 6)
         WHEN 0 THEN '["味道","服务"]'::jsonb WHEN 1 THEN '["环境","价格"]'::jsonb
         WHEN 2 THEN '["分量","排队"]'::jsonb WHEN 3 THEN '["卫生","交通"]'::jsonb
         WHEN 4 THEN '["味道","环境"]'::jsonb ELSE '["服务","价格"]'::jsonb END,
       '{"source":"DEMO"}'::jsonb, 'SUCCESS', '2026-04-10 10:00+08',
       '2026-04-10 10:00+08', '2026-04-10 10:00+08'
FROM reviews WHERE status = 'PUBLISHED' AND moderation_status = 'APPROVED';

INSERT INTO review_tag_relations (
 review_id, review_version, tag_id, sentiment, confidence,
 evidence_text, model_name, created_at, updated_at
)
SELECT r.id, 1, 1 + ((r.id - 1) % 8),
       CASE WHEN r.rating >= 4 THEN 'POSITIVE' WHEN r.rating = 3 THEN 'NEUTRAL' ELSE 'NEGATIVE' END,
       0.9000, '演示评价中的对应主题', 'DEMO-RULE',
       '2026-04-10 10:00+08', '2026-04-10 10:00+08'
FROM reviews r WHERE r.status = 'PUBLISHED' AND r.moderation_status = 'APPROVED';

INSERT INTO review_issue_relations (
 review_id, review_version, issue_category_id, confidence, evidence_text, created_at
)
SELECT id, 1, 1 + ((id - 1) % 8), 0.8500, '演示负面问题依据', '2026-04-10 10:00+08'
FROM reviews WHERE status = 'PUBLISHED' AND moderation_status = 'APPROVED' AND rating <= 2;

INSERT INTO review_reply (
 id, review_id, merchant_id, reply_content, reply_time, status, created_at, updated_at
)
SELECT r.id, r.id, r.merchant_id, '感谢您的演示评价，我们会认真持续改进。',
       '2026-04-12 10:00+08'::timestamptz + r.id * interval '1 hour',
       'VISIBLE', '2026-04-12 10:00+08', '2026-04-12 10:00+08'
FROM reviews r WHERE r.id BETWEEN 1 AND 12;

INSERT INTO notifications (
 id, user_id, review_id, merchant_id, type, title, review_summary,
 reply_summary, merchant_name, status, notified, created_at, updated_at
)
SELECT rr.id, r.user_id, r.id, r.merchant_id, 'REVIEW_REPLY', '商家回复了演示评价',
       left(r.content,100), left(rr.reply_content,100), m.name,
       CASE WHEN rr.id % 2 = 0 THEN 'READ' ELSE 'UNREAD' END, true,
       '2026-04-12 11:00+08', '2026-04-12 11:00+08'
FROM review_reply rr JOIN reviews r ON r.id=rr.review_id
JOIN merchants m ON m.id=r.merchant_id;

UPDATE merchants m SET
 review_count = s.cnt,
 rating = s.avg_rating
FROM (
 SELECT merchant_id, count(*)::integer cnt, round(avg(rating),2) avg_rating
 FROM reviews WHERE review_type='ORIGINAL' AND status='PUBLISHED'
   AND moderation_status='APPROVED' GROUP BY merchant_id
) s WHERE m.id=s.merchant_id;

SELECT setval(pg_get_serial_sequence('reviews','id'), 120, true);
