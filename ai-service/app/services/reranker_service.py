"""
Reranker 服务 — BGE Reranker (bge-reranker-v2-m3)

Cross-encoder 架构：输入 (query, passage) 对，输出相关性分数。
使用 transformers.AutoModelForSequenceClassification + AutoTokenizer。
模型为模块级单例，首次使用时初始化并常驻内存。
"""
import logging

import torch
from transformers import AutoModelForSequenceClassification, AutoTokenizer

logger = logging.getLogger(__name__)


class RerankerService:
    """BGE Cross-Encoder Reranker"""

    def __init__(
        self,
        model_path: str,
        device: str = "auto",
        max_length: int = 512,
        batch_size: int = 16,
    ):
        if device == "auto":
            self._device = "cuda" if torch.cuda.is_available() else "cpu"
        else:
            self._device = device

        logger.info(
            "Loading reranker model '%s' on %s ...",
            model_path,
            self._device,
        )

        self._tokenizer = AutoTokenizer.from_pretrained(model_path)
        self._model = AutoModelForSequenceClassification.from_pretrained(
            model_path
        )
        self._model.to(self._device)
        self._model.eval()

        self._max_length = max_length
        self._batch_size = batch_size

        import os as _os

        self._model_identifier = _os.path.basename(
            model_path.rstrip("/\\")
        )

        logger.info(
            "Reranker model loaded: identifier=%s, device=%s, max_length=%d",
            self._model_identifier,
            self._device,
            self._max_length,
        )

    # ---- 只读属性 ----

    @property
    def model_identifier(self) -> str:
        """模型标识"""
        return self._model_identifier

    # ---- 推理 ----

    @torch.no_grad()
    def compute_scores(
        self, query: str, passages: list[str]
    ) -> list[float]:
        """
        对 (query, passage) 对批量计算相关性分数。

        Args:
            query: 用户查询文本
            passages: 候选文档文本列表

        Returns:
            与 passages 等长的分数列表，每个分数在 [0, 1] 之间。
        """
        if not passages:
            return []

        # 构造 (query, passage) 对
        pairs = [(query, p) for p in passages]

        all_scores: list[float] = []

        # 分批推理
        for i in range(0, len(pairs), self._batch_size):
            batch_pairs = pairs[i : i + self._batch_size]

            inputs = self._tokenizer(
                batch_pairs,
                padding=True,
                truncation=True,
                max_length=self._max_length,
                return_tensors="pt",
            )
            inputs = {k: v.to(self._device) for k, v in inputs.items()}

            outputs = self._model(**inputs)
            # BGE Reranker 使用 logits 的第一个值作为相关性 logit
            raw_scores = outputs.logits[:, 0].cpu()

            # sigmoid 归一化到 [0, 1]
            scores = torch.sigmoid(raw_scores).tolist()
            all_scores.extend(scores)

        return all_scores

    def rerank(
        self,
        query: str,
        candidates: list[dict],
        text_key: str = "text",
    ) -> list[dict]:
        """
        对候选文档列表进行重排序。

        Args:
            query: 用户查询文本
            candidates: 候选文档字典列表，每个字典需包含 text_key 对应的文本
            text_key: 文档中文本字段的键名

        Returns:
            按 rerankScore 降序排列的新列表，每个字典新增 "rerankScore" 字段。
        """
        if not candidates:
            return []

        passages = [c.get(text_key, "") for c in candidates]
        scores = self.compute_scores(query, passages)

        reranked: list[dict] = []
        for candidate, score in zip(candidates, scores):
            candidate["rerankScore"] = round(score, 4)
            reranked.append(candidate)

        reranked.sort(key=lambda c: c["rerankScore"], reverse=True)
        return reranked

    def check_health(self) -> bool:
        """快速自检：跑一对空文本"""
        try:
            self.compute_scores("健康检查", ["测试文本"])
            return True
        except Exception:
            return False


# ============================================
# 模块级单例
# ============================================
_reranker_service: RerankerService | None = None
_reranker_init_attempted: bool = False


def get_reranker_service() -> RerankerService | None:
    """
    获取 RerankerService 单例（懒加载）。

    reranker_enabled=False 或模型加载失败时返回 None，
    调用方应降级到向量相似度排序。
    """
    global _reranker_service, _reranker_init_attempted

    if _reranker_init_attempted:
        return _reranker_service

    _reranker_init_attempted = True

    from app.core.config import settings

    if not settings.reranker_enabled:
        logger.info("Reranker is disabled (reranker_enabled=False)")
        return None

    try:
        _reranker_service = RerankerService(
            model_path=settings.reranker_model_path,
            device=settings.reranker_device,
            max_length=settings.reranker_max_length,
            batch_size=settings.reranker_batch_size,
        )
    except Exception as exc:
        logger.warning(
            "Reranker model failed to load: %s. Reranking disabled.",
            exc,
        )
        _reranker_service = None

    return _reranker_service
