"""
多维度情感分析模型：共享 RoBERTa 主干 + 3 个独立分类头

架构:
    输入文本 → Tokenizer → RoBERTa (共享) → [CLS] hidden (768)
                                                │
              ┌─────────────────────────────────┼─────────────────────────────────┐
              ▼                                 ▼                                 ▼
       overall_head (768→4)              service_head (768→4)              dish_head (768→4)
              │                                 │                                 │
              ▼                                 ▼                                 ▼
       logits_overall                   logits_service                   logits_dish

每个头输出 4 个 logits: [未提及, 负向, 中性, 正向]
"""

import torch
import torch.nn as nn
from transformers import AutoModel, AutoConfig


class MultiHeadSentimentClassifier(nn.Module):
    """
    多维度情感分类器。

    共享一个预训练 transformer 编码器，顶部接 N 个独立的线性分类头，
    每个头负责一个维度（overall / service / dish）的四分类任务。

    用法:
        model = MultiHeadSentimentClassifier("hfl/chinese-roberta-wwm-ext")
        outputs = model(input_ids, attention_mask)
        # outputs["overall"]  → {"logits": (B,4), "probs": (B,4), "pred": (B,)}
    """

    def __init__(
        self,
        base_model_name="hfl/chinese-roberta-wwm-ext",
        dimensions=None,
        num_labels=4,
        dropout=0.1,
    ):
        """
        参数:
            base_model_name: HuggingFace 预训练模型名
            dimensions:      维度名列表，默认 ["overall", "service", "dish"]
            num_labels:      每个维度的类别数（默认 4）
            dropout:         分类头中 dropout 的比例
        """
        super().__init__()
        self.dimensions = dimensions or ["overall", "service", "dish"]
        self.num_labels = num_labels

        # ---- 共享 transformer 编码器 ----
        self.config = AutoConfig.from_pretrained(base_model_name)
        self.encoder = AutoModel.from_pretrained(base_model_name)
        hidden_size = self.config.hidden_size

        # ---- 每个维度一个独立的分类头 ----
        self.dropout = nn.Dropout(dropout)
        self.classifiers = nn.ModuleDict()
        for dim in self.dimensions:
            self.classifiers[dim] = nn.Linear(hidden_size, num_labels)

        # ---- 损失函数（带类别权重） ----
        self.loss_fns = nn.ModuleDict()
        for dim in self.dimensions:
            self.loss_fns[dim] = nn.CrossEntropyLoss()

    def set_class_weights(self, weights_dict):
        """
        设置每个维度的类别权重用于加权损失。

        参数:
            weights_dict: {dim_name: [w0, w1, w2, w3]}
                          从 config.py 中的 CLASS_WEIGHTS 或 preprocess.py 自动计算得到
        """
        # 获取模型所在设备，确保权重跟模型在同一设备上
        try:
            device = next(self.parameters()).device
        except StopIteration:
            device = torch.device("cpu")

        for dim in self.dimensions:
            if dim in weights_dict:
                weight_tensor = torch.tensor(weights_dict[dim], dtype=torch.float,
                                             device=device)
                self.loss_fns[dim] = nn.CrossEntropyLoss(weight=weight_tensor)
            else:
                self.loss_fns[dim] = nn.CrossEntropyLoss()

    def forward(self, input_ids, attention_mask, labels=None):
        """
        前向传播。

        参数:
            input_ids:      (batch_size, max_length)
            attention_mask: (batch_size, max_length)
            labels:         {"overall": (B,), "service": (B,), "dish": (B,)}  或 None

        返回:
            dict:
                "{dim}": {
                    "logits": (B, 4)  原始 logits
                    "probs":  (B, 4)  softmax 概率 → 置信度
                    "pred":   (B,)    预测类别 (0/1/2/3)
                }
                "loss": float  总损失（仅在传入 labels 时）
        """
        # ---- 共享编码 ----
        encoder_outputs = self.encoder(
            input_ids=input_ids,
            attention_mask=attention_mask,
            return_dict=True,
        )
        pooled = encoder_outputs.last_hidden_state[:, 0, :]  # [CLS] token (B, H)
        pooled = self.dropout(pooled)

        # ---- 各维度分类 ----
        outputs = {}
        total_loss = 0.0

        for dim in self.dimensions:
            logits = self.classifiers[dim](pooled)          # (B, 4)
            probs = torch.softmax(logits, dim=-1)           # (B, 4) → 置信度
            preds = torch.argmax(logits, dim=-1)            # (B,)

            outputs[dim] = {
                "logits": logits,
                "probs": probs,
                "pred": preds,
            }

            # 计算损失
            if labels is not None and dim in labels:
                if dim in self.loss_fns:
                    loss_fn = self.loss_fns[dim]
                else:
                    loss_fn = nn.CrossEntropyLoss()
                loss = loss_fn(logits, labels[dim])
                outputs[dim]["loss"] = loss
                total_loss = total_loss + loss

        if labels is not None:
            outputs["loss"] = total_loss

        return outputs

    def predict(self, input_ids, attention_mask):
        """
        推理专用：返回简化的预测结果。

        返回:
            dict: {dim: {"label": int, "label_name": str, "confidence": float}}
        """
        LABEL_ID_TO_NAME = {0: "未提及", 1: "负向", 2: "中性", 3: "正向"}

        with torch.no_grad():
            outputs = self.forward(input_ids, attention_mask)

        results = {}
        for dim in self.dimensions:
            probs = outputs[dim]["probs"]       # (1, 4) or (B, 4)
            pred = outputs[dim]["pred"]
            conf = probs.gather(-1, pred.unsqueeze(-1)).squeeze(-1)

            results[dim] = {
                "label": pred.cpu().tolist(),
                "label_name": [LABEL_ID_TO_NAME.get(p, "未知") for p in pred.cpu().tolist()],
                "confidence": [round(c, 4) for c in conf.cpu().tolist()],
            }
        return results

    def save(self, save_dir):
        """保存模型权重和配置"""
        import os
        os.makedirs(save_dir, exist_ok=True)
        torch.save(self.state_dict(), os.path.join(save_dir, "pytorch_model.bin"))
        # 保存架构配置
        arch = {
            "dimensions": self.dimensions,
            "num_labels": self.num_labels,
            "base_model_name": self.config._name_or_path,
        }
        import json
        with open(os.path.join(save_dir, "model_config.json"), "w", encoding="utf-8") as f:
            json.dump(arch, f, ensure_ascii=False, indent=2)

    @classmethod
    def load(cls, save_dir, base_model_name=None):
        """从保存的权重恢复模型"""
        import os
        import json

        config_path = os.path.join(save_dir, "model_config.json")
        if os.path.exists(config_path):
            with open(config_path, "r", encoding="utf-8") as f:
                arch = json.load(f)
        else:
            arch = {}

        model = cls(
            base_model_name=base_model_name or arch.get("base_model_name", "hfl/chinese-roberta-wwm-ext"),
            dimensions=arch.get("dimensions", ["overall", "service", "dish"]),
            num_labels=arch.get("num_labels", 4),
        )

        state_path = os.path.join(save_dir, "pytorch_model.bin")
        if os.path.exists(state_path):
            state_dict = torch.load(state_path, map_location="cpu")
            # 如果保存的模型有权重，先用带权重的 loss 函数替换默认的
            for dim in model.dimensions:
                weight_key = f"loss_fns.{dim}.weight"
                if weight_key in state_dict:
                    model.loss_fns[dim] = nn.CrossEntropyLoss(
                        weight=state_dict[weight_key]
                    )
            model.load_state_dict(state_dict)

        return model
