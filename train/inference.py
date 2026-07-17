"""
推理脚本：使用训练好的模型对单条或批量评价进行情感预测

用法:
    # 单条预测
    cd train && python inference.py --text "服务员态度好，但菜太难吃了"

    # 从文件批量预测
    cd train && python inference.py --input reviews.csv --output results.csv

    # 管道模式（从 stdin 读取，输出 JSON）
    echo "味道不错，环境也好" | python inference.py --pipe

    # 输出置信度分析
    cd train && python inference.py --text "还不错" --verbose
"""

import os
import sys
import json
import argparse
import csv

import torch
from transformers import AutoTokenizer

from config import BASE_MODEL, MAX_LENGTH, LABEL_ID_TO_NAME, DIMENSION_MAP

DIMENSIONS = list(DIMENSION_MAP.keys())
DIM_NAMES_CN = {
    "overall": "整体", "service": "服务", "dish": "菜品",
    "price": "价格", "environment": "环境", "location": "位置",
}
from model import MultiHeadSentimentClassifier


# ==================== 推理器 ====================

class SentimentPredictor:
    """
    情感预测器：封装模型加载和推理逻辑，方便被其他模块调用。
    """

    def __init__(self, model_dir="model/best", device=None):
        """
        参数:
            model_dir: 训练好的模型目录
            device:    "cuda", "cpu" 或 None (自动选择)
        """
        if device is None:
            self.device = torch.device("cuda" if torch.cuda.is_available() else "cpu")
        else:
            self.device = torch.device(device)

        # 加载模型
        self.model = MultiHeadSentimentClassifier.load(model_dir)
        self.model.to(self.device)
        self.model.eval()

        # 加载 tokenizer
        tokenizer_path = model_dir if os.path.exists(
            os.path.join(model_dir, "tokenizer_config.json")
        ) else BASE_MODEL
        self.tokenizer = AutoTokenizer.from_pretrained(tokenizer_path)

        self.max_length = MAX_LENGTH
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
                "dish":     {"label": 0, "label_name": "未提及", "confidence": 0.8911},
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

    def predict_from_csv(self, csv_path, text_column="content"):
        """
        从 CSV 文件批量预测。

        参数:
            csv_path:     str  输入 CSV 路径
            text_column:  str  文本列名

        返回:
            list[dict]  预测结果列表
        """
        import pandas as pd
        df = pd.read_csv(csv_path)
        if text_column not in df.columns:
            raise KeyError(f"CSV 中未找到列: {text_column}。可用列: {list(df.columns)}")

        results = []
        for i, row in df.iterrows():
            result = self.predict(str(row[text_column]))
            result["id"] = row.get("id", i)
            results.append(result)

            if (i + 1) % 100 == 0:
                print(f"  已处理 {i + 1}/{len(df)} 条...", file=sys.stderr)

        return results


# ==================== 格式化输出 ====================

def format_single(result, verbose=False):
    """格式化单条预测结果为可读文本"""
    text = result["text"]
    # 截断过长文本
    display_text = text[:80] + "..." if len(text) > 80 else text

    lines = [f"\n📝 评价: {display_text}"]
    lines.append("─" * 50)

    dim_names = DIM_NAMES_CN
    for dim, info in result.items():
        if dim == "text":
            continue
        cn_dim = dim_names.get(dim, dim)
        label = info["label_name"]
        conf = info["confidence"]
        # 情感图标
        icons = {"正向": "🟢", "中性": "🟡", "负向": "🔴", "未提及": "⚪"}
        icon = icons.get(label, "❓")
        lines.append(f"  {icon} {cn_dim:>4}: {label}  (置信度: {conf:.2%})")

        if verbose:
            probs = info["probabilities"]
            bars = ""
            for cls_name, prob in probs.items():
                bar_len = int(prob * 20)
                bars += f"     {cls_name:>4}: {'█' * bar_len}{' ' * (20 - bar_len)} {prob:.4f}\n"
            lines.append(bars.rstrip())

    return "\n".join(lines)


def results_to_csv(results, output_path):
    """将预测结果写入 CSV"""
    with open(output_path, "w", encoding="utf-8-sig", newline="") as f:
        writer = csv.writer(f)
        # 表头
        header = ["id", "text"]
        for dim in DIMENSIONS:
            header.extend([f"{dim}_label", f"{dim}_label_name", f"{dim}_confidence"])
        writer.writerow(header)

        for r in results:
            row = [r.get("id", ""), r.get("text", "")]
            for dim in DIMENSIONS:
                if dim in r:
                    row.extend([
                        r[dim]["label"],
                        r[dim]["label_name"],
                        r[dim]["confidence"],
                    ])
                else:
                    row.extend(["", "", ""])
            writer.writerow(row)


# ==================== CLI 入口 ====================

def main():
    parser = argparse.ArgumentParser(description="多维度情感分析推理")
    parser.add_argument("--model", type=str, default="model/best",
                        help="模型路径（默认: model/best）")
    parser.add_argument("--text", type=str, default=None,
                        help="单条评价文本")
    parser.add_argument("--input", type=str, default=None,
                        help="批量推理：输入 CSV 文件路径")
    parser.add_argument("--output", type=str, default=None,
                        help="批量推理：输出 CSV 文件路径")
    parser.add_argument("--text_column", type=str, default="content",
                        help="批量推理：文本列名（默认: content）")
    parser.add_argument("--pipe", action="store_true",
                        help="管道模式：从 stdin 读取，JSON 输出")
    parser.add_argument("--verbose", action="store_true",
                        help="显示每个类别的概率分布")
    parser.add_argument("--cpu", action="store_true",
                        help="强制使用 CPU")

    args = parser.parse_args()

    # ---- 初始化预测器 ----
    if args.cpu:
        device = "cpu"
    else:
        device = None  # 自动选择

    predictor = SentimentPredictor(model_dir=args.model, device=device)

    # ---- 管道模式 ----
    if args.pipe:
        text = sys.stdin.read().strip()
        if not text:
            print("❌ stdin 为空", file=sys.stderr)
            sys.exit(1)
        result = predictor.predict(text)
        print(json.dumps(result, ensure_ascii=False, indent=2))
        return

    # ---- 单条预测 ----
    if args.text:
        result = predictor.predict(args.text)
        print(format_single(result, verbose=args.verbose))
        return

    # ---- 批量预测 ----
    if args.input:
        print(f"📦 批量推理: {args.input}")
        results = predictor.predict_from_csv(args.input, text_column=args.text_column)

        output_path = args.output or "predictions.csv"
        results_to_csv(results, output_path)

        # 简要统计
        from collections import Counter
        print(f"\n📊 预测结果统计 ({len(results)} 条):")
        for dim in DIMENSIONS:
            counter = Counter(r[dim]["label_name"] for r in results)
            cn_dim = DIM_NAMES_CN.get(dim, dim)
            stats = ", ".join(f"{v}:{c}" for v, c in counter.items())
            print(f"  {cn_dim:>4}: {stats}")

        print(f"\n📁 结果已保存: {output_path}")
        return

    # ---- 无参数：交互模式 ----
    print("💬 交互模式 — 输入评价文本，Ctrl+C 退出\n")
    try:
        while True:
            text = input("评价> ").strip()
            if not text:
                continue
            if text.lower() in ("exit", "quit", "q"):
                break
            result = predictor.predict(text)
            print(format_single(result, verbose=args.verbose))
    except (KeyboardInterrupt, EOFError):
        print("\n👋 再见!")


if __name__ == "__main__":
    main()
