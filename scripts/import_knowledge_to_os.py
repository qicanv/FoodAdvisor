"""
批量导入脚本：将 PostgreSQL 中的商家知识数据导入 OpenSearch 向量库

流程：
  1. 读 PostgreSQL（reviews / merchants / dishes）
  2. 调 POST /internal/content/process 清洗+切分
  3. 调 POST /internal/knowledge/upsert 向量化+写入 OpenSearch

用法：
  conda activate intern
  cd ai-service
  python ../scripts/import_knowledge_to_os.py

前置：
  - AI 服务已启动（uvicorn app.main:app --port 8000）
  - OpenSearch 已启动
  - 根目录 .env 中已配置 POSTGRES_* 和 INTERNAL_API_TOKEN
"""
import os
import sys
import time
import logging
from datetime import datetime

import psycopg2
import psycopg2.extras
import requests
from dotenv import load_dotenv

# 加载根目录 .env
load_dotenv(os.path.join(os.path.dirname(__file__), "..", ".env"))

# ---- 配置 ----
AI_SERVICE_URL = "http://localhost:8000"
BATCH_SIZE = 200  # 每次 API 调用最多多少条
INTERNAL_TOKEN = os.getenv("INTERNAL_API_TOKEN", "")

DB_CONFIG = {
    "host": os.getenv("POSTGRES_HOST", "localhost"),
    "port": int(os.getenv("POSTGRES_PORT", "5432")),
    "dbname": os.getenv("POSTGRES_DB", "foodadvisor"),
    "user": os.getenv("POSTGRES_USER", "postgres"),
    "password": os.getenv("POSTGRES_PASSWORD", ""),
}

HEADERS = {
    "Content-Type": "application/json",
    "X-Internal-Token": INTERNAL_TOKEN,
}

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(message)s",
)
logger = logging.getLogger("import")


# ============================================
# 数据读取
# ============================================

def fetch_reviews(conn) -> list[dict]:
    """读取已发布且审核通过的评价"""
    sql = """
        SELECT id, merchant_id, content, review_time, current_version
        FROM reviews
        WHERE status = 'PUBLISHED'
          AND moderation_status = 'APPROVED'
          AND deleted_at IS NULL
          AND content IS NOT NULL
          AND trim(content) <> ''
        ORDER BY id
    """
    with conn.cursor(cursor_factory=psycopg2.extras.RealDictCursor) as cur:
        cur.execute(sql)
        rows = cur.fetchall()
    logger.info("reviews: %d 条（PUBLISHED + APPROVED）", len(rows))

    items = []
    for r in rows:
        items.append({
            "merchantId": r["merchant_id"],
            "sourceType": "REVIEW",
            "sourceId": r["id"],
            "sourceTimestamp": r["review_time"].isoformat() if r["review_time"] else None,
            "content": r["content"],
            "metadata": {"currentVersion": r["current_version"]},
        })
    return items


def fetch_merchants(conn) -> list[dict]:
    """读取活跃商家的介绍"""
    sql = """
        SELECT id, description, updated_at
        FROM merchants
        WHERE platform_status = 'ACTIVE'
          AND deleted_at IS NULL
          AND description IS NOT NULL
          AND trim(description) <> ''
        ORDER BY id
    """
    with conn.cursor(cursor_factory=psycopg2.extras.RealDictCursor) as cur:
        cur.execute(sql)
        rows = cur.fetchall()
    logger.info("merchants: %d 条（有介绍内容）", len(rows))

    items = []
    for r in rows:
        items.append({
            "merchantId": r["id"],
            "sourceType": "MERCHANT_INTRO",
            "sourceId": r["id"],
            "sourceTimestamp": r["updated_at"].isoformat() if r["updated_at"] else None,
            "content": r["description"],
            "metadata": {},
        })
    return items


def fetch_dishes(conn) -> list[dict]:
    """读取在售菜品，拼接名字、价格和描述"""
    sql = """
        SELECT id, merchant_id, name, price, description, taste_tags, updated_at
        FROM dishes
        WHERE status = 'ACTIVE'
          AND deleted_at IS NULL
        ORDER BY id
    """
    with conn.cursor(cursor_factory=psycopg2.extras.RealDictCursor) as cur:
        cur.execute(sql)
        rows = cur.fetchall()
    logger.info("dishes: %d 条", len(rows))

    items = []
    for r in rows:
        # 拼接菜品文本：名称 + 价格 + 描述 + 口味标签
        parts = [r["name"]]
        if r["price"] is not None:
            parts.append(f"¥{r['price']}")
        if r["description"] and r["description"].strip():
            parts.append(r["description"])
        if r["taste_tags"]:
            tags = r["taste_tags"]
            if isinstance(tags, list):
                parts.append("，".join(tags))

        text = " - ".join(parts)

        items.append({
            "merchantId": r["merchant_id"],
            "sourceType": "MENU",
            "sourceId": r["id"],
            "sourceTimestamp": r["updated_at"].isoformat() if r["updated_at"] else None,
            "content": text,
            "metadata": {},
        })
    return items


# ============================================
# API 调用
# ============================================

def call_process(items: list[dict]) -> dict:
    """调 POST /internal/content/process"""
    resp = requests.post(
        f"{AI_SERVICE_URL}/internal/content/process",
        json={"items": items},
        headers=HEADERS,
        timeout=300,
    )
    resp.raise_for_status()
    return resp.json()


def call_upsert(chunks: list[dict]) -> dict:
    """调 POST /internal/knowledge/upsert"""
    documents = []
    for c in chunks:
        documents.append({
            "chunkId": c["chunkId"],
            "merchantId": c["merchantId"],
            "sourceType": c["sourceType"],
            "sourceId": c["sourceId"],
            "contentVersion": c.get("metadata", {}).get("currentVersion", 1),
            "chunkIndex": c["chunkIndex"],
            "totalChunks": c["totalChunks"],
            "text": c["cleanedText"],
            "sourceTimestamp": c.get("sourceTimestamp"),
        })

    resp = requests.post(
        f"{AI_SERVICE_URL}/internal/knowledge/upsert",
        json={"documents": documents},
        headers=HEADERS,
        timeout=300,  # embedding 可能较慢
    )
    resp.raise_for_status()
    return resp.json()


# ============================================
# 主流程
# ============================================

def main():
    print("=" * 60)
    print("FoodAdvisor 知识批量导入 OpenSearch 向量库")
    print(f"时间: {datetime.now().isoformat()}")
    print("=" * 60)

    # 0. 检查 AI 服务
    print("\n[0] 检查 AI 服务...")
    try:
        resp = requests.get(f"{AI_SERVICE_URL}/health", timeout=60)
        health = resp.json()
        print(f"  AI Service: {health['status']}")
        print(f"  OpenSearch: {health['dependencies']['openSearch']}")
    except Exception as e:
        print(f"  [FAIL] 无法连接 AI 服务: {e}")
        print("  请先启动: cd ai-service && uvicorn app.main:app --host 0.0.0.0 --port 8000")
        sys.exit(1)

    # 1. 读 PostgreSQL
    print("\n[1] 读取 PostgreSQL 数据...")
    conn = psycopg2.connect(**DB_CONFIG)

    all_items = []
    all_items.extend(fetch_reviews(conn))
    all_items.extend(fetch_merchants(conn))
    all_items.extend(fetch_dishes(conn))
    conn.close()

    total_sources = len(all_items)
    print(f"  总计: {total_sources} 条（评价+商家介绍+菜品）")

    if total_sources == 0:
        print("  没有需要导入的数据，退出。")
        return

    # 2. 分批清洗+切分
    print("\n[2] 清洗+切分（POST /internal/content/process）...")

    all_chunks = []
    process_errors = []

    for i in range(0, total_sources, BATCH_SIZE):
        batch = all_items[i:i + BATCH_SIZE]
        batch_num = i // BATCH_SIZE + 1
        total_batches = (total_sources + BATCH_SIZE - 1) // BATCH_SIZE

        print(f"  批次 {batch_num}/{total_batches} ({len(batch)} 条)...", end=" ", flush=True)

        try:
            result = call_process(batch)
            chunks = result.get("chunks", [])
            errors = result.get("errors", [])
            all_chunks.extend(chunks)
            process_errors.extend(errors)
            print(f"→ {result['successCount']} 成功, {result['failCount']} 失败, {len(chunks)} 个 chunk")
        except Exception as e:
            print(f"\n  [FAIL] 清洗切分失败: {e}")
            sys.exit(1)

    print(f"\n  清洗切分完成: {len(all_chunks)} 个 chunk, {len(process_errors)} 条源数据失败")

    if process_errors:
        print("  清洗失败明细:")
        for e in process_errors:
            print(f"    sourceType={e['sourceType']}, sourceId={e['sourceId']}: {e['error']}")

    if not all_chunks:
        print("  没有可用的 chunk，退出。")
        return

    # 3. 分批向量化+写入
    print(f"\n[3] 向量化写入（POST /internal/knowledge/upsert）...")

    upsert_total = 0
    upsert_success = 0
    upsert_skipped = 0
    upsert_failed = 0
    upsert_errors = []

    for i in range(0, len(all_chunks), BATCH_SIZE):
        batch = all_chunks[i:i + BATCH_SIZE]
        batch_num = i // BATCH_SIZE + 1
        total_batches = (len(all_chunks) + BATCH_SIZE - 1) // BATCH_SIZE

        print(f"  批次 {batch_num}/{total_batches} ({len(batch)} chunks)...", end=" ", flush=True)

        try:
            result = call_upsert(batch)
            upsert_total += result["total"]
            upsert_success += result["successCount"]
            upsert_skipped += result["skipCount"]
            upsert_failed += result["failCount"]

            for r in result.get("results", []):
                if r["status"] == "FAILED":
                    upsert_errors.append(r)

            print(f"→ {result['successCount']} 写入, {result['skipCount']} 跳过, {result['failCount']} 失败")
        except Exception as e:
            print(f"\n  [FAIL] 向量化写入失败: {e}")
            sys.exit(1)

    print(f"\n  写入完成: total={upsert_total}, success={upsert_success}, skipped={upsert_skipped}, failed={upsert_failed}")

    if upsert_errors:
        print("  写入失败明细:")
        for e in upsert_errors[:10]:
            print(f"    {e['chunkId']}: {e.get('error', 'unknown')}")
        if len(upsert_errors) > 10:
            print(f"    ... 共 {len(upsert_errors)} 条失败")

    # 4. 汇总
    print("\n" + "=" * 60)
    print("导入完成！")
    print(f"  源数据: {total_sources} 条")
    print(f"  清洗失败: {len(process_errors)} 条")
    print(f"  Chunk 数: {len(all_chunks)} 个")
    print(f"  写入成功: {upsert_success} 个")
    print(f"  跳过(重复): {upsert_skipped} 个")
    print(f"  写入失败: {upsert_failed} 个")
    print(f"  OpenSearch 索引: foodadvisor_knowledge_v1")
    print("=" * 60)


if __name__ == "__main__":
    main()
