"""
将 OpenStreetMap GeoJSON 数据转换为 PostgreSQL INSERT SQL。

用法:
    python scripts/convert_osm.py <geojson文件路径>

输出:
    - scripts/postgres/imports/07_osm_merchants.sql  (仅供手动导入，不参与数据库初始化)
    - scripts/postgres/07_osm_import_items.sql  (导入明细，同样自动执行)

生成的文件提交到 git 后，队友只需 docker-compose up 即可自动导入数据。
"""

import json
import sys
import os
from datetime import datetime

# ── 配置 ──────────────────────────────────────────────────
OUTPUT_SQL = "scripts/postgres/imports/07_osm_merchants.sql"
OSM_TIMESTAMP = None  # 从 GeoJSON 中读取

AMENITY_CN = {
    "restaurant": "餐厅",
    "cafe": "咖啡厅",
    "fast_food": "快餐",
    "internet_cafe": "网吧",
}

# 导入任务创建者（admin 用户，由 03_seed_data.sql 保证存在）
IMPORT_CREATED_BY = 1


def escape_sql(s: str | None) -> str:
    """将 Python 字符串转为 SQL 字符串字面量（NULL 安全）"""
    if s is None:
        return "NULL"
    # 只转义单引号；反斜杠在 standard_conforming_strings=ON（PG 默认）下是普通字符
    return "'" + s.replace("'", "''") + "'"


def build_address(props: dict) -> str:
    """从 addr:* 字段拼接地址，无数据时返回 '成都市'"""
    parts = []
    for key in ["addr:province", "addr:city", "addr:district", "addr:street", "addr:housenumber"]:
        val = props.get(key)
        if val:
            parts.append(val)
    return "".join(parts) if parts else "成都市"


def build_name(props: dict) -> str:
    """获取名称，无名称时返回 '未命名XXX'"""
    name = props.get("name")
    if name and name.strip():
        return name.strip()
    amenity = props.get("amenity", "unknown")
    cn = AMENITY_CN.get(amenity, amenity)
    return f"未命名{cn}"


def convert(geojson_path: str) -> None:
    """主转换逻辑"""
    # 读取 GeoJSON
    with open(geojson_path, "r", encoding="utf-8") as f:
        data = json.load(f)

    features = data["features"]
    total = len(features)
    global OSM_TIMESTAMP
    OSM_TIMESTAMP = data.get("timestamp", datetime.utcnow().isoformat())

    print(f"共 {total} 条 features")

    # 构建 SQL
    lines = []
    lines.append("-- ============================================")
    lines.append("-- FoodAdvisor OSM 成都餐饮数据导入")
    lines.append(f"-- 来源: OpenStreetMap (overpass-turbo)")
    lines.append(f"-- OSM 时间戳: {OSM_TIMESTAMP}")
    lines.append(f"-- 生成时间: {datetime.now().isoformat()}")
    lines.append(f"-- 共 {total} 条商家记录")
    lines.append("--")
    lines.append("-- 映射说明:")
    lines.append("--   amenity → category (餐厅/咖啡厅/快餐/网吧)")
    lines.append("--   name 缺失 → '未命名' + category")
    lines.append("--   address 缺失 → '成都市'")
    lines.append("--   cuisine / phone / description → 有则填入")
    lines.append("--   rating / average_price / region_code 等 → NULL（OSM 无此数据）")
    lines.append("-- ============================================")
    lines.append("")
    lines.append("BEGIN;")
    lines.append("")
    lines.append("-- ============================================")
    lines.append("-- 1. 导入任务记录")
    lines.append("-- ============================================")
    lines.append("INSERT INTO import_tasks "
               "(task_type, original_filename, status, total_count, success_count, failure_count, created_by, started_at, completed_at)")
    lines.append(f"VALUES ('MERCHANT', 'chengdu_restaurants.geojson', 'SUCCESS', "
               f"{total}, {total}, 0, {IMPORT_CREATED_BY}, NOW(), NOW());")
    lines.append("")

    # ── 2. 临时表 + 批量 INSERT ──────────────────────────
    lines.append("-- ============================================")
    lines.append("-- 2. 创建临时导入表")
    lines.append("-- ============================================")
    lines.append("CREATE TEMP TABLE _osm_import (")
    lines.append("    osm_id       TEXT,")
    lines.append("    name         TEXT,")
    lines.append("    category     TEXT,")
    lines.append("    cuisine      TEXT,")
    lines.append("    address      TEXT,")
    lines.append("    longitude    NUMERIC(10,6),")
    lines.append("    latitude     NUMERIC(10,6),")
    lines.append("    phone        TEXT,")
    lines.append("    description  TEXT,")
    lines.append("    raw_data     JSONB")
    lines.append(");")
    lines.append("")

    # INSERT INTO _osm_import VALUES
    lines.append("INSERT INTO _osm_import (osm_id, name, category, cuisine, address, longitude, latitude, phone, description, raw_data) VALUES")

    row_lines = []
    for i, feat in enumerate(features):
        props = feat.get("properties", {})
        geom = feat.get("geometry", {})

        osm_id = props.get("@id", feat.get("id", ""))
        name = build_name(props)
        amenity = props.get("amenity", "")
        category = AMENITY_CN.get(amenity, amenity)
        cuisine = props.get("cuisine")
        address = build_address(props)
        coords = geom.get("coordinates", [None, None])
        lon = coords[0]
        lat = coords[1]
        phone = props.get("phone")
        description = props.get("description")

        # raw_data: 完整的 properties + 省略的元数据，便于追溯
        raw = json.dumps(feat, ensure_ascii=False)

        row = (
            f"  ({escape_sql(osm_id)}, {escape_sql(name)}, {escape_sql(category)}, "
            f"{escape_sql(cuisine)}, {escape_sql(address)}, "
            f"{lon if lon is not None else 'NULL'}, {lat if lat is not None else 'NULL'}, "
            f"{escape_sql(phone)}, {escape_sql(description)}, "
            f"{escape_sql(raw)}::jsonb)"
        )
        row_lines.append(row)

        if (i + 1) % 500 == 0:
            print(f"  已处理 {i + 1}/{total}...")

    lines.append(",\n".join(row_lines) + ";")
    lines.append("")

    # ── 3. 写入 merchants 表 ──────────────────────────────
    lines.append("-- ============================================")
    lines.append("-- 3. 从临时表批量写入 merchants")
    lines.append("-- ============================================")
    lines.append("-- 修正序列值：种子数据手动指定了 id，序列未自动推进")
    lines.append("SELECT setval('merchants_id_seq', (SELECT COALESCE(MAX(id), 0) FROM merchants));")
    lines.append("")
    lines.append("INSERT INTO merchants (")
    lines.append("    merchant_code, name, category, cuisine,")
    lines.append("    rating, average_price, review_count,")
    lines.append("    address, region_code,")
    lines.append("    longitude, latitude,")
    lines.append("    phone, contact_email, description, cover_image_url,")
    lines.append("    environment_tags, platform_status, operation_status")
    lines.append(")")
    lines.append("SELECT")
    lines.append("    osm_id, name, category, cuisine,")
    lines.append("    NULL AS rating, NULL AS average_price, 0 AS review_count,")
    lines.append("    address, NULL AS region_code,")
    lines.append("    longitude, latitude,")
    lines.append("    phone, NULL AS contact_email, description, NULL AS cover_image_url,")
    lines.append("    '[]'::jsonb AS environment_tags, 'ACTIVE' AS platform_status, 'OPERATING' AS operation_status")
    lines.append("FROM _osm_import")
    lines.append("ON CONFLICT (merchant_code) DO NOTHING;")
    lines.append("")

    # ── 4. 写入 import_task_items ─────────────────────────
    lines.append("-- ============================================")
    lines.append("-- 4. 导入明细（关联 merchants.id）")
    lines.append("-- ============================================")
    lines.append("INSERT INTO import_task_items "
               "(task_id, row_no, external_key, raw_data, status, target_id)")
    lines.append("SELECT")
    lines.append("    (SELECT id FROM import_tasks WHERE original_filename = 'chengdu_restaurants.geojson' ORDER BY id DESC LIMIT 1),")
    lines.append("    ROW_NUMBER() OVER (),")
    lines.append("    o.osm_id,")
    lines.append("    o.raw_data,")
    lines.append("    'SUCCESS',")
    lines.append("    m.id")
    lines.append("FROM _osm_import o")
    lines.append("JOIN merchants m ON m.merchant_code = o.osm_id;")
    lines.append("")

    # ── 5. 清理 ───────────────────────────────────────────
    lines.append("-- ============================================")
    lines.append("-- 5. 清理临时表")
    lines.append("-- ============================================")
    lines.append("DROP TABLE _osm_import;")
    lines.append("")
    lines.append("COMMIT;")
    lines.append("")
    lines.append(f"-- 导入完成: {total} 条记录")

    # 写入文件
    script_dir = os.path.dirname(os.path.abspath(__file__))
    output_path = os.path.join(script_dir, "..", OUTPUT_SQL)
    output_path = os.path.normpath(output_path)

    with open(output_path, "w", encoding="utf-8") as f:
        f.write("\n".join(lines))

    file_size_kb = os.path.getsize(output_path) / 1024
    print(f"\n已生成: {output_path} ({file_size_kb:.1f} KB)")


if __name__ == "__main__":
    if len(sys.argv) < 2:
        print("用法: python scripts/convert_osm.py <geojson文件路径>")
        print("示例: python scripts/convert_osm.py C:\\Users\\asus\\Downloads\\chengdu_restaurants.geojson")
        sys.exit(1)

    convert(sys.argv[1])
