"""
从 PostgreSQL 读取所有活跃商家，增强文本后直接调用 AI Service 内部方法向量化入库。

不经过 HTTP，绕过 Windows 代理问题。

用法:
    cd ai-service
    python sync_all_merchants.py
"""
import sys
import os
import json
import asyncio
import psycopg2
from dotenv import load_dotenv

load_dotenv(os.path.join(os.path.dirname(__file__), ".env"))
load_dotenv(os.path.join(os.path.dirname(__file__), "..", ".env"))

# ---- 配置 ----
PG_HOST = os.getenv("PG_HOST", "localhost")
PG_PORT = int(os.getenv("PG_PORT", "5433"))
PG_DB = os.getenv("PG_DATABASE", "foodadvisor")
PG_USER = os.getenv("POSTGRES_USER", "postgres")
PG_PASS = os.getenv("POSTGRES_PASSWORD", "")


async def main():
    # 初始化 AI Service 内部组件（懒加载）
    from app.services.content_processing_service import content_processing_service
    from app.services.knowledge_service import get_knowledge_service
    from app.schemas.content_processing import (
        ProcessRequest, ContentItem, SourceTypeEnum, ChunkConfig, CleanConfig,
    )
    from app.schemas.knowledge import KnowledgeUpsertRequest, KnowledgeDocument

    print("AI Service 内部服务已加载")

    # 连接数据库
    try:
        conn = psycopg2.connect(
            host=PG_HOST, port=PG_PORT, dbname=PG_DB,
            user=PG_USER, password=PG_PASS
        )
        conn.autocommit = True
        print(f"PostgreSQL: 已连接 {PG_HOST}:{PG_PORT}/{PG_DB}")
    except Exception as e:
        print(f"无法连接 PostgreSQL: {e}")
        sys.exit(1)

    cur = conn.cursor()

    # 查询所有活跃商家
    cur.execute("""
        SELECT id, name, category, cuisine, environment_tags, description
        FROM merchants
        WHERE platform_status = 'ACTIVE' AND deleted_at IS NULL
        ORDER BY id
    """)
    merchants = cur.fetchall()
    print(f"活跃商家: {len(merchants)} 家\n")

    if not merchants:
        print("无活跃商家，无需同步。")
        cur.close()
        conn.close()
        return

    total_chunks = 0
    synced = 0
    failed = 0
    knowledge = get_knowledge_service()

    for m_id, name, category, cuisine, env_tags, desc in merchants:
        try:
            print(f"[{m_id}] {name} ...", end=" ", flush=True)

            items = []

            # ---- 1. MERCHANT_INTRO ----
            intro_text = build_intro_text(name, category, cuisine, env_tags, desc or "")
            items.append(ContentItem(
                merchantId=m_id,
                sourceType=SourceTypeEnum.MERCHANT_INTRO,
                sourceId=m_id,
                content=intro_text,
            ))

            # ---- 2. MENU ----
            cur.execute("""
                SELECT id, name, category, taste_tags, price, description
                FROM dishes
                WHERE merchant_id = %s AND status = 'ACTIVE' AND deleted_at IS NULL
            """, (m_id,))
            dishes = cur.fetchall()
            for d_id, d_name, d_cat, d_tags, d_price, d_desc in dishes:
                dish_text = build_dish_text(d_name, d_cat, d_tags, d_price, d_desc or "")
                items.append(ContentItem(
                    merchantId=m_id,
                    sourceType=SourceTypeEnum.MENU,
                    sourceId=d_id,
                    content=dish_text,
                ))

            # ---- 3. REVIEWS ----
            cur.execute("""
                SELECT id, rating, content
                FROM reviews
                WHERE merchant_id = %s AND status = 'PUBLISHED' AND moderation_status = 'APPROVED' AND deleted_at IS NULL
            """, (m_id,))
            reviews = cur.fetchall()
            for r_id, rating, content in reviews:
                review_text = build_review_text(rating, content or "")
                items.append(ContentItem(
                    merchantId=m_id,
                    sourceType=SourceTypeEnum.REVIEW,
                    sourceId=r_id,
                    content=review_text,
                ))

            # ---- 清洗切分 ----
            process_req = ProcessRequest(
                items=items,
                cleanConfig=CleanConfig(),
                chunkConfig=ChunkConfig(),
            )
            process_result = content_processing_service.process(process_req)

            if not process_result.chunks:
                print(f"✓ 0 chunks (无内容)")
                synced += 1
                continue

            # ---- 向量化 + 写入 OpenSearch ----
            documents = []
            for c in process_result.chunks:
                documents.append(KnowledgeDocument(
                    chunkId=c.chunkId,
                    merchantId=c.merchantId,
                    sourceType=c.sourceType.value,
                    sourceId=c.sourceId,
                    contentVersion=1,
                    chunkIndex=c.chunkIndex,
                    totalChunks=c.totalChunks,
                    text=c.cleanedText,
                    sourceTimestamp=c.sourceTimestamp,
                ))

            upsert_req = KnowledgeUpsertRequest(documents=documents)
            upsert_result = knowledge.upsert(upsert_req)

            chunks = len(documents)
            total_chunks += chunks
            synced += 1
            intro_count = sum(1 for c in process_result.chunks if c.sourceType == SourceTypeEnum.MERCHANT_INTRO)
            menu_count = sum(1 for c in process_result.chunks if c.sourceType == SourceTypeEnum.MENU)
            review_count = sum(1 for c in process_result.chunks if c.sourceType == SourceTypeEnum.REVIEW)
            print(f"✓ {chunks} chunks (intro={intro_count}, menu={menu_count}, review={review_count})")

        except Exception as e:
            failed += 1
            print(f"✗ 失败: {e}")

    cur.close()
    conn.close()

    print(f"\n{'=' * 50}")
    print(f"完成: synced={synced}, failed={failed}, total_chunks={total_chunks}")


# ================================================================
# 文本构造
# ================================================================

def build_intro_text(name, category, cuisine, env_tags_json, description):
    parts = [name]
    if category:
        parts.append(f"类型: {category}")
    if cuisine:
        parts.append(f"菜系: {cuisine}")
    tags = parse_json_array(env_tags_json)
    if tags:
        parts.append(f"环境标签: {'、'.join(tags)}")
    if description:
        parts.append(description)
    return "。".join(parts)


def build_dish_text(name, category, taste_tags_json, price, description):
    parts = [name]
    if category:
        parts.append(f"分类: {category}")
    tags = parse_json_array(taste_tags_json)
    if tags:
        parts.append(f"口味: {'、'.join(tags)}")
    if price is not None:
        parts.append(f"价格: ¥{price}")
    if description:
        parts.append(description)
    return "，".join(parts)


def build_review_text(rating, content):
    if rating and float(rating) > 0:
        return f"评分: {rating}/5。{content}"
    return content


def parse_json_array(json_str):
    if not json_str:
        return []
    try:
        return json.loads(json_str) if isinstance(json_str, str) else json_str
    except (json.JSONDecodeError, TypeError):
        return []


if __name__ == "__main__":
    asyncio.run(main())
