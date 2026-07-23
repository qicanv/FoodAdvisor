"""
POST /internal/knowledge/upsert 端到端验证脚本

验证点：
1. 清洗 + 切分测试评价
2. Embedding 生成 768 维向量
3. 写入 OpenSearch，检查文档存在
4. 重复请求返回 SKIPPED（去重）
5. 直接查询 OpenSearch 确认数据可取

用法：
    cd ai-service
    python test_knowledge_upsert.py
"""
import sys
import json
import requests
from opensearchpy import OpenSearch

# ---- 配置 ----
AI_SERVICE_URL = "http://localhost:8000"
OPENSEARCH_HOST = "localhost"
OPENSEARCH_PORT = 9200
INDEX_NAME = "foodadvisor_knowledge_v1"

# 从环境读取 token（或直接硬编码测试用）
import os
from dotenv import load_dotenv
load_dotenv(os.path.join(os.path.dirname(__file__), "..", ".env"))
INTERNAL_TOKEN = os.getenv("INTERNAL_API_TOKEN", "replace_with_a_long_random_internal_token")

HEADERS = {
    "Content-Type": "application/json",
    "X-Internal-Token": INTERNAL_TOKEN,
}

# ---- 测试数据 ----
SAMPLE_REVIEWS = [
    {
        "merchantId": 1,
        "sourceType": "REVIEW",
        "sourceId": 99901,
        "content": "这家店环境特别好，安静舒适，适合朋友聚会。菜品口味正宗，价格也很实惠，人均50块左右。2024年3月15日去的。",
    },
    {
        "merchantId": 1,
        "sourceType": "REVIEW",
        "sourceId": 99902,
        "content": "排队太久了！等了将近一小时才吃上。不过味道确实不错，水煮牛肉很好吃。服务态度一般般。",
    },
]

# ---- 工具函数 ----
def green(text): return f"[PASS] {text}"
def red(text): return f"[FAIL] {text}"
def yellow(text): return f"[SKIP] {text}"


def check(condition, label):
    if condition:
        print(f"  {green('✓')} {label}")
    else:
        print(f"  {red('✗')} {label}")
    assert condition, f"FAILED: {label}"


def main():
    print("=" * 60)
    print("POST /internal/knowledge/upsert 端到端验证")
    print("=" * 60)

    # ---- Step 1: 健康检查 ----
    print("\n[1/6] 检查 AI 服务健康状态...")
    resp = requests.get(f"{AI_SERVICE_URL}/health", timeout=30)
    check(resp.status_code == 200, f"健康检查: {resp.status_code}")
    health = resp.json()
    print(f"  Service: {health.get('service')}, Status: {health.get('status')}")

    # ---- Step 2: 清洗 + 切分 ----
    print("\n[2/6] 调用 POST /internal/content/process 清洗+切分...")
    process_body = {
        "items": SAMPLE_REVIEWS,
    }
    resp = requests.post(
        f"{AI_SERVICE_URL}/internal/content/process",
        json=process_body,
        headers=HEADERS,
        timeout=30,
    )
    check(resp.status_code == 200, f"清洗切分: HTTP {resp.status_code}")
    process_result = resp.json()
    print(f"  items={process_result['totalItems']}, "
          f"success={process_result['successCount']}, "
          f"fail={process_result['failCount']}, "
          f"chunks={process_result['totalChunks']}")

    chunks = process_result.get("chunks", [])
    check(len(chunks) > 0, f"产生了 {len(chunks)} 个文本块")

    for c in chunks:
        print(f"  chunkId={c['chunkId']}, "
              f"sourceType={c['sourceType']}, "
              f"sourceId={c['sourceId']}, "
              f"text_len={len(c['cleanedText'])}")

    # ---- Step 3: 构建 upsert 请求 ----
    print("\n[3/6] 调用 POST /internal/knowledge/upsert 向量化写入...")
    documents = []
    for c in chunks:
        documents.append({
            "chunkId": c["chunkId"],
            "merchantId": c["merchantId"],
            "sourceType": c["sourceType"],
            "sourceId": c["sourceId"],
            "contentVersion": 1,
            "chunkIndex": c["chunkIndex"],
            "totalChunks": c["totalChunks"],
            "text": c["cleanedText"],
            "sourceTimestamp": c.get("sourceTimestamp"),
        })

    resp = requests.post(
        f"{AI_SERVICE_URL}/internal/knowledge/upsert",
        json={"documents": documents},
        headers=HEADERS,
        timeout=120,  # 首次调用需加载模型，可能需要较长时间
    )
    check(resp.status_code == 200, f"向量化写入: HTTP {resp.status_code}")
    upsert_result = resp.json()
    print(f"  total={upsert_result['total']}, "
          f"success={upsert_result['successCount']}, "
          f"skipped={upsert_result['skipCount']}, "
          f"failed={upsert_result['failCount']}")

    for r in upsert_result.get("results", []):
        status_icon = green("✓") if r["status"] == "SUCCESS" else yellow("○") if r["status"] == "SKIPPED" else red("✗")
        print(f"  {status_icon} {r['chunkId']}: {r['status']}"
              + (f" — {r.get('error', '')}" if r.get("error") else ""))

    check(upsert_result["successCount"] + upsert_result["skipCount"] == upsert_result["total"],
          "全部成功或跳过")
    check(upsert_result["failCount"] == 0, "无失败记录")

    # ---- Step 4: 直接查询 OpenSearch 验证 ----
    print("\n[4/6] 直接查询 OpenSearch 验证文档...")
    os_client = OpenSearch(
        hosts=[{"host": OPENSEARCH_HOST, "port": OPENSEARCH_PORT}],
        timeout=10,
    )

    # 检查索引存在
    index_exists = os_client.indices.exists(index=INDEX_NAME)
    check(index_exists, f"索引 '{INDEX_NAME}' 存在")

    if index_exists:
        # 检查文档数量
        os_client.indices.refresh(index=INDEX_NAME)
        count_resp = os_client.count(index=INDEX_NAME)
        doc_count = count_resp.get("count", 0)
        print(f"  索引中文档数: {doc_count}")
        check(doc_count >= len(documents), f"文档数 >= {len(documents)}")

        # 读取第一条文档验证字段
        for doc in documents:
            try:
                fetched = os_client.get(index=INDEX_NAME, id=doc["chunkId"])
                source = fetched["_source"]
                break  # 只检查第一条
            except Exception:
                continue

        check(source["merchantId"] == 1, f"merchantId={source['merchantId']}")
        check(source["sourceType"] == "REVIEW", f"sourceType={source['sourceType']}")
        check(source["embeddingDimension"] == 768, f"向量维度={source['embeddingDimension']}")
        check("bge-base-zh-v1.5" in source["embeddingModel"],
              f"模型={source['embeddingModel']}")
        check(len(source["embedding"]) == 768,
              f"向量长度={len(source['embedding'])}")
        check("isActive" in source, "isActive 字段存在")
        check("text" in source, "text 字段存在")
        check("contentHash" in source, "contentHash 字段存在")
        check("createdAt" in source, "createdAt 字段存在")
        check("updatedAt" in source, "updatedAt 字段存在")
        print(f"  documentId={source['documentId']}, "
              f"embeddingDimension={source['embeddingDimension']}")

    # ---- Step 5: 重复请求验证去重 ----
    print("\n[5/6] 重复请求验证去重（相同数据再次 upsert）...")
    resp2 = requests.post(
        f"{AI_SERVICE_URL}/internal/knowledge/upsert",
        json={"documents": documents},
        headers=HEADERS,
        timeout=60,
    )
    check(resp2.status_code == 200, f"重复请求: HTTP {resp2.status_code}")
    upsert2 = resp2.json()
    print(f"  total={upsert2['total']}, "
          f"success={upsert2['successCount']}, "
          f"skipped={upsert2['skipCount']}, "
          f"failed={upsert2['failCount']}")
    check(upsert2["skipCount"] == len(documents),
          f"全部 SKIPPED({upsert2['skipCount']})，无重复写入")
    check(upsert2["successCount"] == 0, "无新增写入")

    # 文档数不变
    os_client.indices.refresh(index=INDEX_NAME)
    count_resp2 = os_client.count(index=INDEX_NAME)
    check(count_resp2["count"] == doc_count,
          f"文档数不变({count_resp2['count']} == {doc_count})")

    # ---- Step 6: 修改文本验证更新 ----
    print("\n[6/6] 修改文本内容验证更新...")
    modified_docs = []
    for doc in documents:
        mod = dict(doc)
        mod["text"] = doc["text"] + " [已更新]"
        modified_docs.append(mod)

    resp3 = requests.post(
        f"{AI_SERVICE_URL}/internal/knowledge/upsert",
        json={"documents": modified_docs},
        headers=HEADERS,
        timeout=60,
    )
    check(resp3.status_code == 200, f"修改后请求: HTTP {resp3.status_code}")
    upsert3 = resp3.json()
    print(f"  total={upsert3['total']}, "
          f"success={upsert3['successCount']}, "
          f"skipped={upsert3['skipCount']}, "
          f"failed={upsert3['failCount']}")
    check(upsert3["successCount"] == len(documents),
          f"全部 SUCCESS({upsert3['successCount']})，无跳过")
    check(upsert3["skipCount"] == 0, "无 SKIPPED")

    # 文档数仍不变（更新，非新增）
    os_client.indices.refresh(index=INDEX_NAME)
    count_resp3 = os_client.count(index=INDEX_NAME)
    check(count_resp3["count"] == doc_count,
          f"文档数不变({count_resp3['count']} == {doc_count})：更新而非新增")

    # ---- 总结 ----
    print("\n" + "=" * 60)
    print(green("✓ 全部验证通过！"))
    print("=" * 60)
    print(f"""
验证结果摘要：
  - 清洗切分：正常
  - Embedding：768 维向量生成正常
  - OpenSearch 写入：文档可查询，字段完整
  - 去重：相同内容不重复写入
  - 更新：修改内容正确覆盖
  - 索引：{INDEX_NAME}，文档数={doc_count}
    """)


if __name__ == "__main__":
    try:
        main()
    except AssertionError as e:
        print(f"\n{red('✗ 验证失败')}: {e}")
        sys.exit(1)
    except requests.ConnectionError:
        print(f"\n{red('✗ 无法连接到 AI 服务')}: {AI_SERVICE_URL}")
        print("请先启动 AI 服务: cd ai-service && uvicorn app.main:app --reload")
        sys.exit(1)
    except Exception as e:
        print(f"\n{red('✗ 异常')}: {e}")
        import traceback
        traceback.print_exc()
        sys.exit(1)
