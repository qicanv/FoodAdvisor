"""
语义检索服务 — 查询向量化 + OpenSearch k-NN 检索

流程：
1. 将用户查询文本通过 BGE 模型转为 768 维查询向量（带指令前缀）
2. 在 OpenSearch 中执行 k-NN 向量检索
3. 应用过滤条件（isActive=true + 可选的 merchantIds/sourceTypes）
4. 返回 Top-K 相关文档及相似度分数
"""
import logging
from opensearchpy import OpenSearch

from app.clients.opensearch_client import create_opensearch_client
from app.core.config import settings
from app.schemas.search import (
    SearchRequest,
    SearchResponse,
    SearchResultItem,
    SearchData,
)
from app.services.embedding_service import get_embedding_service

logger = logging.getLogger(__name__)


class SearchService:
    """语义检索服务"""

    def __init__(self):
        self._client: OpenSearch | None = None
        self._embedding = get_embedding_service()
        self._index_name = settings.knowledge_index_name

    @property
    def client(self) -> OpenSearch:
        if self._client is None:
            self._client = create_opensearch_client()
        return self._client

    # ============================================
    # 主入口
    # ============================================

    def search(self, request: SearchRequest) -> SearchResponse:
        """
        执行语义检索。
        """
        # Step 1: 查询向量化（带 BGE 指令前缀）
        try:
            query_vector = self._embedding.encode_query(request.query)
        except Exception as exc:
            logger.error("Query embedding failed: %s", exc)
            return SearchResponse(
                requestId=request.requestId,
                status="FAILED",
                data=SearchData(
                    searchMode="VECTOR",
                    results=[],
                ),
            )

        # Step 2: 构建 OpenSearch k-NN 查询
        search_body = _build_knn_query(
            query_vector=query_vector,
            top_k=request.topK,
            filters=request.filters,
        )

        # Step 3: 执行检索
        try:
            response = self.client.search(
                index=self._index_name,
                body=search_body,
            )
        except Exception as exc:
            logger.error("OpenSearch search failed: %s", exc)
            return SearchResponse(
                requestId=request.requestId,
                status="SUCCESS",
                data=SearchData(
                    searchMode="KEYWORD_FALLBACK",
                    results=[],
                ),
            )

        # Step 4: 解析结果
        results: list[SearchResultItem] = []
        for hit in response["hits"]["hits"]:
            source = hit["_source"]
            results.append(SearchResultItem(
                documentId=source.get("documentId", ""),
                chunkId=source.get("chunkId", hit["_id"]),
                merchantId=source.get("merchantId", 0),
                sourceType=source.get("sourceType", ""),
                sourceId=source.get("sourceId", 0),
                text=source.get("text", ""),
                score=round(hit["_score"], 4),
                updatedAt=source.get("updatedAt"),
            ))

        logger.info(
            "Search: query='%s', topK=%d, returned=%d",
            f"<redacted:length={len(request.query)}>",
            request.topK,
            len(results),
        )

        return SearchResponse(
            requestId=request.requestId,
            status="SUCCESS",
            data=SearchData(
                searchMode="VECTOR",
                results=results,
            ),
        )


# ============================================
# 查询构建
# ============================================


def _build_knn_query(
    query_vector: list[float],
    top_k: int,
    filters=None,
) -> dict:
    """
    构建 OpenSearch k-NN 查询。

    OpenSearch 2.x 的 knn 查询语法：
    - 在 query.knn 中指定向量字段、向量值和 k
    - filter 用于限定 isActive=true 及自定义过滤条件
    """
    # 基础过滤：只检索活跃文档
    filter_clauses: list[dict] = [
        {"term": {"isActive": True}},
    ]

    # 用户指定的过滤条件
    if filters:
        if filters.merchantIds:
            filter_clauses.append({
                "terms": {"merchantId": filters.merchantIds}
            })
        if filters.sourceTypes:
            filter_clauses.append({
                "terms": {"sourceType": filters.sourceTypes}
            })

    return {
        "size": top_k,
        "_source": {
            "exclude": ["embedding"]  # 不需要返回向量，减小响应体积
        },
        "query": {
            "knn": {
                "embedding": {
                    "vector": query_vector,
                    "k": top_k,
                    "filter": {
                        "bool": {
                            "must": filter_clauses,
                        },
                    },
                },
            },
        },
    }


# ============================================
# 模块级单例
# ============================================
_search_service: SearchService | None = None


def get_search_service() -> SearchService:
    global _search_service
    if _search_service is None:
        _search_service = SearchService()
    return _search_service
