"""
推理模块：封装 SentimentPredictor，供 ai-service 调用
"""

import os

import torch
from transformers import AutoTokenizer

from app.ml.config import BASE_MODEL, MAX_LENGTH, LABEL_ID_TO_NAME
from app.ml.model import MultiHeadSentimentClassifier


class SentimentPredictor:
    """
    情感预测器：封装模型加载和推理逻辑。

    用法:
        predictor = SentimentPredictor(model_dir="../train/model/best")
        result = predictor.predict("服务员态度好，但菜太难吃了")
        # result → {"overall": {"label": 3, "label_name": "正向", "confidence": 0.92}, ...}
    """

    def __init__(self, model_dir, device=None):
        """
        参数:
            model_dir: 训练好的模型目录（包含 pytorch_model.bin, model_config.json 等）
            device:    "cuda", "cpu" 或 None (自动选择)
        """
        if device is None:
            self.device = torch.device("cuda" if torch.cuda.is_available() else "cpu")
        else:
            self.device = torch.device(device)

        self.model_dir = model_dir
        self.max_length = MAX_LENGTH

        # 加载模型
        self.model = MultiHeadSentimentClassifier.load(model_dir)
        self.model.to(self.device)
        self.model.eval()

        # 加载 tokenizer：优先使用模型目录下的，否则用预训练 tokenizer
        if os.path.exists(os.path.join(model_dir, "tokenizer_config.json")):
            self.tokenizer = AutoTokenizer.from_pretrained(model_dir)
        else:
            self.tokenizer = AutoTokenizer.from_pretrained(BASE_MODEL)

        self.dimensions = self.model.dimensions

    def predict(self, text):
        """
        预测单条评价的情感。

        参数:
            text: str  评价文本

        返回:
            dict: {
                "text": "...",
                "overall":  {"label": 3, "label_name": "正向", "confidence": 0.9234},
                "service":  {"label": 1, "label_name": "负向", "confidence": 0.7812},
                ...
            }
        """
        encoding = self.tokenizer(
            text,
            padding="max_length",
            truncation=True,
            max_length=self.max_length,
            return_tensors="pt",
        )

        input_ids = encoding["input_ids"].to(self.device)
        attention_mask = encoding["attention_mask"].to(self.device)

        with torch.no_grad():
            outputs = self.model(input_ids, attention_mask)

        result = {"text": text}
        for dim in self.dimensions:
            probs = outputs[dim]["probs"][0]     # (4,)
            pred = outputs[dim]["pred"][0].item()
            conf = probs[pred].item()

            result[dim] = {
                "label": pred,
                "label_name": LABEL_ID_TO_NAME.get(pred, "未知"),
                "confidence": round(conf, 4),
                "probabilities": {
                    "未提及": round(probs[0].item(), 4),
                    "负向": round(probs[1].item(), 4),
                    "中性": round(probs[2].item(), 4),
                    "正向": round(probs[3].item(), 4),
                },
            }
        return result

    def predict_batch(self, texts):
        """
        批量预测。

        参数:
            texts: list[str]

        返回:
            list[dict]
        """
        results = []
        for text in texts:
            results.append(self.predict(text))
        return results
