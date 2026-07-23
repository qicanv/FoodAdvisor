"""
删除并重建 OpenSearch 知识索引。

用法:
    cd ai-service
    python rebuild_index.py

安全机制:
    - 删除前打印索引中文档数并要求确认
    - 索引会在下次知识入库时自动创建
"""
import sys
import os
from dotenv import load_dotenv

load_dotenv(os.path.join(os.path.dirname(__file__), ".env"))

from opensearchpy import OpenSearch

HOST = os.getenv("OPENSEARCH_HOST", "localhost")
PORT = int(os.getenv("OPENSEARCH_PORT", "9200"))
INDEX_NAME = os.getenv("KNOWLEDGE_INDEX_NAME", "foodadvisor_knowledge_v1")


def main():
    client = OpenSearch(
        hosts=[{"host": HOST, "port": PORT}],
        timeout=10,
    )

    # 检查连接
    if not client.ping():
        print(f"✗ 无法连接 OpenSearch ({HOST}:{PORT})")
        print("  请先启动: docker compose up -d opensearch")
        sys.exit(1)

    # 检查索引
    if not client.indices.exists(index=INDEX_NAME):
        print(f"索引 '{INDEX_NAME}' 不存在，无需删除。")
        print("下次知识入库时会自动创建。")
        return

    # 统计文档数
    count = client.count(index=INDEX_NAME).get("count", 0)
    print(f"索引 '{INDEX_NAME}' 当前有 {count} 条文档。")
    print(f"\n即将删除该索引，所有向量数据将丢失。")
    print(f"删除后需要通过后端 API 重新同步商家数据。")

    confirm = input("\n确认删除？输入 yes 继续: ")
    if confirm.strip().lower() != "yes":
        print("已取消。")
        return

    # 删除索引
    client.indices.delete(index=INDEX_NAME)
    print(f"✓ 索引 '{INDEX_NAME}' 已删除。")

    # 重新创建空索引（由 AI Service 首次 upsert 时自动创建，这里先做一个空创建）
    # 实际上不创建也行，upsert 时会调用 ensure_knowledge_index
    print(f"\n索引已清空。下一步:")
    print(f"  1. 确保 AI Service 正在运行: cd ai-service && uvicorn app.main:app --reload")
    print(f"  2. 调用后端 API 重新同步商家数据")
    print(f"  3. 或运行测试验证: python -m pytest tests/test_rag.py -v -s -k three_way")


if __name__ == "__main__":
    main()
