-- Password for every demo account: Demo@123456
INSERT INTO users (
    id, username, password_hash, nickname, email, phone, role, status,
    created_at, updated_at
) VALUES
(1, 'demo_admin', '$2a$10$Gbdx0A6ZGPTgMXKzDJkOyOCgnF5Gs1LyT3PoR7PnQPZvD8312/7p6', '演示管理员', 'admin@example.invalid', '19900000001', 'ADMIN', 'ACTIVE', '2026-01-01 08:00+08', '2026-01-01 08:00+08'),
(2, 'demo_diner_1', '$2a$10$Gbdx0A6ZGPTgMXKzDJkOyOCgnF5Gs1LyT3PoR7PnQPZvD8312/7p6', '虚构食客甲', 'diner1@example.invalid', '19900000002', 'USER', 'ACTIVE', '2026-01-01 08:00+08', '2026-01-01 08:00+08'),
(3, 'demo_diner_2', '$2a$10$Gbdx0A6ZGPTgMXKzDJkOyOCgnF5Gs1LyT3PoR7PnQPZvD8312/7p6', '虚构食客乙', 'diner2@example.invalid', '19900000003', 'USER', 'ACTIVE', '2026-01-01 08:00+08', '2026-01-01 08:00+08'),
(4, 'demo_diner_3', '$2a$10$Gbdx0A6ZGPTgMXKzDJkOyOCgnF5Gs1LyT3PoR7PnQPZvD8312/7p6', '虚构食客丙', 'diner3@example.invalid', '19900000004', 'USER', 'ACTIVE', '2026-01-01 08:00+08', '2026-01-01 08:00+08'),
(5, 'demo_diner_4', '$2a$10$Gbdx0A6ZGPTgMXKzDJkOyOCgnF5Gs1LyT3PoR7PnQPZvD8312/7p6', '虚构食客丁', 'diner4@example.invalid', '19900000005', 'USER', 'ACTIVE', '2026-01-01 08:00+08', '2026-01-01 08:00+08'),
(6, 'demo_merchant_1', '$2a$10$Gbdx0A6ZGPTgMXKzDJkOyOCgnF5Gs1LyT3PoR7PnQPZvD8312/7p6', '虚构商家甲', 'merchant1@example.invalid', '19900000006', 'MERCHANT', 'ACTIVE', '2026-01-01 08:00+08', '2026-01-01 08:00+08'),
(7, 'demo_merchant_2', '$2a$10$Gbdx0A6ZGPTgMXKzDJkOyOCgnF5Gs1LyT3PoR7PnQPZvD8312/7p6', '虚构商家乙', 'merchant2@example.invalid', '19900000007', 'MERCHANT', 'ACTIVE', '2026-01-01 08:00+08', '2026-01-01 08:00+08'),
(8, 'demo_merchant_3', '$2a$10$Gbdx0A6ZGPTgMXKzDJkOyOCgnF5Gs1LyT3PoR7PnQPZvD8312/7p6', '虚构商家丙', 'merchant3@example.invalid', '19900000008', 'MERCHANT', 'ACTIVE', '2026-01-01 08:00+08', '2026-01-01 08:00+08'),
(9, 'demo_merchant_4', '$2a$10$Gbdx0A6ZGPTgMXKzDJkOyOCgnF5Gs1LyT3PoR7PnQPZvD8312/7p6', '虚构商家丁', 'merchant4@example.invalid', '19900000009', 'MERCHANT', 'ACTIVE', '2026-01-01 08:00+08', '2026-01-01 08:00+08'),
(10, 'demo_disabled', '$2a$10$Gbdx0A6ZGPTgMXKzDJkOyOCgnF5Gs1LyT3PoR7PnQPZvD8312/7p6', '停用演示账号', 'disabled@example.invalid', '19900000010', 'USER', 'DISABLED', '2026-01-01 08:00+08', '2026-01-01 08:00+08');

WITH regions(code, label, base_lon, base_lat) AS (VALUES
 ('CD-JJ','锦江区',104.080000,30.650000), ('CD-QY','青羊区',104.060000,30.675000),
 ('CD-WH','武侯区',104.040000,30.630000), ('CD-CH','成华区',104.110000,30.670000),
 ('CD-JN','金牛区',104.055000,30.700000), ('CD-GX','高新区',104.070000,30.560000)
), cuisines(name, category) AS (VALUES
 ('川菜','中餐'),('火锅','火锅'),('粤菜','中餐'),('咖啡甜品','休闲餐饮')
)
INSERT INTO merchants (
 id, merchant_code, name, category, cuisine, average_price, address,
 region_code, longitude, latitude, description, environment_tags,
 platform_status, operation_status, created_at, updated_at
)
SELECT row_number() OVER (ORDER BY r.code,c.cidx), 'DEMO-M-' || lpad(row_number() OVER (ORDER BY r.code,c.cidx)::text, 3, '0'),
       r.label || c.name || '演示馆' || row_number() OVER (ORDER BY r.code,c.cidx), c.category, c.name,
       28 + row_number() OVER (ORDER BY r.code,c.cidx) * 4,
       '成都市' || r.label || '演示路 ' || row_number() OVER (ORDER BY r.code,c.cidx) || ' 号',
       r.code, r.base_lon + cidx * 0.002, r.base_lat + cidx * 0.002,
       '完全虚构的本地演示商家，不对应任何现实门店。',
       CASE WHEN cidx % 2 = 0 THEN '["安静","适合聚餐"]'::jsonb ELSE '["明亮","交通便利"]'::jsonb END,
       'ACTIVE',
       CASE WHEN row_number() OVER (ORDER BY r.code,c.cidx) = 23 THEN 'SUSPENDED'
            WHEN row_number() OVER (ORDER BY r.code,c.cidx) = 24 THEN 'CLOSED_PERMANENTLY'
            ELSE 'OPERATING' END,
       '2026-01-02 09:00+08', '2026-01-02 09:00+08'
FROM regions r CROSS JOIN LATERAL
 (SELECT name, category, row_number() OVER () AS cidx FROM cuisines) c;

INSERT INTO merchant_members (merchant_id, user_id, member_role, status, created_at, updated_at)
SELECT id, 6 + ((id - 1) % 4), 'OWNER', 'ACTIVE', '2026-01-02 10:00+08', '2026-01-02 10:00+08'
FROM merchants;

INSERT INTO merchant_business_hours (
 merchant_id, day_of_week, open_time, close_time, is_closed,
 crosses_midnight, created_at, updated_at
)
SELECT m.id, d, '10:00'::time, '22:00'::time, false, false,
       '2026-01-02 10:00+08', '2026-01-02 10:00+08'
FROM merchants m CROSS JOIN generate_series(1, 7) d;

INSERT INTO dishes (
 id, merchant_id, name, price, category, description, taste_tags,
 recommended, status, created_at, updated_at
)
SELECT (m.id - 1) * 4 + d, m.id,
       m.cuisine || CASE d WHEN 1 THEN '招牌一号' WHEN 2 THEN '时蔬小份'
                           WHEN 3 THEN '双人分享' ELSE '演示饮品' END,
       m.average_price * (0.25 + d * 0.12), CASE WHEN d = 4 THEN '饮品' ELSE '主菜' END,
       '虚构演示菜品', '["演示","本地素材为空"]'::jsonb, d = 1,
       CASE WHEN d = 4 AND m.id % 5 = 0 THEN 'OFF_SHELF' ELSE 'ACTIVE' END,
       '2026-01-03 09:00+08', '2026-01-03 09:00+08'
FROM merchants m CROSS JOIN generate_series(1, 4) d;
