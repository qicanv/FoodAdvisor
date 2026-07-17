"""
本地模型情感分析服务 — 使用微调的 MultiHeadSentimentClassifier 进行推理

提供与 ReviewAnalysisService 的集成点：
- 懒加载模型（首次调用时加载，避免拖慢启动）
- 单条/批量预测
- 输出映射为 AnalyzeResponse schema
"""

import logging
import os
import uuid
from datetime import datetime, timezone
from typing import Optional

from app.ml.config import DIMENSIONS, DIM_NAMES_CN
from app.models.schemas import (
    AnalyzeRequest, AnalyzeResponse, AspectResult,
    SentimentEnum,
)

logger = logging.getLogger(__name__)

# 模型维度 → API 方面类别映射
DIMENSION_TO_ASPECT_CATEGORY = {
    "overall": "OVERALL",
    "service": "SERVICE",
    "dish": "TASTE",
    "price": "PRICE",
    "environment": "ENVIRONMENT",
}

# 模型标签名 → API 情感枚举映射
LABEL_TO_SENTIMENT = {
    "正向": "POSITIVE",
    "负向": "NEGATIVE",
    "中性": "NEUTRAL",
    "未提及": "NEUTRAL",
}


class MLSentimentService:
    """
    本地模型情感分析服务。

    封装 SentimentPredictor，提供与 AnalyzeResponse schema 兼容的输出。
    支持懒加载：首次调用 predict() 时才加载 409MB 模型。
    """

    def __init__(self, model_path: Optional[str] = None, device: Optional[str] = None):
        """
        参数:
            model_path: 模型目录路径，默认 "../train/model/best"
            device:     "cuda" / "cpu" / None (自动选择)
        """
        self.model_path = model_path or os.path.join(
            os.path.dirname(__file__), "..", "..", "..", "train", "model", "best"
        )
        # 规范化路径
        self.model_path = os.path.normpath(self.model_path)
        self._device = device
        self._predictor = None
        self._load_error = None

    # ---- 懒加载 ----

    def _ensure_loaded(self):
        """确保模型已加载（首次调用时触发）"""
        if self._predictor is not None:
            return
        if self._load_error is not None:
            raise RuntimeError(f"模型加载失败（已缓存）: {self._load_error}")

        try:
            from app.ml.inference import SentimentPredictor
            logger.info(f"正在加载本地模型: {self.model_path}")
            self._predictor = SentimentPredictor(
                model_dir=self.model_path,
                device=self._device,
            )
            logger.info(
                f"模型加载成功。维度: {self._predictor.dimensions}，"
                f"设备: {self._predictor.device}"
            )
        except Exception as e:
            self._load_error = str(e)
            logger.error(f"模型加载失败: {e}")
            raise RuntimeError(f"无法加载情感分析模型: {e}") from e

    @property
    def is_loaded(self) -> bool:
        return self._predictor is not None

    @property
    def model_name(self) -> str:
        return "local:MultiHeadSentimentClassifier"

    # ---- 推理 ----

    def predict(self, text: str) -> dict:
        """
        预测单条评价的情感。

        返回:
            dict: {"overall": {...}, "service": {...}, "dish": {...}, ...}
        """
        self._ensure_loaded()
        return self._predictor.predict(text)

    def predict_batch(self, texts: list) -> list:
        """批量预测"""
        self._ensure_loaded()
        return self._predictor.predict_batch(texts)

    # ---- 映射到 API schema ----

    def map_to_analyze_response(
        self,
        request: AnalyzeRequest,
        ml_result: dict,
        analysis_version: int = 1,
    ) -> AnalyzeResponse:
        """
        将模型原始输出映射为 AnalyzeResponse。

        映射规则:
        - overall.label_name → sentiment (正向→POSITIVE, 负向→NEGATIVE, etc.)
        - overall.confidence → confidence
        - 5 维度各生成一个 AspectResult 放入 aspects
        - 模型不产生 keywords/tags/issueCategories（设为空列表）
        """
        overall = ml_result.get("overall", {})
        sentiment_label = overall.get("label_name", "中性")
        confidence = overall.get("confidence", 0.5)

        # 构建 aspects
        aspects = []
        for dim in DIMENSIONS:
            if dim not in ml_result:
                continue
            dim_result = ml_result[dim]
            aspect = AspectResult(
                category=DIMENSION_TO_ASPECT_CATEGORY.get(dim, dim.upper()),
                sentiment=LABEL_TO_SENTIMENT.get(
                    dim_result.get("label_name", "中性"), "NEUTRAL"
                ),
                text=f"{DIM_NAMES_CN.get(dim, dim)}: {dim_result.get('label_name', '')} (置信度: {dim_result.get('confidence', 0):.2%})",
            )
            aspects.append(aspect)

        trace_id = f"trace-{uuid.uuid4().hex[:16]}"

        return AnalyzeResponse(
            reviewId=request.reviewId,
            merchantId=request.merchantId,
            reviewVersion=request.reviewVersion,
            analysisVersion=analysis_version,
            sentiment=LABEL_TO_SENTIMENT.get(sentiment_label, "NEUTRAL"),
            confidence=round(confidence, 4),
            lowConfidence=confidence < 0.6,
            keywords=[],
            aspects=aspects,
            tags=[],
            issueCategories=[],
            negativeReason=None,
            modelName=self.model_name,
            modelVersion=request.modelVersion,
            businessTraceId=trace_id,
            status="SUCCESS",
            errorMessage=None,
        )

    def degrade_response(
        self,
        request: AnalyzeRequest,
        error: str,
    ) -> AnalyzeResponse:
        """模型加载/推理失败时返回降级结果"""
        trace_id = f"trace-{uuid.uuid4().hex[:16]}"
        return AnalyzeResponse(
            reviewId=request.reviewId,
            merchantId=request.merchantId,
            reviewVersion=request.reviewVersion,
            analysisVersion=1,
            sentiment=SentimentEnum.NEUTRAL,
            confidence=0.0,
            lowConfidence=True,
            keywords=[],
            aspects=[],
            tags=[],
            issueCategories=[],
            negativeReason=None,
            modelName=f"fallback:{self.model_name}",
            modelVersion=request.modelVersion,
            businessTraceId=trace_id,
            status="FAILED",
            errorMessage=error[:500],
        )


# 单例 — 在 ReviewAnalysisService 中引用
ml_sentiment_service = MLSentimentService()
