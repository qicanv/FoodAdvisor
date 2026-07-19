"""
Embedding 服务 — 封装 BGE 模型，提供文本向量化能力

使用 SentenceTransformer 加载 BAAI/bge-base-zh-v1.5，
输出 768 维归一化向量。

模型加载为模块级单例，首次使用时初始化并常驻内存。
"""
import logging
import torch
from sentence_transformers import SentenceTransformer

logger = logging.getLogger(__name__)


class EmbeddingService:
    """本地 BGE Embedding 模型服务"""

    def __init__(self, model_name_or_path: str, device: str = "auto"):
        if device == "auto":
            self._device = "cuda" if torch.cuda.is_available() else "cpu"
        else:
            self._device = device

        logger.info(
            "Loading embedding model '%s' on %s ...",
            model_name_or_path,
            self._device,
        )
        self._model = SentenceTransformer(
            model_name_or_path=model_name_or_path,
            device=self._device,
        )
        dim = self._model.get_sentence_embedding_dimension()
        if dim is None:
            # 极少见的边界情况：部分 SentenceTransformer 封装可能返回 None
            # type: ignore[union-attr] — convert_to_numpy=True 返回 ndarray
            dim = self._model.encode(["test"], normalize_embeddings=True).shape[1]  # type: ignore[union-attr]
        self._dimension: int = dim
        # 记录模型标识（用于写入 OpenSearch 元数据）
        # 如果是本地路径，提取最后一段作为模型名
        import os as _os
        self._model_identifier = _os.path.basename(model_name_or_path.rstrip("/\\"))

        logger.info(
            "Embedding model loaded: dimension=%d, device=%s",
            self._dimension,
            self._device,
        )

    # ---- 只读属性 ----

    @property
    def dimension(self) -> int:
        """模型输出向量维度"""
        return self._dimension

    @property
    def model_identifier(self) -> str:
        """模型标识（用于写入 OpenSearch 的 embeddingModel 字段）"""
        return self._model_identifier

    # ---- 向量化 ----

    def encode(self, texts: list[str], batch_size: int = 32) -> list[list[float]]:
        """
        将文本列表转换为向量列表。

        Args:
            texts: 待编码文本列表（清洗后的纯文本）
            batch_size: 推理批次大小

        Returns:
            与 texts 等长的向量列表，每个向量为 768 维 float 列表。
            向量已做 L2 归一化，可直接用于余弦相似度计算。
        """
        if not texts:
            return []

        cleaned = [t.strip() for t in texts]
        if not any(cleaned):
            return []

        embeddings = self._model.encode(
            cleaned,
            normalize_embeddings=True,
            batch_size=batch_size,
            show_progress_bar=False,
            convert_to_numpy=True,
        )
        return embeddings.tolist()

    def encode_query(self, text: str) -> list[float]:
        """
        将查询文本转换为向量（带 BGE 指令前缀）。

        BGE 模型在检索时需要给查询加上特定前缀以获得最佳效果，
        文档向量不需要前缀（encode() 已处理）。

        Args:
            text: 用户查询文本

        Returns:
            768 维向量，已 L2 归一化
        """
        prefixed = f"为这个句子生成表示以用于检索相关文章：{text.strip()}"

        embeddings = self._model.encode(
            [prefixed],
            normalize_embeddings=True,
            batch_size=1,
            show_progress_bar=False,
            convert_to_numpy=True,
        )
        return embeddings[0].tolist()

    def check_health(self) -> bool:
        """快速自检：用空文本试跑一次编码"""
        try:
            self.encode(["健康检查"])
            return True
        except Exception:
            return False


# ============================================
# 模块级单例
# ============================================
_embedding_service: EmbeddingService | None = None


def get_embedding_service() -> EmbeddingService:
    """获取 EmbeddingService 单例（懒加载）"""
    global _embedding_service
    if _embedding_service is None:
        from app.core.config import settings

        _embedding_service = EmbeddingService(
            model_name_or_path=settings.embedding_model_path,
            device=settings.embedding_device,
        )
    return _embedding_service
