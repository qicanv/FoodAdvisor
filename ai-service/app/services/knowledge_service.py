"""
知识向量化与存储服务

编排流程：
1. 接收清洗后的 ContentChunk → 计算文本哈希去重
2. 调用 EmbeddingService 批量生成向量
3. 写入 OpenSearch（以 chunkId 为 _id，幂等 upsert）
4. 维度校验、单条失败隔离、SKIPPED/SUCCESS/FAILED 分别记录
"""
import hashlib
import logging
from datetime import datetime, timezone

from opensearchpy import OpenSearch

from app.clients.opensearch_client import (
    create_opensearch_client,
    ensure_knowledge_index,
    check_document_exists,
    upsert_knowledge_document,
)
from app.core.config import settings
from app.schemas.knowledge import (
    KnowledgeDocument,
    KnowledgeUpsertRequest,
    KnowledgeUpsertResponse,
    KnowledgeDocumentResult,
)
from app.services.embedding_service import get_embedding_service

logger = logging.getLogger(__name__)


class KnowledgeService:
    """知识向量化与 OpenSearch 写入服务"""

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

    def upsert(self, request: KnowledgeUpsertRequest) -> KnowledgeUpsertResponse:
        """
        批量写入知识文档到 OpenSearch。

        流程：
        1. 确保索引存在并校验维度
        2. 逐条检查 chunkId 是否已有相同内容 → 跳过
        3. 对新增/变更的文档批量 embedding
        4. 批量写入 OpenSearch
        5. 返回每条结果
        """
        documents = request.documents

        # ---- Step 1: 确保索引就绪 ----
        try:
            ensure_knowledge_index(
                self.client,
                self._index_name,
                self._embedding.dimension,
            )
        except ValueError as exc:
            logger.error("Index dimension mismatch: %s", exc)
            return KnowledgeUpsertResponse(
                requestId=request.requestId,
                total=len(documents),
                failCount=len(documents),
                results=[
                    KnowledgeDocumentResult(
                        chunkId=doc.chunkId,
                        documentId=_make_document_id(doc),
                        status="FAILED",
                        error=str(exc)[:500],
                    )
                    for doc in documents
                ],
            )

        # ---- Step 2: 去重检查 ----
        to_embed: list[KnowledgeDocument] = []
        results: list[KnowledgeDocumentResult] = []

        for doc in documents:
            content_hash = _hash_text(doc.text)

            try:
                existing = check_document_exists(
                    self.client, self._index_name, doc.chunkId
                )
            except Exception:
                existing = None

            if existing and existing.get("contentHash") == content_hash:
                # 内容未变化，跳过
                results.append(KnowledgeDocumentResult(
                    chunkId=doc.chunkId,
                    documentId=_make_document_id(doc),
                    status="SKIPPED",
                ))
            else:
                to_embed.append(doc)

        skip_count = len(results)
        logger.info(
            "Upsert: total=%d, skipped=%d, to_embed=%d",
            len(documents),
            skip_count,
            len(to_embed),
        )

        # ---- Step 3: 批量 Embedding ----
        if to_embed:
            embed_results = self._embed_and_write(request.requestId, to_embed)
            results.extend(embed_results)
        else:
            embed_results = []

        # 统计
        success_count = sum(1 for r in embed_results if r.status == "SUCCESS")
        fail_count = sum(1 for r in embed_results if r.status == "FAILED")

        return KnowledgeUpsertResponse(
            requestId=request.requestId,
            total=len(documents),
            successCount=success_count,
            skipCount=skip_count,
            failCount=fail_count,
            results=results,
        )

    # ============================================
    # 内部：Embedding + 写入
    # ============================================

    def _embed_and_write(
        self,
        request_id: str | None,
        documents: list[KnowledgeDocument],
    ) -> list[KnowledgeDocumentResult]:
        """批量 embedding 后逐条写入 OpenSearch"""

        # ---- 批量生成向量 ----
        texts = [doc.text for doc in documents]
        batch_size = settings.embedding_batch_size

        try:
            vectors = self._embedding.encode(texts, batch_size=batch_size)
        except Exception as exc:
            logger.error("Batch embedding failed: %s", exc, exc_info=True)
            # 全部标记失败
            return [
                KnowledgeDocumentResult(
                    chunkId=doc.chunkId,
                    documentId=_make_document_id(doc),
                    status="FAILED",
                    error=f"Embedding failed: {str(exc)[:480]}",
                )
                for doc in documents
            ]

        if len(vectors) != len(documents):
            logger.error(
                "Vector count mismatch: %d vectors for %d documents",
                len(vectors),
                len(documents),
            )
            return [
                KnowledgeDocumentResult(
                    chunkId=doc.chunkId,
                    documentId=_make_document_id(doc),
                    status="FAILED",
                    error="Vector count mismatch",
                )
                for doc in documents
            ]

        # ---- 逐条写入 ----
        results: list[KnowledgeDocumentResult] = []
        now = datetime.now(timezone.utc).isoformat()

        for doc, vector in zip(documents, vectors):
            try:
                content_hash = _hash_text(doc.text)

                doc_body = {
                    "chunkId": doc.chunkId,
                    "documentId": _make_document_id(doc),
                    "merchantId": doc.merchantId,
                    "sourceType": doc.sourceType,
                    "sourceId": doc.sourceId,
                    "contentVersion": doc.contentVersion,
                    "chunkIndex": doc.chunkIndex,
                    "totalChunks": doc.totalChunks,
                    "text": doc.text,
                    "contentHash": content_hash,
                    "embeddingModel": self._embedding.model_identifier,
                    "embeddingDimension": self._embedding.dimension,
                    "embedding": vector,
                    "isActive": True,
                    "sourceTimestamp": doc.sourceTimestamp,
                    "createdAt": now,
                    "updatedAt": now,
                }

                upsert_knowledge_document(
                    self.client,
                    self._index_name,
                    doc.chunkId,
                    doc_body,
                )

                results.append(KnowledgeDocumentResult(
                    chunkId=doc.chunkId,
                    documentId=_make_document_id(doc),
                    status="SUCCESS",
                ))

            except Exception as exc:
                logger.error(
                    "Failed to write document chunkId=%s: %s",
                    doc.chunkId,
                    exc,
                    exc_info=True,
                )
                results.append(KnowledgeDocumentResult(
                    chunkId=doc.chunkId,
                    documentId=_make_document_id(doc),
                    status="FAILED",
                    error=str(exc)[:500],
                ))

        return results


# ============================================
# 工具函数
# ============================================


def _make_document_id(doc: KnowledgeDocument) -> str:
    """生成 documentId：sourceType 小写 + '-' + sourceId"""
    return f"{doc.sourceType.lower()}-{doc.sourceId}"


def _hash_text(text: str) -> str:
    """计算文本 SHA256 哈希（用于去重对比）"""
    return hashlib.sha256(text.encode("utf-8")).hexdigest()


# ============================================
# 模块级单例
# ============================================
_knowledge_service: KnowledgeService | None = None


def get_knowledge_service() -> KnowledgeService:
    global _knowledge_service
    if _knowledge_service is None:
        _knowledge_service = KnowledgeService()
    return _knowledge_service
