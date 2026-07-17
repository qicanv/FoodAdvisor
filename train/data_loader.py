"""
PyTorch Dataset：加载预处理后的数据，tokenize，返回训练就绪的张量
"""

import torch
from torch.utils.data import Dataset
import pandas as pd


class SentimentDataset(Dataset):
    """
    多维度情感分析数据集。

    每条样本返回:
        input_ids:      (max_length,)
        attention_mask: (max_length,)
        labels:         {"overall": int, "service": int, "dish": int}
                        每个值为 0/1/2/3 (未提及/负向/中性/正向)
    """

    def __init__(self, csv_path, tokenizer, max_length=256):
        self.df = pd.read_csv(csv_path)
        self.tokenizer = tokenizer
        self.max_length = max_length

        # 维度名（从 config 读取，对应 CSV 中的 *_label 列）
        from config import DIMENSION_MAP
        self.dimensions = list(DIMENSION_MAP.keys())

        # 验证必要的列存在
        for dim in self.dimensions:
            col = f"{dim}_label"
            assert col in self.df.columns, f"CSV 缺少列: {col}"

    def __len__(self):
        return len(self.df)

    def __getitem__(self, idx):
        row = self.df.iloc[idx]
        text = str(row["content"])

        # Tokenize
        encoding = self.tokenizer(
            text,
            padding="max_length",
            truncation=True,
            max_length=self.max_length,
            return_tensors="pt",
        )

        # 三个维度的标签
        labels = {}
        for dim in self.dimensions:
            labels[dim] = torch.tensor(int(row[f"{dim}_label"]), dtype=torch.long)

        return {
            "input_ids": encoding["input_ids"].squeeze(0),
            "attention_mask": encoding["attention_mask"].squeeze(0),
            "labels": labels,
        }


def create_dataloaders(
    train_csv,
    val_csv,
    tokenizer,
    batch_size=16,
    max_length=256,
    num_workers=0,
):
    """
    便捷函数：一步创建训练集和验证集的 DataLoader。

    参数:
        train_csv:    预处理后的训练集路径
        val_csv:      预处理后的验证集路径
        tokenizer:    HuggingFace tokenizer
        batch_size:   批次大小
        max_length:   最大序列长度
        num_workers:  数据加载子进程数 (Windows 上建议 0)

    返回:
        train_loader, val_loader
    """
    from torch.utils.data import DataLoader

    train_dataset = SentimentDataset(train_csv, tokenizer, max_length)
    val_dataset = SentimentDataset(val_csv, tokenizer, max_length)

    train_loader = DataLoader(
        train_dataset,
        batch_size=batch_size,
        shuffle=True,
        num_workers=num_workers,
        pin_memory=True,
    )
    val_loader = DataLoader(
        val_dataset,
        batch_size=batch_size,
        shuffle=False,
        num_workers=num_workers,
        pin_memory=True,
    )

    print(f"DataLoader 创建完成: train={len(train_dataset)} 条, val={len(val_dataset)} 条")
    return train_loader, val_loader
