"""
本地情感分析服务 — 使用自己训练的 RoBERTa 多维度情感分类模型

与 LLM-based review_analysis_service 的区别:
  - 免费、低延迟（GPU 上 ~10ms/条）
  - 输出维度: overall / service / dish，每维度四分类 + 置信度
  - 不支持关键词提取和方面级分析（那是 LLM 的强项）

用法:
    from app.services.local_sentiment_service import local_sentiment_service
    result = local_sentiment_service.predict("服务员态度好，但菜太难吃了")
"""

import os
import sys
import logging
from typing import List, Optional
from collections import Counter, defaultdict

logger = logging.getLogger(__name__)

# ---- 延迟导入：torch/transformers 只在首次使用时加载 ----
_predictor = None
_model_loaded = False
_load_error = None


def _get_predictor():
    """
    获取 SentimentPredictor 单例（延迟加载）。
    首次调用时加载模型到 GPU/CPU，后续调用复用。
    """
    global _predictor, _model_loaded, _load_error

    if _predictor is not None:
        return _predictor

    if _load_error is not None:
        raise _load_error

    try:
        # 将 train/ 目录加入 Python 路径，以便 import inference 模块
        train_dir = os.path.join(os.path.dirname(__file__), "..", "..", "..", "train")
        train_dir = os.path.abspath(train_dir)
        if train_dir not in sys.path:
            sys.path.insert(0, train_dir)

        from inference import SentimentPredictor
        from app.core.config import settings

        model_path = settings.local_model_path
        # 如果是相对路径，解析为相对于项目根目录的绝对路径
        if not os.path.isabs(model_path):
            root = os.path.abspath(
                os.path.join(os.path.dirname(__file__), "..", "..", "..")
            )
            model_path = os.path.join(root, model_path)

        if not os.path.exists(model_path):
            available = []
            for candidate in ["train/model/final", "train/model/best"]:
                cand_path = os.path.join(root, candidate)
                if os.path.exists(cand_path):
                    available.append(candidate)
            raise FileNotFoundError(
                f"模型路径不存在: {model_path}。"
                + (f" 可用路径: {available}" if available else " 请先运行 train.py 训练模型")
            )

        logger.info(f"加载本地模型: {model_path}")
        _predictor = SentimentPredictor(model_dir=model_path)
        _model_loaded = True
        logger.info(f"本地模型加载完成 (设备: {_predictor.device})")
        return _predictor

    except ImportError as e:
        _load_error = RuntimeError(
            f"无法导入训练模块: {e}。请确保 train/ 目录存在且已安装 torch 和 transformers"
        )
        raise _load_error
    except Exception as e:
        _load_error = e
        raise


# ==================== 统计聚合 ====================

LABEL_NAMES = {0: "未提及", 1: "负向", 2: "中性", 3: "正向"}
DIM_CN = {"overall": "整体", "service": "服务", "dish": "菜品"}


def aggregate_stats(predictions: List[dict]) -> dict:
    """
    对一组预测结果做统计聚合。

    参数:
        predictions: SentimentPredictor.predict() 返回的 dict 列表

    返回:
        {
            "total": 100,
            "overall": {
                "positive": 65, "negative": 10, "neutral": 20, "unmentioned": 5,
                "positive_pct": 65.0, ...
            },
            "service": { ... },
            "dish": { ... },
            # 时间维度统计（如果传入数据包含 created_at）
            "by_time": { "2024-01": {...}, "2024-02": {...} },
        }
    """
    total = len(predictions)
    dims = ["overall", "service", "dish"]

    stats = {"total": total}
    time_buckets = defaultdict(lambda: defaultdict(int))

    for pred in predictions:
        created_at = pred.get("created_at")  # 可选，用于按时间统计

        for dim in dims:
            if dim not in pred:
                continue
            label = pred[dim]["label_name"]

            # 按维度统计
            if dim not in stats:
                stats[dim] = {"正向": 0, "负向": 0, "中性": 0, "未提及": 0}
            stats[dim][label] = stats[dim].get(label, 0) + 1

            # 按时间+维度统计（按月分桶）
            if created_at:
                month_key = str(created_at)[:7]  # "2024-01"
                time_buckets[month_key][f"{dim}_{label}"] += 1
                time_buckets[month_key]["total"] = time_buckets[month_key].get("total", 0) + 1

    # 计算百分比
    for dim in dims:
        if dim in stats:
            for label in ["正向", "负向", "中性", "未提及"]:
                count = stats[dim].get(label, 0)
                stats[dim][f"{label}_pct"] = round(count / total * 100, 1) if total > 0 else 0.0

    # 合并时间维度统计
    if time_buckets:
        stats["by_time"] = dict(time_buckets)

    return stats


def aggregate_by_merchant(predictions: List[dict]) -> dict:
    """
    按商家聚合：如果 predictions 中包含 merchant_id 字段，
    则返回每个商家的情感分布。
    """
    merchant_groups = defaultdict(list)
    for pred in predictions:
        mid = pred.get("merchant_id", "__unknown__")
        merchant_groups[mid].append(pred)

    result = {}
    for mid, preds in merchant_groups.items():
        result[str(mid)] = aggregate_stats(preds)
    return result


# ==================== 服务门面 ====================

class LocalSentimentService:
    """
    本地情感分析服务门面。
    提供单条预测、批量预测、统计聚合三个核心能力。
    """

    @property
    def is_available(self) -> bool:
        """检查本地模型是否可用"""
        global _model_loaded
        if _model_loaded:
            return True
        try:
            _get_predictor()
            return True
        except Exception:
            return False

    @property
    def model_info(self) -> dict:
        """获取模型信息"""
        predictor = _get_predictor()
        return {
            "dimensions": predictor.dimensions,
            "device": str(predictor.device),
            "max_length": predictor.max_length,
        }

    def predict(self, text: str, review_id: Optional[int] = None,
                merchant_id: Optional[int] = None) -> dict:
        """
        分析单条评价。

        返回:
            {
                "review_id": 123,
                "merchant_id": 456,
                "text": "...",
                "overall":  {"label": 3, "label_name": "正向", "confidence": 0.92},
                "service":  {"label": 1, "label_name": "负向", "confidence": 0.78},
                "dish":     {"label": 2, "label_name": "中性", "confidence": 0.55},
            }
        """
        predictor = _get_predictor()
        result = predictor.predict(text)
        result["review_id"] = review_id
        result["merchant_id"] = merchant_id
        return result

    def predict_batch(self, reviews: List[dict]) -> List[dict]:
        """
        批量分析。

        参数:
            reviews: [{"review_id": 1, "merchant_id": 2, "content": "..."}, ...]

        返回:
            [{"review_id": 1, "merchant_id": 2, "overall": {...}, ...}, ...]
        """
        predictor = _get_predictor()
        results = []
        for r in reviews:
            result = predictor.predict(r["content"])
            result["review_id"] = r.get("review_id")
            result["merchant_id"] = r.get("merchant_id")
            result["created_at"] = r.get("created_at")
            results.append(result)
        return results

    def compute_stats(self, reviews: List[dict]) -> dict:
        """
        批量分析 + 统计聚合。

        参数:
            reviews: 同 predict_batch，可包含 created_at 字段用于时间统计

        返回:
            aggregate_stats() 的输出
        """
        predictions = self.predict_batch(reviews)
        return aggregate_stats(predictions)


# 单例
local_sentiment_service = LocalSentimentService()
