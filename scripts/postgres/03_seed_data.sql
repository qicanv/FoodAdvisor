-- ============================================
-- FoodAdvisor 种子数据 V0.2
-- 3用户 + 5商家 + 营业时间 + 13菜品 + 35条评价 + 标签字典
-- ============================================

-- ============================================
-- 1. 测试用户（密码都是 "123456" 的 BCrypt 哈希）
-- ============================================
INSERT INTO users (id, username, password_hash, nickname, email, phone, role, status) VALUES
(1, 'admin',    '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', '管理员',   'admin@foodadvisor.com',   '13800000001', 'ADMIN',    'ACTIVE'),
(2, 'merchant', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', '商家用户', 'merchant@foodadvisor.com', '13800000002', 'MERCHANT', 'ACTIVE'),
(3, 'demo',     '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', '测试用户', 'demo@foodadvisor.com',     '13800000003', 'USER',     'ACTIVE')
ON CONFLICT (username) DO NOTHING;

-- ============================================
-- 2. 商家（5家）
-- ============================================
INSERT INTO merchants (id, merchant_code, name, category, cuisine, rating, average_price, review_count, address, region_code, longitude, latitude, phone, description, environment_tags, platform_status, operation_status) VALUES
(1, 'M000001', '川味小馆',   '川菜',   '川菜', 4.7, 78.00,  9, '成都市锦江区春熙路88号',          'REGION-001', 104.081534, 30.655823, '028-88880001',
 '一家主打传统川菜的餐厅，招牌菜有麻婆豆腐、水煮鱼和回锅肉。店内装修走的是新中式风格，环境舒适。',
 '["朋友聚会","家庭聚餐","环境舒适","新中式"]', 'ACTIVE', 'OPERATING'),

(2, 'M000002', '粤鲜坊',     '粤菜',   '粤菜', 4.5, 120.00, 8, '广州市天河区体育西路168号',        'REGION-002', 113.321335, 23.129163, '020-88880002',
 '专注正宗粤菜和精致早茶，由资深粤菜大厨主理。店内环境优雅，设有包间。',
 '["商务宴请","家庭聚会","早茶","包间","环境优雅"]', 'ACTIVE', 'OPERATING'),

(3, 'M000003', '深夜烧烤王', '烧烤',   '烧烤', 4.3, 60.00,  6, '北京市朝阳区三里屯路55号',          'REGION-003', 116.455147, 39.932569, '010-88880003',
 '营业至凌晨两点的深夜烧烤店，主打东北风味炭火烧烤。店面不大但氛围热闹。',
 '["夜宵","热闹氛围","朋友聚会","深夜营业"]', 'ACTIVE', 'OPERATING'),

(4, 'M000004', '绿意轻食',   '轻食沙拉','轻食', 4.6, 45.00,  6, '上海市静安区南京西路1266号',        'REGION-004', 121.447935, 31.229511, '021-88880004',
 '主打健康轻食和创意沙拉，选用新鲜有机蔬菜。简约清新的装修风格。',
 '["独自用餐","健康轻食","简约清新","快速简餐"]', 'ACTIVE', 'OPERATING'),

(5, 'M000005', '和风居酒屋', '日料',   '日料', 4.8, 150.00, 6, '杭州市西湖区龙井路8号',            'REGION-005', 120.129723, 30.241901, '0571-88880005',
 '一家氛围温馨的日式居酒屋，提供新鲜刺身、烤物和各类日本酒。榻榻米和木质装修。',
 '["情侣约会","朋友小聚","日式风格","榻榻米","安静舒适"]', 'ACTIVE', 'OPERATING')
ON CONFLICT (merchant_code) DO NOTHING;

-- ============================================
-- 3. 商家成员
-- ============================================
INSERT INTO merchant_members (merchant_id, user_id, member_role, status) VALUES
(1, 2, 'OWNER', 'ACTIVE'),
(2, 2, 'MANAGER', 'ACTIVE')
ON CONFLICT (merchant_id, user_id) DO NOTHING;

-- ============================================
-- 4. 商家营业时间（默认 10:00-22:00，烧烤店 17:00-02:00）
-- ============================================
-- 川味小馆
INSERT INTO merchant_business_hours (merchant_id, day_of_week, open_time, close_time, is_closed, crosses_midnight) VALUES
(1, 1, '10:00', '22:00', false, false),
(1, 2, '10:00', '22:00', false, false),
(1, 3, '10:00', '22:00', false, false),
(1, 4, '10:00', '22:00', false, false),
(1, 5, '10:00', '22:00', false, false),
(1, 6, '10:00', '22:00', false, false),
(1, 7, '10:00', '22:00', false, false)
ON CONFLICT DO NOTHING;

-- 粤鲜坊 (08:00-22:00)
INSERT INTO merchant_business_hours (merchant_id, day_of_week, open_time, close_time, is_closed, crosses_midnight) VALUES
(2, 1, '08:00', '22:00', false, false),
(2, 2, '08:00', '22:00', false, false),
(2, 3, '08:00', '22:00', false, false),
(2, 4, '08:00', '22:00', false, false),
(2, 5, '08:00', '22:00', false, false),
(2, 6, '08:00', '22:00', false, false),
(2, 7, '08:00', '22:00', false, false)
ON CONFLICT DO NOTHING;

-- 深夜烧烤王 (17:00-02:00, 跨午夜)
INSERT INTO merchant_business_hours (merchant_id, day_of_week, open_time, close_time, is_closed, crosses_midnight) VALUES
(3, 1, '17:00', '02:00', false, true),
(3, 2, '17:00', '02:00', false, true),
(3, 3, '17:00', '02:00', false, true),
(3, 4, '17:00', '02:00', false, true),
(3, 5, '17:00', '02:00', false, true),
(3, 6, '17:00', '02:00', false, true),
(3, 7, '17:00', '02:00', false, true)
ON CONFLICT DO NOTHING;

-- 绿意轻食 (09:00-21:00)
INSERT INTO merchant_business_hours (merchant_id, day_of_week, open_time, close_time, is_closed, crosses_midnight) VALUES
(4, 1, '09:00', '21:00', false, false),
(4, 2, '09:00', '21:00', false, false),
(4, 3, '09:00', '21:00', false, false),
(4, 4, '09:00', '21:00', false, false),
(4, 5, '09:00', '21:00', false, false),
(4, 6, '09:00', '21:00', false, false),
(4, 7, '09:00', '21:00', false, false)
ON CONFLICT DO NOTHING;

-- 和风居酒屋 (11:00-23:00)
INSERT INTO merchant_business_hours (merchant_id, day_of_week, open_time, close_time, is_closed, crosses_midnight) VALUES
(5, 1, '11:00', '23:00', false, false),
(5, 2, '11:00', '23:00', false, false),
(5, 3, '11:00', '23:00', false, false),
(5, 4, '11:00', '23:00', false, false),
(5, 5, '11:00', '23:00', false, false),
(5, 6, '11:00', '23:00', false, false),
(5, 7, '11:00', '23:00', false, false)
ON CONFLICT DO NOTHING;

-- ============================================
-- 5. 菜品（13个）
-- ============================================
INSERT INTO dishes (id, merchant_id, name, price, category, description, taste_tags, recommended, status) VALUES
-- 川味小馆
(1,  1, '麻婆豆腐',   28.00, '热菜', '正宗四川麻婆豆腐，麻辣鲜香嫩烫酥',     '["麻辣","下饭"]',    true,  'ACTIVE'),
(2,  1, '水煮鱼',     68.00, '热菜', '鲜嫩鱼片配麻辣汤底，鲜香四溢',         '["麻辣","鲜香"]',    true,  'ACTIVE'),
(3,  1, '回锅肉',     38.00, '热菜', '五花肉配蒜苗豆瓣酱，肥而不腻',         '["咸香","下饭"]',    true,  'ACTIVE'),
(4,  1, '夫妻肺片',   32.00, '凉菜', '牛杂配红油酱汁，麻辣开胃',             '["麻辣","开胃"]',    false, 'ACTIVE'),
-- 粤鲜坊
(5,  2, '虾饺皇',     38.00, '点心', '鲜虾仁配薄透水晶皮，鲜甜弹牙',         '["鲜美","清淡"]',    true,  'ACTIVE'),
(6,  2, '白切鸡',     58.00, '烧腊', '清远鸡白切，皮滑肉嫩配姜葱蘸料',       '["鲜嫩","清淡"]',    true,  'ACTIVE'),
(7,  2, '蜜汁叉烧',   48.00, '烧腊', '梅花肉蜜汁烤制，外甜里嫩',             '["蜜汁","咸甜"]',    false, 'ACTIVE'),
-- 深夜烧烤王
(8,  3, '羊肉串',      5.00, '烤串', '肥瘦相间羊肉炭火烤制，外焦里嫩',       '["香辣","炭烤"]',    true,  'ACTIVE'),
(9,  3, '烤茄子',     15.00, '蔬菜', '整茄炭烤配蒜蓉酱料，软烂入味',         '["蒜香","微辣"]',    false, 'ACTIVE'),
-- 绿意轻食
(10, 4, '牛油果鸡肉沙拉', 38.00, '沙拉', '新鲜牛油果配低温慢煮鸡胸肉',        '["清新","健康"]',    true,  'ACTIVE'),
(11, 4, '冷榨混合果汁',   28.00, '饮品', '当季水果冷榨，不加水不加糖',        '["天然","清爽"]',    false, 'ACTIVE'),
-- 和风居酒屋
(12, 5, '三文鱼刺身',  88.00, '刺身', '进口三文鱼厚切，新鲜肥美',           '["鲜美","刺身"]',    true,  'ACTIVE'),
(13, 5, '烤鳗鱼',     78.00, '烤物', '活鳗蒲烧，外焦里嫩酱汁浓郁',           '["酱香","鲜美"]',    true,  'ACTIVE')
ON CONFLICT DO NOTHING;

-- ============================================
-- 6. 评论（35条，含 status / moderation_status）
-- ============================================

-- ------ 川味小馆 (9条) ------
INSERT INTO reviews (id, merchant_id, user_id, rating, content, source, review_time, status, moderation_status) VALUES
(1, 1, 3, 5.0, '味道非常正宗！麻婆豆腐特别好吃，麻辣鲜香，每次来都要点。水煮鱼的分量也很足，两个人吃完全够。', 'SYSTEM', '2026-07-01 12:30:00+08:00', 'PUBLISHED', 'APPROVED'),
(2, 1, 3, 3.5, '环境不错，装修挺有格调的，服务态度也很好。但是周末人太多了，排了将近一个小时才吃上，建议工作日来。', 'SYSTEM', '2026-07-03 19:15:00+08:00', 'PUBLISHED', 'APPROVED'),
(3, 1, 2, 5.0, '价格实惠分量足，四个朋友一起聚餐人均才七十多。回锅肉做得特别地道，是朋友聚会的好地方！', 'SYSTEM', '2026-07-05 13:00:00+08:00', 'PUBLISHED', 'APPROVED'),
(4, 1, 3, 2.0, '上菜速度太慢了！等了半个多小时才上来第一个菜，而且服务员态度冷漠，叫了好几次都没人理。味道再好也不想再来了。', 'SYSTEM', '2026-07-07 20:45:00+08:00', 'PUBLISHED', 'APPROVED'),
(5, 1, 2, 4.5, '水煮鱼做得很地道，麻辣鲜香！夫妻肺片也很开胃。就是店面小了点，人多的时候略显拥挤。', 'SYSTEM', '2026-07-09 18:30:00+08:00', 'PUBLISHED', 'APPROVED'),
(6, 1, 3, 1.5, '今天吃的麻婆豆腐太咸了，感觉盐放多了，跟之前来的时候完全不是一个水准。而且价格好像涨了，性价比不如以前。', 'SYSTEM', '2026-07-11 12:00:00+08:00', 'PUBLISHED', 'APPROVED'),
(31, 1, 2, 4.5, '最近新增了几道新菜，尝试了一下宫保虾球，味道不错！服务员也比之前热情了一些，感觉店家有在改进。', 'SYSTEM', '2026-07-13 12:30:00+08:00', 'PUBLISHED', 'APPROVED'),
(32, 1, 3, 2.0, '今天晚上去的，排队排了一个半小时！而且感觉厨师换人了？水煮鱼没以前好吃了，麻辣味不够。', 'SYSTEM', '2026-07-13 19:00:00+08:00', 'PUBLISHED', 'APPROVED'),
(33, 1, 2, 4.0, '四人套餐很划算，人均才六十多。麻婆豆腐还是一如既往的好吃，就是周末人实在太多了，建议工作日来。', 'SYSTEM', '2026-07-14 12:00:00+08:00', 'PUBLISHED', 'APPROVED');

-- ------ 粤鲜坊 (8条) ------
INSERT INTO reviews (id, merchant_id, user_id, rating, content, source, review_time, status, moderation_status) VALUES
(7, 2, 3, 5.0, '早茶品种很丰富，虾饺皇和肠粉都很好吃！虾饺皮薄馅大，虾仁很新鲜。环境也很优雅，适合带家人来。', 'SYSTEM', '2026-07-02 09:00:00+08:00', 'PUBLISHED', 'APPROVED'),
(8, 2, 3, 4.0, '环境确实优雅，包间装修很有档次，适合商务宴请。白切鸡做得很嫩，就是人均120确实有点贵，性价比一般。', 'SYSTEM', '2026-07-04 19:30:00+08:00', 'PUBLISHED', 'APPROVED'),
(9, 2, 2, 5.0, '白切鸡做得非常嫩，蘸料也很正宗！蜜汁叉烧外甜里嫩，小朋友特别喜欢吃。服务人员很专业，换盘倒茶都很及时。', 'SYSTEM', '2026-07-06 12:15:00+08:00', 'PUBLISHED', 'APPROVED'),
(10, 2, 3, 2.0, '上次去吃到了不新鲜的海鲜，清蒸鱼有明显腥味，回去拉肚子了。跟服务员反映也没给个说法，这种价位这种品控太让人失望了。', 'SYSTEM', '2026-07-08 20:00:00+08:00', 'PUBLISHED', 'APPROVED'),
(11, 2, 2, 4.5, '烧鹅烤得很好，皮脆肉嫩！就是停车不太方便，地下车库位置少，绕了好几圈才找到车位。', 'SYSTEM', '2026-07-10 18:45:00+08:00', 'PUBLISHED', 'APPROVED'),
(12, 2, 3, 1.5, '服务态度比之前差了很多，叫服务员加水叫了三次才来。而且菜品分量感觉缩水了，叉烧只有薄薄几片，跟图片差距太大。', 'SYSTEM', '2026-07-12 19:00:00+08:00', 'PUBLISHED', 'APPROVED'),
(34, 2, 3, 5.0, '周末带父母来喝早茶，点了虾饺、凤爪、金钱肚，每一样都很满意。爸妈也吃得很开心，说下次还要来。', 'SYSTEM', '2026-07-13 10:00:00+08:00', 'PUBLISHED', 'APPROVED'),
(35, 2, 2, 1.0, '在汤里发现了一根头发！！！太恶心了，跟经理投诉也只是说免了这道菜的钱。这种价位这种卫生，绝对不会再来了。', 'SYSTEM', '2026-07-14 19:30:00+08:00', 'PUBLISHED', 'APPROVED');

-- ------ 深夜烧烤王 (6条) ------
INSERT INTO reviews (id, merchant_id, user_id, rating, content, source, review_time, status, moderation_status) VALUES
(13, 3, 3, 4.5, '深夜觅食的好地方！羊肉串烤得外焦里嫩，配上一瓶冰啤酒简直完美。凌晨一点多还能吃到热乎的烧烤，太幸福了。', 'SYSTEM', '2026-07-01 23:30:00+08:00', 'PUBLISHED', 'APPROVED'),
(14, 3, 3, 5.0, '烤茄子必点！蒜蓉酱料特别香，茄子烤得软烂入味。价格也很实惠，三个人吃了一百多块就吃撑了，性价比超高。', 'SYSTEM', '2026-07-02 22:00:00+08:00', 'PUBLISHED', 'APPROVED'),
(15, 3, 2, 2.5, '味道还行，但是环境真的比较一般。地面有点油腻，桌椅也不太干净，对卫生有要求的人可能会介意。', 'SYSTEM', '2026-07-05 21:15:00+08:00', 'PUBLISHED', 'APPROVED'),
(16, 3, 3, 3.0, '排队排了四十分钟才吃上，味道中规中矩吧。羊肉串有点咸了，鸡翅烤得有点焦。可能期望值太高了，没有传说中那么好吃。', 'SYSTEM', '2026-07-08 00:30:00+08:00', 'PUBLISHED', 'APPROVED'),
(17, 3, 2, 5.0, '北京深夜食堂！老板很热情，还送了一份烤馒头。烤韭菜和烤金针菇也很赞，是朋友宵夜聚会的好去处。', 'SYSTEM', '2026-07-10 01:00:00+08:00', 'PUBLISHED', 'APPROVED'),
(18, 3, 3, 2.0, '这次体验很不好，服务员上错了菜还不承认，态度很差。烤串有的地方烤焦了有的地方还没熟，品控不稳定。', 'SYSTEM', '2026-07-11 22:45:00+08:00', 'PUBLISHED', 'APPROVED');

-- ------ 绿意轻食 (6条) ------
INSERT INTO reviews (id, merchant_id, user_id, rating, content, source, review_time, status, moderation_status) VALUES
(19, 4, 3, 5.0, '牛油果鸡肉沙拉超好吃！鸡胸肉一点都不柴，应该是低温慢煮的，很嫩。沙拉酱汁是店家自制的，酸甜适中。', 'SYSTEM', '2026-07-03 12:30:00+08:00', 'PUBLISHED', 'APPROVED'),
(20, 4, 3, 4.5, '环境很清新舒适，适合一个人安静地吃顿饭。冷榨果汁是现做的，很新鲜。就是价格略贵，一份沙拉加果汁要六七十。', 'SYSTEM', '2026-07-05 13:45:00+08:00', 'PUBLISHED', 'APPROVED'),
(21, 4, 2, 5.0, '减脂期间的天堂！食材都很新鲜，分量也够，吃完很有饱腹感但又不会觉得油腻。已经连续吃了一周了，强烈推荐给健身的朋友。', 'SYSTEM', '2026-07-07 12:00:00+08:00', 'PUBLISHED', 'APPROVED'),
(22, 4, 3, 3.0, '口味偏清淡，感觉没什么味道，可能是为了健康考虑吧。如果你习惯重口味的话可能不太适应。不过食材新鲜度确实不错。', 'SYSTEM', '2026-07-09 11:30:00+08:00', 'PUBLISHED', 'APPROVED'),
(23, 4, 2, 2.5, '出餐速度太慢了，午休时间有限，等了快二十分钟才拿到。沙拉这种东西又不需要现做现炒，这个速度确实不太能接受。', 'SYSTEM', '2026-07-10 12:15:00+08:00', 'PUBLISHED', 'APPROVED'),
(24, 4, 3, 4.0, '创意搭配挺有意思的，尝试了新品藜麦牛油果碗，口感层次丰富。就是份量比之前少了点，希望店家能保持水准。', 'SYSTEM', '2026-07-12 12:00:00+08:00', 'PUBLISHED', 'APPROVED');

-- ------ 和风居酒屋 (6条) ------
INSERT INTO reviews (id, merchant_id, user_id, rating, content, source, review_time, status, moderation_status) VALUES
(25, 5, 3, 5.0, '三文鱼刺身厚切真的太满足了！非常新鲜，入口即化。环境也很有日式风情，榻榻米座位很舒服，适合约会。', 'SYSTEM', '2026-07-02 19:00:00+08:00', 'PUBLISHED', 'APPROVED'),
(26, 5, 3, 5.0, '烤鳗鱼是招牌中的招牌！外焦里嫩，酱汁浓郁甜香，配米饭简直绝了。服务也很贴心，服务员都是蹲下来点单的，很有日式服务的感觉。', 'SYSTEM', '2026-07-04 20:30:00+08:00', 'PUBLISHED', 'APPROVED'),
(27, 5, 2, 4.0, '环境和氛围很不错，安静适合聊天。刺身拼盘种类丰富，就是价格不便宜，两个人吃了四百多。偶尔犒劳一下自己还行。', 'SYSTEM', '2026-07-06 19:45:00+08:00', 'PUBLISHED', 'APPROVED'),
(28, 5, 3, 2.5, '预约了七点的位置，到了之后说还要等二十分钟。预订的意义在哪里？而且那天空调好像坏了，榻榻米区域有点闷热。', 'SYSTEM', '2026-07-08 20:00:00+08:00', 'PUBLISHED', 'APPROVED'),
(29, 5, 2, 5.0, '和女朋友一周年纪念日在这里过的，提前跟店家说了，还特意给我们准备了小甜品和祝福卡片，太暖心了！服务真的没话说。', 'SYSTEM', '2026-07-10 19:30:00+08:00', 'PUBLISHED', 'APPROVED'),
(30, 5, 3, 3.0, '味道是不错，但是上菜节奏很慢，前菜到主菜中间等了快半小时。而且价格确实偏贵，刺身量有点少。', 'SYSTEM', '2026-07-11 20:15:00+08:00', 'PUBLISHED', 'APPROVED');

-- ============================================
-- 7. 评论标签字典（12个常用标签）
-- ============================================
INSERT INTO review_tags (code, name, category, status) VALUES
('TASTE_GOOD',        '口味好',   'TASTE',        'ACTIVE'),
('TASTE_BAD',         '口味差',   'TASTE',        'ACTIVE'),
('ENVIRONMENT_GOOD',  '环境好',   'ENVIRONMENT',  'ACTIVE'),
('ENVIRONMENT_BAD',   '环境差',   'ENVIRONMENT',  'ACTIVE'),
('SERVICE_GOOD',      '服务好',   'SERVICE',      'ACTIVE'),
('SERVICE_BAD',       '服务差',   'SERVICE',      'ACTIVE'),
('PRICE_LOW',         '价格实惠', 'PRICE',        'ACTIVE'),
('PRICE_HIGH',        '价格偏高', 'PRICE',        'ACTIVE'),
('QUEUE_LONG',        '排队久',   'QUEUE_TIME',   'ACTIVE'),
('PORTION_LARGE',     '分量足',   'PORTION',      'ACTIVE'),
('HYGIENE_BAD',       '卫生差',   'HYGIENE',      'ACTIVE'),
('SPEED_SLOW',        '上菜慢',   'SPEED',        'ACTIVE')
ON CONFLICT (code) DO NOTHING;

-- ============================================
-- 8. 更新序列号
-- ============================================
SELECT setval('users_id_seq', (SELECT COALESCE(MAX(id), 1) FROM users));
SELECT setval('merchants_id_seq', (SELECT COALESCE(MAX(id), 1) FROM merchants));
SELECT setval('dishes_id_seq', (SELECT COALESCE(MAX(id), 1) FROM dishes));
SELECT setval('reviews_id_seq', (SELECT COALESCE(MAX(id), 1) FROM reviews));
SELECT setval('review_tags_id_seq', (SELECT COALESCE(MAX(id), 1) FROM review_tags));
