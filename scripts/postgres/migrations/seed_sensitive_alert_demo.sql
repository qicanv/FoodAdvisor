-- ============================================================
-- 敏感话题预警 - 演示种子数据
-- 为4个商家分别注入食品安全/卫生/投诉/纠纷类敏感评价
-- 每条评价均为 PUBLISHED + APPROVED 状态，时间戳为当前时间附近
-- ============================================================

-- ============================================================
-- 商家1 (锦江区川菜演示馆1): 食品安全 — 5条评价
-- ============================================================
INSERT INTO reviews (
    id, merchant_id, user_id, review_type, rating, taste_rating,
    environment_rating, service_rating, average_spend, consumption_date,
    content, source, idempotency_key, current_version, status,
    moderation_status, risk_level, published_at, created_at, updated_at
) VALUES
(20001, 1, NULL, 'ORIGINAL', 1, 2, 3, 1, 45, DATE '2026-07-23',
 '昨天在这家店吃完就拉肚子了，怀疑是食物中毒，菜里还有异物，太可怕了！',
 'USER', 'SENS-DEMO-001', 1, 'PUBLISHED', 'APPROVED', 'LOW',
 NOW() - interval '2 hours', NOW() - interval '2 hours', NOW() - interval '2 hours'),
(20002, 1, NULL, 'ORIGINAL', 1, 1, 3, 2, 38, DATE '2026-07-23',
 '吃坏肚子了，我们一桌四个人全拉肚子了，这食材肯定过期变质了。',
 'USER', 'SENS-DEMO-002', 1, 'PUBLISHED', 'APPROVED', 'LOW',
 NOW() - interval '3 hours', NOW() - interval '3 hours', NOW() - interval '3 hours'),
(20003, 1, NULL, 'ORIGINAL', 2, 1, 4, 1, 52, DATE '2026-07-24',
 '吃完不舒服，晚上上吐下泻的，怀疑他们的食材不新鲜，还有蟑螂在厨房爬。',
 'USER', 'SENS-DEMO-003', 1, 'PUBLISHED', 'APPROVED', 'LOW',
 NOW() - interval '5 hours', NOW() - interval '5 hours', NOW() - interval '5 hours'),
(20004, 1, NULL, 'ORIGINAL', 1, 2, 2, 1, 30, DATE '2026-07-24',
 '馊了的菜！发霉的食材还在用！吃完就闹肚子，绝对食品质量问题。',
 'USER', 'SENS-DEMO-004', 1, 'PUBLISHED', 'APPROVED', 'LOW',
 NOW() - interval '1 hour', NOW() - interval '1 hour', NOW() - interval '1 hour'),
(20005, 1, NULL, 'ORIGINAL', 1, 1, 2, 1, 60, DATE '2026-07-24',
 '太恶心了，菜里有苍蝇，我们一桌都食物中毒了，已经去卫生局举报了。',
 'USER', 'SENS-DEMO-005', 1, 'PUBLISHED', 'APPROVED', 'LOW',
 NOW() - interval '30 minutes', NOW() - interval '30 minutes', NOW() - interval '30 minutes');

-- ============================================================
-- 商家5 (青羊区火锅演示馆): 卫生问题 — 4条评价
-- ============================================================
INSERT INTO reviews (
    id, merchant_id, user_id, review_type, rating, taste_rating,
    environment_rating, service_rating, average_spend, consumption_date,
    content, source, idempotency_key, current_version, status,
    moderation_status, risk_level, published_at, created_at, updated_at
) VALUES
(20006, 5, NULL, 'ORIGINAL', 1, 2, 1, 2, 80, DATE '2026-07-24',
 '这家店卫生堪忧！厨房脏得不行，地上全是油腻污渍，蟑螂到处爬。',
 'USER', 'SENS-DEMO-006', 1, 'PUBLISHED', 'APPROVED', 'LOW',
 NOW() - interval '2 hours', NOW() - interval '2 hours', NOW() - interval '2 hours'),
(20007, 5, NULL, 'ORIGINAL', 2, 3, 1, 3, 90, DATE '2026-07-24',
 '卫生间脏得没法用，洗手池都是污渍。厨房里看到老鼠了，太不干净了！',
 'USER', 'SENS-DEMO-007', 1, 'PUBLISHED', 'APPROVED', 'LOW',
 NOW() - interval '3 hours', NOW() - interval '3 hours', NOW() - interval '3 hours'),
(20008, 5, NULL, 'ORIGINAL', 2, 2, 1, 2, 75, DATE '2026-07-24',
 '整体环境很差，油腻腻的桌子和地板，餐具也不干净。卫生差评。',
 'USER', 'SENS-DEMO-008', 1, 'PUBLISHED', 'APPROVED', 'LOW',
 NOW() - interval '4 hours', NOW() - interval '4 hours', NOW() - interval '4 hours'),
(20009, 5, NULL, 'ORIGINAL', 1, 2, 1, 1, 65, DATE '2026-07-24',
 '洗碗水看起来很脏，消毒措施基本没有，这种不卫生的店以后不会再来了。',
 'USER', 'SENS-DEMO-009', 1, 'PUBLISHED', 'APPROVED', 'LOW',
 NOW() - interval '1 hour', NOW() - interval '1 hour', NOW() - interval '1 hour');

-- ============================================================
-- 商家10 (锦江区粤菜演示馆): 严重服务纠纷 — 5条评价
-- ============================================================
INSERT INTO reviews (
    id, merchant_id, user_id, review_type, rating, taste_rating,
    environment_rating, service_rating, average_spend, consumption_date,
    content, source, idempotency_key, current_version, status,
    moderation_status, risk_level, published_at, created_at, updated_at
) VALUES
(20010, 10, NULL, 'ORIGINAL', 1, 3, 2, 1, 120, DATE '2026-07-24',
 '服务员态度恶劣，直接骂人，我们要求道歉还被老板威胁说要打人。',
 'USER', 'SENS-DEMO-010', 1, 'PUBLISHED', 'APPROVED', 'LOW',
 NOW() - interval '2 hours', NOW() - interval '2 hours', NOW() - interval '2 hours'),
(20011, 10, NULL, 'ORIGINAL', 1, 2, 1, 1, 110, DATE '2026-07-24',
 '发生了严重冲突！服务员和顾客争执起来，差点动手，最后报警了才解决。',
 'USER', 'SENS-DEMO-011', 1, 'PUBLISHED', 'APPROVED', 'LOW',
 NOW() - interval '3 hours', NOW() - interval '3 hours', NOW() - interval '3 hours'),
(20012, 10, NULL, 'ORIGINAL', 1, 2, 3, 1, 100, DATE '2026-07-24',
 '第一次遇到这么态度恶劣的服务员，对我们人身攻击，说我们是穷鬼吃不起。太侮辱人了！',
 'USER', 'SENS-DEMO-012', 1, 'PUBLISHED', 'APPROVED', 'LOW',
 NOW() - interval '4 hours', NOW() - interval '4 hours', NOW() - interval '4 hours'),
(20013, 10, NULL, 'ORIGINAL', 2, 3, 2, 1, 95, DATE '2026-07-24',
 '强制消费，必须点他们推荐的高价菜，不点就甩脸色。霸王条款加多收钱。',
 'USER', 'SENS-DEMO-013', 1, 'PUBLISHED', 'APPROVED', 'LOW',
 NOW() - interval '5 hours', NOW() - interval '5 hours', NOW() - interval '5 hours'),
(20014, 10, NULL, 'ORIGINAL', 1, 1, 2, 1, 130, DATE '2026-07-24',
 '被宰客了，四个人吃了两千多还全是预制菜。服务员歧视外地人，真的太过分了。',
 'USER', 'SENS-DEMO-014', 1, 'PUBLISHED', 'APPROVED', 'LOW',
 NOW() - interval '1 hour', NOW() - interval '1 hour', NOW() - interval '1 hour');

-- ============================================================
-- 商家15 (高新区咖啡甜品演示馆): 集中投诉 — 4条评价
-- ============================================================
INSERT INTO reviews (
    id, merchant_id, user_id, review_type, rating, taste_rating,
    environment_rating, service_rating, average_spend, consumption_date,
    content, source, idempotency_key, current_version, status,
    moderation_status, risk_level, published_at, created_at, updated_at
) VALUES
(20015, 15, NULL, 'ORIGINAL', 1, 2, 3, 1, 55, DATE '2026-07-24',
 '我要投诉这家店！买的蛋糕是过期改标签的，退款还要推三阻四，已向消协举报。',
 'USER', 'SENS-DEMO-015', 1, 'PUBLISHED', 'APPROVED', 'LOW',
 NOW() - interval '2 hours', NOW() - interval '2 hours', NOW() - interval '2 hours'),
(20016, 15, NULL, 'ORIGINAL', 2, 3, 2, 2, 48, DATE '2026-07-24',
 '商家态度敷衍，出了问题不理不睬。要求赔偿还被说成是无理取闹，就是黑店。',
 'USER', 'SENS-DEMO-016', 1, 'PUBLISHED', 'APPROVED', 'LOW',
 NOW() - interval '3 hours', NOW() - interval '3 hours', NOW() - interval '3 hours'),
(20017, 15, NULL, 'ORIGINAL', 1, 1, 3, 2, 62, DATE '2026-07-24',
 '上当受骗了！团购券到店不让用，还强行要求加钱。我要去工商局投诉维权。',
 'USER', 'SENS-DEMO-017', 1, 'PUBLISHED', 'APPROVED', 'LOW',
 NOW() - interval '4 hours', NOW() - interval '4 hours', NOW() - interval '4 hours'),
(20018, 15, NULL, 'ORIGINAL', 1, 2, 2, 2, 40, DATE '2026-07-24',
 '退款拖了一个月都没到账，客服态度极差不理人。这种奸商就应该曝光。',
 'USER', 'SENS-DEMO-018', 1, 'PUBLISHED', 'APPROVED', 'LOW',
 NOW() - interval '1 hour', NOW() - interval '1 hour', NOW() - interval '1 hour');

-- ============================================================
-- 商家3 (武侯区川菜演示馆): 食品安全+卫生 — 3条混搭评价
-- ============================================================
INSERT INTO reviews (
    id, merchant_id, user_id, review_type, rating, taste_rating,
    environment_rating, service_rating, average_spend, consumption_date,
    content, source, idempotency_key, current_version, status,
    moderation_status, risk_level, published_at, created_at, updated_at
) VALUES
(20019, 3, NULL, 'ORIGINAL', 1, 1, 1, 1, 50, DATE '2026-07-24',
 '食材不新鲜，吃完就呕吐了。而且厨房很脏，到处是蟑螂老鼠。',
 'USER', 'SENS-DEMO-019', 1, 'PUBLISHED', 'APPROVED', 'LOW',
 NOW() - interval '2 hours', NOW() - interval '2 hours', NOW() - interval '2 hours'),
(20020, 3, NULL, 'ORIGINAL', 2, 2, 1, 2, 55, DATE '2026-07-24',
 '在菜里发现了虫子！太恶心了。而且卫生间脏得没法下脚。',
 'USER', 'SENS-DEMO-020', 1, 'PUBLISHED', 'APPROVED', 'LOW',
 NOW() - interval '3 hours', NOW() - interval '3 hours', NOW() - interval '3 hours'),
(20021, 3, NULL, 'ORIGINAL', 1, 2, 1, 1, 42, DATE '2026-07-24',
 '这家店食品安全堪忧，鸡肉没煮熟还带血丝。厨房苍蝇到处飞，卫生条件太差了。',
 'USER', 'SENS-DEMO-021', 1, 'PUBLISHED', 'APPROVED', 'LOW',
 NOW() - interval '1 hour', NOW() - interval '1 hour', NOW() - interval '1 hour');

-- ============================================================
-- 商家8 (成华区火锅演示馆): 服务纠纷+投诉 — 4条混搭评价
-- ============================================================
INSERT INTO reviews (
    id, merchant_id, user_id, review_type, rating, taste_rating,
    environment_rating, service_rating, average_spend, consumption_date,
    content, source, idempotency_key, current_version, status,
    moderation_status, risk_level, published_at, created_at, updated_at
) VALUES
(20022, 8, NULL, 'ORIGINAL', 1, 2, 1, 1, 88, DATE '2026-07-24',
 '消费者维权太难了！这家店态度恶劣，老板直接骂人还恐吓说让我们出不了门。',
 'USER', 'SENS-DEMO-022', 1, 'PUBLISHED', 'APPROVED', 'LOW',
 NOW() - interval '2 hours', NOW() - interval '2 hours', NOW() - interval '2 hours'),
(20023, 8, NULL, 'ORIGINAL', 1, 2, 2, 1, 95, DATE '2026-07-24',
 '发生了严重纠纷，服务员和顾客争吵动手。已经报警了，太恶劣了！',
 'USER', 'SENS-DEMO-023', 1, 'PUBLISHED', 'APPROVED', 'LOW',
 NOW() - interval '3 hours', NOW() - interval '3 hours', NOW() - interval '3 hours'),
(20024, 8, NULL, 'ORIGINAL', 1, 3, 2, 1, 78, DATE '2026-07-24',
 '强制消费没人管？强行加收服务费还不提前告知。我要向食药监投诉他们。',
 'USER', 'SENS-DEMO-024', 1, 'PUBLISHED', 'APPROVED', 'LOW',
 NOW() - interval '4 hours', NOW() - interval '4 hours', NOW() - interval '4 hours'),
(20025, 8, NULL, 'ORIGINAL', 1, 2, 1, 1, 102, DATE '2026-07-24',
 '歧视顾客！对讲方言的客人区别对待。被多收钱还不给退款。投诉到底。',
 'USER', 'SENS-DEMO-025', 1, 'PUBLISHED', 'APPROVED', 'LOW',
 NOW() - interval '1 hour', NOW() - interval '1 hour', NOW() - interval '1 hour');
