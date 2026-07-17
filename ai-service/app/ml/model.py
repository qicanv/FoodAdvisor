"""
多维度情感分析模型：共享 RoBERTa 主干 + 多个独立分类头

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
    每个头负责一个维度的四分类任务。

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

        # ---- 损失函数 ----
        self.loss_fns = nn.ModuleDict()
        for dim in self.dimensions:
            self.loss_fns[dim] = nn.CrossEntropyLoss()

    def forward(self, input_ids, attention_mask, labels=None):
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

            if labels is not None and dim in labels:
                loss = self.loss_fns[dim](logits, labels[dim])
                outputs[dim]["loss"] = loss
                total_loss = total_loss + loss

        if labels is not None:
            outputs["loss"] = total_loss

        return outputs

    def save(self, save_dir):
        """保存模型权重和配置"""
        import os, json
        os.makedirs(save_dir, exist_ok=True)
        torch.save(self.state_dict(), os.path.join(save_dir, "pytorch_model.bin"))
        arch = {
            "dimensions": self.dimensions,
            "num_labels": self.num_labels,
            "base_model_name": self.config._name_or_path,
        }
        with open(os.path.join(save_dir, "model_config.json"), "w", encoding="utf-8") as f:
            json.dump(arch, f, ensure_ascii=False, indent=2)

    @classmethod
    def load(cls, save_dir, base_model_name=None):
        """从保存的权重恢复模型"""
        import os, json

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
            state_dict = torch.load(state_path, map_location="cpu", weights_only=True)
            for dim in model.dimensions:
                weight_key = f"loss_fns.{dim}.weight"
                if weight_key in state_dict:
                    model.loss_fns[dim] = nn.CrossEntropyLoss(
                        weight=state_dict[weight_key]
                    )
            model.load_state_dict(state_dict)

        return model
