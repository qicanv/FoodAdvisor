"""OpenSearch 客户端 — 连接、索引管理和文档写入"""
import logging
from typing import Optional

from opensearchpy import OpenSearch, helpers

from app.core.config import settings

logger = logging.getLogger(__name__)

# ============================================
# 连接
# ============================================


def create_opensearch_client() -> OpenSearch:
    authentication = None

    if settings.opensearch_username and settings.opensearch_password:
        authentication = (
            settings.opensearch_username,
            settings.opensearch_password,
        )

    client = OpenSearch(
        hosts=[
            {
                "host": settings.opensearch_host,
                "port": settings.opensearch_port,
            }
        ],
        http_auth=authentication,
        use_ssl=settings.opensearch_use_ssl,
        verify_certs=settings.opensearch_verify_certs,
        ssl_assert_hostname=False,
        ssl_show_warn=False,
        timeout=30,  # 向量写入可能比普通查询慢
    )

    return client


def check_opensearch_connection() -> bool:
    try:
        client = create_opensearch_client()
        return bool(client.ping())
    except Exception:
        return False


# ============================================
# 知识索引管理
# ============================================


def ensure_knowledge_index(
    client: OpenSearch,
    index_name: str,
    embedding_dim: int,
) -> bool:
    """
    确保知识索引存在。不存在则创建，已存在则校验维度一致性。

    返回 True 表示索引就绪，抛出异常表示维度不兼容。
    """
    if client.indices.exists(index=index_name):
        existing_dim = _get_index_embedding_dimension(client, index_name)
        if existing_dim is not None and existing_dim != embedding_dim:
            raise ValueError(
                f"Index '{index_name}' embedding dimension is {existing_dim}, "
                f"but current model outputs {embedding_dim}. "
                f"Please create a new index version or use a compatible model."
            )
        return True

    _create_knowledge_index(client, index_name, embedding_dim)
    return True


def _create_knowledge_index(
    client: OpenSearch,
    index_name: str,
    embedding_dim: int,
) -> None:
    """创建支持 KNN 向量检索的知识索引"""
    index_body = {
        "settings": {
            "number_of_shards": 1,
            "number_of_replicas": 0,
            "knn": True,
        },
        "mappings": {
            "properties": {
                # ---- 主键 & 去重 ----
                "chunkId": {"type": "keyword"},
                "documentId": {"type": "keyword"},
                # ---- 商家与来源 ----
                "merchantId": {"type": "integer"},
                "merchantCode": {"type": "keyword"},
                "sourceType": {"type": "keyword"},
                "sourceId": {"type": "integer"},
                "contentVersion": {"type": "integer"},
                "chunkIndex": {"type": "integer"},
                "totalChunks": {"type": "integer"},
                # ---- 文本 ----
                "text": {"type": "text", "analyzer": "standard"},
                "contentHash": {"type": "keyword"},
                # ---- 向量 ----
                "embeddingModel": {"type": "keyword"},
                "embeddingDimension": {"type": "integer"},
                "embedding": {
                    "type": "knn_vector",
                    "dimension": embedding_dim,
                    "method": {
                        "name": "hnsw",
                        "space_type": "cosinesimil",
                        "parameters": {
                            "ef_construction": 128,
                            "m": 16,
                        },
                    },
                },
                # ---- 生命周期 ----
                "isActive": {"type": "boolean"},
                "sourceTimestamp": {"type": "date"},
                "createdAt": {"type": "date"},
                "updatedAt": {"type": "date"},
            }
        },
    }

    client.indices.create(index=index_name, body=index_body)
    logger.info(
        "Created knowledge index '%s' with embedding_dim=%d",
        index_name,
        embedding_dim,
    )


def _get_index_embedding_dimension(
    client: OpenSearch,
    index_name: str,
) -> Optional[int]:
    """从索引 mapping 中读取已配置的向量维度"""
    try:
        mapping = client.indices.get_mapping(index=index_name)
        props = (
            mapping
            .get(index_name, {})
            .get("mappings", {})
            .get("properties", {})
        )
        emb = props.get("embedding", {})
        return emb.get("dimension")
    except Exception:
        logger.warning("Failed to read embedding dimension from index '%s'", index_name)
        return None


# ============================================
# 文档写入
# ============================================


def upsert_knowledge_document(
    client: OpenSearch,
    index_name: str,
    doc_id: str,
    doc_body: dict,
) -> dict:
    """
    幂等写入单条知识文档到 OpenSearch。

    Args:
        client: OpenSearch 客户端
        index_name: 索引名
        doc_id: 文档 _id（使用 chunkId 保证幂等）
        doc_body: 文档内容

    Returns:
        {"result": "created"|"updated"|"noop"}
    """
    response = client.index(
        index=index_name,
        id=doc_id,
        body=doc_body,
        refresh=False,
    )
    return {"result": response.get("result", "unknown")}


def bulk_upsert_knowledge_documents(
    client: OpenSearch,
    index_name: str,
    documents: list[tuple[str, dict]],
) -> tuple[int, int, list[dict]]:
    """
    批量幂等写入知识文档。

    Args:
        client: OpenSearch 客户端
        index_name: 索引名
        documents: [(doc_id, doc_body), ...]

    Returns:
        (success_count, error_count, errors) — errors 为失败详情列表
    """
    if not documents:
        return 0, 0, []

    actions = []
    for doc_id, doc_body in documents:
        actions.append({
            "_index": index_name,
            "_id": doc_id,
            "_source": doc_body,
        })

    success = 0
    errors = []

    try:
        success, failed_items = helpers.bulk(
            client,
            actions,
            refresh=False,
            raise_on_error=False,
            stats_only=False,
        )
        if isinstance(failed_items, list):
            for item in failed_items:
                err_detail = item.get("index", item)
                errors.append({
                    "docId": err_detail.get("_id", "unknown"),
                    "error": str(err_detail.get("error", "unknown")),
                })
    except Exception as exc:
        logger.error("Bulk upsert failed: %s", exc)
        errors.append({"docId": "batch", "error": str(exc)[:500]})

    return success, len(errors), errors


def check_document_exists(
    client: OpenSearch,
    index_name: str,
    doc_id: str,
) -> Optional[dict]:
    """检查文档是否存在，存在则返回 _source"""
    try:
        if client.exists(index=index_name, id=doc_id):
            doc = client.get(index=index_name, id=doc_id)
            return doc.get("_source")
    except Exception:
        pass
    return None


# ============================================
# 文档停用
# ============================================


def deactivate_documents(
    client: OpenSearch,
    index_name: str,
    source_type: str,
    source_ids: list[int],
) -> int:
    """
    批量停用知识文档（将 isActive 设为 false）。

    - sourceType=MERCHANT：停用 merchantId 在 source_ids 中的全部文档
    - sourceType=MERCHANT_INTRO/MENU/REVIEW：精确停用 sourceType + sourceId

    Args:
        client: OpenSearch 客户端
        index_name: 索引名
        source_type: MERCHANT / MERCHANT_INTRO / MENU / REVIEW
        source_ids: 要停用的来源 ID 列表

    Returns:
        实际更新的文档数
    """
    if source_type == "MERCHANT":
        query = {
            "bool": {
                "must": [
                    {"terms": {"merchantId": source_ids}}
                ]
            }
        }
    else:
        query = {
            "bool": {
                "must": [
                    {"term": {"sourceType": source_type}},
                    {"terms": {"sourceId": source_ids}},
                ]
            }
        }

    script = {
        "source": "ctx._source.isActive = false; ctx._source.updatedAt = params.now",
        "lang": "painless",
        "params": {
            "now": "now",
        },
    }

    try:
        response = client.update_by_query(
            index=index_name,
            body={
                "script": script,
                "query": query,
            },
            refresh=True,
        )
        updated = response.get("updated", 0)
        logger.info(
            "Deactivated %d documents: sourceType=%s, sourceIds=%s",
            updated,
            source_type,
            source_ids,
        )
        return updated
    except Exception as exc:
        logger.error(
            "Failed to deactivate documents: sourceType=%s, error=%s",
            source_type,
            exc,
        )
        raise
