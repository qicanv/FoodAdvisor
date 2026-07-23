"""
语义检索服务 — 查询向量化 + OpenSearch k-NN 检索 + Rerank 重排序

流程：
1. 将用户查询文本通过 BGE 模型转为 768 维查询向量（带指令前缀）
2. 在 OpenSearch 中执行 k-NN 向量检索（rerank 时多取 N 倍候选）
3. 应用过滤条件（isActive=true + 可选的 merchantIds/sourceTypes）
4. （可选）BGE Reranker Cross-Encoder 重排序候选结果
5. 返回 Top-K 相关文档及相似度分数
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
    """语义检索服务（含可选 Rerank 重排序）"""

    def __init__(self):
        self._client: OpenSearch | None = None
        self._embedding = get_embedding_service()
        self._index_name = settings.knowledge_index_name
        self._reranker: object | None = None   # None=未初始化, False=不可用
        self._reranker_resolved: bool = False

    @property
    def client(self) -> OpenSearch:
        if self._client is None:
            self._client = create_opensearch_client()
        return self._client

    @property
    def reranker(self):
        """懒加载 reranker 服务。返回 RerankerService 或 None。"""
        if self._reranker_resolved:
            return self._reranker if self._reranker is not False else None

        self._reranker_resolved = True
        from app.services.reranker_service import get_reranker_service

        self._reranker = get_reranker_service() or False
        if self._reranker is False:
            return None
        return self._reranker

    # ============================================
    # 主入口
    # ============================================

    def search(self, request: SearchRequest) -> SearchResponse:
        """
        执行语义检索。reranker 可用时自动启用 Cross-Encoder 重排序。
        """
        # Step 0: 确定从 OpenSearch 取多少候选（rerank 时多取）
        reranker = self.reranker
        if reranker is not None:
            fetch_k = max(
                request.topK * settings.reranker_fetch_multiplier,
                min(request.topK * 2, 20),
            )
            fetch_k = min(fetch_k, 100)  # 不超过 SearchRequest.topK 上限
        else:
            fetch_k = None

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
            fetch_k=fetch_k,
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
                status="FAILED",
                data=SearchData(
                    searchMode="VECTOR",
                    results=[],
                ),
            )

        # Step 4: 解析原始结果
        raw_results: list[dict] = []
        for hit in response["hits"]["hits"]:
            source = hit["_source"]
            raw_results.append({
                "documentId": source.get("documentId", ""),
                "chunkId": source.get("chunkId", hit["_id"]),
                "merchantId": source.get("merchantId", 0),
                "sourceType": source.get("sourceType", ""),
                "sourceId": source.get("sourceId", 0),
                "text": source.get("text", ""),
                "vectorScore": round(hit["_score"], 4),
                "updatedAt": source.get("updatedAt"),
            })

        search_mode = "VECTOR"

        # Step 4.5: Rerank 重排序（可选）
        if reranker is not None and len(raw_results) > request.topK:
            try:
                documents = [r["text"] for r in raw_results]
                rerank_scores = reranker.compute_scores(
                    request.query, documents
                )
                for i, r in enumerate(raw_results):
                    r["rerankScore"] = round(rerank_scores[i], 4)

                # 按 rerank 分数降序排序
                raw_results.sort(
                    key=lambda r: r.get("rerankScore", 0), reverse=True
                )
                # 截取最终 topK
                raw_results = raw_results[:request.topK]
                search_mode = "RERANKED"

                logger.info(
                    "Reranker applied: candidates=%d, selected=%d",
                    len(documents),
                    len(raw_results),
                )
            except Exception as exc:
                logger.warning(
                    "Rerank failed, falling back to vector scores: %s", exc
                )
                # 降级：按原始向量分数取 topK
                raw_results.sort(
                    key=lambda r: r["vectorScore"], reverse=True
                )
                raw_results = raw_results[:request.topK]
        else:
            # reranker 不可用时，按原始向量分数取 topK
            raw_results = raw_results[:request.topK]

        # Step 5: 构建最终 SearchResultItem 列表
        results: list[SearchResultItem] = []
        for r in raw_results:
            has_rerank = "rerankScore" in r and r["rerankScore"] is not None
            results.append(SearchResultItem(
                documentId=r["documentId"],
                chunkId=r["chunkId"],
                merchantId=r["merchantId"],
                sourceType=r["sourceType"],
                sourceId=r["sourceId"],
                text=r["text"],
                score=r["rerankScore"] if has_rerank else r["vectorScore"],
                rerankScore=r.get("rerankScore"),
                updatedAt=r.get("updatedAt"),
            ))

        logger.info(
            "Search: query='%s', topK=%d, returned=%d, mode=%s",
            f"<redacted:length={len(request.query)}>",
            request.topK,
            len(results),
            search_mode,
        )

        return SearchResponse(
            requestId=request.requestId,
            status="SUCCESS",
            data=SearchData(
                searchMode=search_mode,
                results=results,
            ),
        )


# ============================================
# 查询构建
# ============================================


def _build_knn_query(
    query_vector: list[float],
    top_k: int,
    fetch_k: int | None = None,
    filters=None,
) -> dict:
    """
    构建 OpenSearch k-NN 查询。

    OpenSearch 2.x 的 knn 查询语法：
    - 在 query.knn 中指定向量字段、向量值和 k
    - filter 用于限定 isActive=true 及自定义过滤条件
    - fetch_k 为 None 时使用 top_k；非 None 时从 OpenSearch 多取候选供 rerank

    Args:
        query_vector: 查询向量
        top_k: 最终返回数量
        fetch_k: OpenSearch 实际检索数量（None 时 = top_k）
        filters: 过滤条件
    """
    effective_k = fetch_k if fetch_k is not None else top_k

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
        "size": effective_k,
        "_source": {
            "exclude": ["embedding"]  # 不需要返回向量，减小响应体积
        },
        "query": {
            "knn": {
                "embedding": {
                    "vector": query_vector,
                    "k": effective_k,
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
