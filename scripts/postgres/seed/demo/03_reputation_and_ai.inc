INSERT INTO merchant_review_summaries (
 id, merchant_id, version, summary_text, advantages, disadvantages,
 recommended_dishes, environment_summary, service_summary, recent_changes,
 review_count, source_start_time, source_end_time, status, model_name,
 model_version, generated_at
)
SELECT id, id, 1, '演示摘要：评价覆盖味道、服务和环境。',
 '["味道稳定","服务友好"]', '["高峰期可能等候"]', '["招牌一号"]',
 '{"summary":"环境整洁"}', '{"summary":"响应及时"}', '[]',
 review_count, '2026-03-01', '2026-04-30', 'SUCCESS',
 'DEMO-RULE', '1.0', '2026-05-01 08:00+08' FROM merchants;

INSERT INTO merchant_summary_evidences (
 summary_id, review_id, source_type, source_merchant_id, review_version,
 evidence_type, evidence_excerpt, created_at
)
SELECT s.id, r.id, 'REVIEW', s.merchant_id, r.version,
 'ADVANTAGE', left(r.content,120), '2026-05-01 08:00+08'
FROM merchant_review_summaries s
JOIN LATERAL (SELECT id,content,version FROM reviews WHERE merchant_id=s.merchant_id
 AND status='PUBLISHED' AND moderation_status='APPROVED' ORDER BY id LIMIT 1) r ON true;

INSERT INTO merchant_highlights (
 id, merchant_id, highlight_type, title, description, mention_count,
 positive_ratio, version, status, generated_at
)
SELECT id, id, 'TASTE',
 CASE WHEN id = 10 THEN '微辣风味' ELSE '虚构演示亮点' END,
 CASE WHEN id = 10 THEN '评价明确提到菜品微辣、辣度柔和'
      ELSE '味道和服务获得稳定反馈' END,
 greatest(review_count,1), 0.6667, 1, 'ACTIVE', '2026-05-01 08:00+08'
FROM merchants;

INSERT INTO merchant_highlight_evidences (
 highlight_id, review_id, review_version, evidence_excerpt, created_at
)
SELECT h.id, r.id, 1, left(r.content,120), '2026-05-01 08:00+08'
FROM merchant_highlights h
JOIN LATERAL (SELECT id,content FROM reviews WHERE merchant_id=h.merchant_id
 AND status='PUBLISHED' AND moderation_status='APPROVED' ORDER BY id LIMIT 1) r ON true;

INSERT INTO merchant_reputation_statistics (
 merchant_id, period_type, period_start, period_end, average_rating,
 positive_count, neutral_count, negative_count, total_review_count,
 positive_ratio, negative_ratio, created_at
)
SELECT merchant_id, 'MONTH', DATE '2026-03-01', DATE '2026-03-31',
 round(avg(rating),2), count(*) FILTER(WHERE rating>=4),
 count(*) FILTER(WHERE rating=3), count(*) FILTER(WHERE rating<=2), count(*),
 (count(*) FILTER(WHERE rating>=4))::numeric/count(*),
 (count(*) FILTER(WHERE rating<=2))::numeric/count(*), '2026-05-01'
FROM reviews WHERE review_type='ORIGINAL' AND status='PUBLISHED'
 AND moderation_status='APPROVED' GROUP BY merchant_id;

INSERT INTO region_hot_words (
 id, region_code, word, category, sentiment, heat_score, mention_count,
 review_count, merchant_count, positive_ratio, source_type, period_type,
 period_start, period_end, version, status, created_at, updated_at
)
SELECT row_number() OVER (), region_code,
 CASE region_code WHEN 'CD-JJ' THEN '味道' WHEN 'CD-QY' THEN '环境'
  WHEN 'CD-WH' THEN '分量' WHEN 'CD-CH' THEN '卫生'
  WHEN 'CD-JN' THEN '味道' ELSE '服务' END,
 'GENERAL','POSITIVE',75.00,3,3,3,0.6667,'KEYWORD_EXTRACT','MONTHLY',
 '2026-03-01','2026-03-31',1,'ACTIVE','2026-05-01','2026-05-01'
FROM (SELECT DISTINCT region_code FROM merchants) x;

INSERT INTO chat_sessions (id,user_id,title,status,created_at,updated_at) VALUES
(1,2,'四人川菜聚餐','ACTIVE','2026-05-02 10:00+08','2026-05-02 10:10+08'),
(2,3,'两人安静约会','ACTIVE','2026-05-03 10:00+08','2026-05-03 10:10+08'),
(3,4,'一人快速简餐','ACTIVE','2026-05-04 10:00+08','2026-05-04 10:10+08');

INSERT INTO chat_messages (id,session_id,role,content,message_type,request_id,metadata,created_at)
SELECT g, ((g-1)/4)+1, CASE WHEN g%4 IN(1,3) THEN 'USER' ELSE 'ASSISTANT' END,
 CASE WHEN g%4 IN(1,3) THEN '请给出演示探店建议' ELSE '已整理虚构商家推荐' END,
 CASE WHEN g%4=0 THEN 'RECOMMENDATION' ELSE 'TEXT' END,
 'DEMO-MSG-'||g, '{}', '2026-05-02 10:00+08'::timestamptz+g*interval '1 minute'
FROM generate_series(1,12) g;

INSERT INTO chat_session_states (
 session_id,current_constraints,missing_fields,rejected_fields,
 conversation_stage,version,updated_at
)
SELECT id, CASE id WHEN 1 THEN '{"partySize":4,"budget":80,"cuisine":"川菜"}'
 WHEN 2 THEN '{"partySize":2,"scene":"约会","distanceKm":3}'
 ELSE '{"partySize":1,"budget":35,"scene":"简餐"}' END::jsonb,
 '[]','[]','RECOMMENDED',1,'2026-05-04 11:00+08' FROM chat_sessions;

INSERT INTO constraint_extractions (
 session_id,message_id,extracted_constraints,merged_constraints,
 changed_fields,conflict_fields,model_name,model_version,created_at
)
SELECT s.id, (s.id-1)*4+1, st.current_constraints, st.current_constraints,
 '["demo"]','[]','DEMO-RULE','1.0','2026-05-04 11:00+08'
FROM chat_sessions s JOIN chat_session_states st ON st.session_id=s.id;

INSERT INTO recommendations (
 id,user_id,session_id,user_message_id,assistant_message_id,request_id,
 trace_id,query_text,parsed_constraints,reply_text,algorithm_version,
 status,result_count,created_at,completed_at
)
SELECT s.id,s.user_id,s.id,(s.id-1)*4+1,(s.id-1)*4+4,'DEMO-REC-'||s.id,'DEMO-TRACE-'||s.id,
 s.title,(SELECT current_constraints FROM chat_session_states WHERE session_id=s.id),
 '以下均为虚构演示商家。','demo-1.0','SUCCESS',3,
 '2026-05-05 10:00+08','2026-05-05 10:00+08' FROM chat_sessions s;

INSERT INTO recommendation_items (
 id,recommendation_id,merchant_id,rank_no,score,score_details,
 matched_conditions,unmatched_conditions,reason,created_at
)
SELECT (r.id-1)*3+rank_no,r.id,1+(r.id-1)*4+(rank_no-1),
 rank_no,0.95-rank_no*0.05,'{"demo":true}','["营业中","虚构数据"]','[]',
 '与演示预算、场景和距离条件相符。','2026-05-05 10:00+08'
FROM recommendations r CROSS JOIN generate_series(1,3) rank_no;

INSERT INTO recommendation_evidences (
 recommendation_item_id,source_type,source_merchant_id,review_id,
 evidence_excerpt,source_text_snapshot,relevance_score,created_at
)
SELECT ri.id,'REVIEW',ri.merchant_id,r.id,left(r.content,120),left(r.content,120),
 0.900000,'2026-05-05 10:00+08'
FROM recommendation_items ri JOIN LATERAL (
 SELECT id,content FROM reviews WHERE merchant_id=ri.merchant_id
  AND status='PUBLISHED' AND moderation_status='APPROVED' ORDER BY id LIMIT 1
) r ON true;

INSERT INTO recommendation_feedback (
 recommendation_id,feedback_type,content,reason_category,created_at,updated_at
) VALUES
(1,'SATISFIED','演示反馈：推荐符合聚餐需求','MATCH','2026-05-06','2026-05-06'),
(2,'DISSATISFIED','演示反馈：希望更安静','ENVIRONMENT','2026-05-06','2026-05-06');

INSERT INTO audit_logs (
 operation_type,operator_user_id,operator_username,operator_role,module,
 level,result,object_type,object_id,metadata,created_at
) VALUES
('LOGIN',1,'demo_admin','ADMIN','AUTH','INFO','SUCCESS','USER','1','{"demo":true}','2026-05-06 09:00+08'),
('ADMIN_OPERATION',1,'demo_admin','ADMIN','MERCHANT','INFO','SUCCESS','MERCHANT','1','{"demo":true}','2026-05-06 09:05+08');
