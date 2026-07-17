"""
模型评估脚本：全面评估训练好的情感分析模型

功能:
  1. 维度级指标 (Accuracy, Precision, Recall, Macro-F1)
  2. 混淆矩阵 (每维度一个 4×4 矩阵)
  3. 置信度校准评估 (ECE + 可靠性图数据)
  4. 错误案例分析 (输出高置信度错判样本)

用法:
    cd train && python evaluate.py                          # 评估最佳模型
    cd train && python evaluate.py --model model/best       # 指定模型路径
    cd train && python evaluate.py --output report.json     # 指定输出文件
    cd train && python evaluate.py --badcase 20             # 输出 top-20 错判样本
"""

import os
import sys
import json
import argparse
from collections import defaultdict

import numpy as np
import torch
from torch.utils.data import DataLoader
from transformers import AutoTokenizer
from sklearn.metrics import (
    accuracy_score, precision_score, recall_score, f1_score,
    confusion_matrix, classification_report,
)

from config import (
    BASE_MODEL, PROCESSED_DIR,
    BATCH_SIZE, MAX_LENGTH, LABEL_ID_TO_NAME, SEED,
)
from data_loader import SentimentDataset
from model import MultiHeadSentimentClassifier


# ==================== ECE 计算 ====================

def compute_ece(probs, labels, n_bins=10):
    """
    计算 Expected Calibration Error（期望校准误差）。

    ECE 衡量模型输出的置信度是否"诚实"：
      - 将预测按置信度分桶
      - 每桶内计算 |平均置信度 - 实际准确率|
      - 加权求和

    返回:
        ece: float         总体 ECE (越低越好, 0 = 完美校准)
        bin_data: list     每桶的详细数据 (供绘制可靠性图)
    """
    probs = np.asarray(probs)
    labels = np.asarray(labels)
    preds = np.argmax(probs, axis=-1)
    confs = np.max(probs, axis=-1)
    corrects = (preds == labels).astype(float)

    bin_boundaries = np.linspace(0., 1., n_bins + 1)
    bin_lowers = bin_boundaries[:-1]
    bin_uppers = bin_boundaries[1:]

    ece = 0.0
    bin_data = []

    for bin_lower, bin_upper in zip(bin_lowers, bin_uppers):
        in_bin = (confs > bin_lower) & (confs <= bin_upper)
        prop_in_bin = in_bin.mean()

        if prop_in_bin > 0:
            accuracy_in_bin = corrects[in_bin].mean()
            avg_confidence_in_bin = confs[in_bin].mean()
            ece += prop_in_bin * abs(avg_confidence_in_bin - accuracy_in_bin)
            bin_data.append({
                "bin_lower": round(float(bin_lower), 2),
                "bin_upper": round(float(bin_upper), 2),
                "n_samples": int(in_bin.sum()),
                "accuracy": round(float(accuracy_in_bin), 4),
                "avg_confidence": round(float(avg_confidence_in_bin), 4),
                "gap": round(float(abs(avg_confidence_in_bin - accuracy_in_bin)), 4),
            })
        else:
            bin_data.append({
                "bin_lower": round(float(bin_lower), 2),
                "bin_upper": round(float(bin_upper), 2),
                "n_samples": 0,
                "accuracy": None,
                "avg_confidence": None,
                "gap": None,
            })

    return round(float(ece), 4), bin_data


# ==================== 评估主函数 ====================

@torch.no_grad()
def evaluate(model, data_loader, device, badcase_n=0):
    """
    全面评估模型。

    返回:
        metrics:  dict  维度级指标
        matrices: dict  每维度的混淆矩阵 (4×4 list)
        badcases: list  高置信度错判样本
    """
    model.eval()
    dims = model.dimensions

    # 累积器
    all_probs = {dim: [] for dim in dims}
    all_preds = {dim: [] for dim in dims}
    all_labels = {dim: [] for dim in dims}

    # 用于 badcase 分析：保留每条样本的文本和置信度
    samples = []

    for batch in data_loader:
        input_ids = batch["input_ids"].to(device)
        attention_mask = batch["attention_mask"].to(device)
        labels = {dim: batch["labels"][dim].to(device) for dim in dims}

        outputs = model(input_ids, attention_mask)

        batch_size = input_ids.size(0)
        for i in range(batch_size):
            sample = {"dims": {}}
            for dim in dims:
                prob = outputs[dim]["probs"][i].cpu().tolist()
                pred = outputs[dim]["pred"][i].cpu().item()
                true_label = labels[dim][i].cpu().item()

                all_probs[dim].append(prob)
                all_preds[dim].append(pred)
                all_labels[dim].append(true_label)

                sample["dims"][dim] = {
                    "prob": prob,
                    "pred": pred,
                    "true": true_label,
                    "confidence": max(prob),
                    "correct": pred == true_label,
                }
            samples.append(sample)

    # ---- 1. 维度级指标 ----
    metrics = {}
    for dim in dims:
        y_true = all_labels[dim]
        y_pred = all_preds[dim]

        metrics[dim] = {
            "accuracy": round(accuracy_score(y_true, y_pred) * 100, 2),
            "precision_macro": round(precision_score(y_true, y_pred, average="macro", zero_division=0) * 100, 2),
            "recall_macro": round(recall_score(y_true, y_pred, average="macro", zero_division=0) * 100, 2),
            "f1_macro": round(f1_score(y_true, y_pred, average="macro", zero_division=0) * 100, 2),
        }

        # 四分类每类指标
        cls_report = classification_report(
            y_true, y_pred,
            labels=[0, 1, 2, 3],
            target_names=["未提及", "负向", "中性", "正向"],
            output_dict=True,
            zero_division=0,
        )
        metrics[dim]["per_class"] = {
            name: {
                "precision": round(cls_report[name]["precision"] * 100, 1),
                "recall": round(cls_report[name]["recall"] * 100, 1),
                "f1": round(cls_report[name]["f1-score"] * 100, 1),
                "support": int(cls_report[name]["support"]),
            }
            for name in ["未提及", "负向", "中性", "正向"]
        }

    # 综合得分
    f1_vals = [metrics[dim]["f1_macro"] for dim in dims]
    metrics["avg_f1_macro"] = round(np.mean(f1_vals), 2)

    # ---- 2. 混淆矩阵 ----
    matrices = {}
    for dim in dims:
        cm = confusion_matrix(all_labels[dim], all_preds[dim], labels=[0, 1, 2, 3])
        matrices[dim] = cm.tolist()

    # ---- 3. 置信度校准 ----
    calibration = {}
    for dim in dims:
        ece, bin_data = compute_ece(np.array(all_probs[dim]), np.array(all_labels[dim]))
        calibration[dim] = {"ece": ece, "reliability_bins": bin_data}

    # ---- 4. Badcase 分析 ----
    badcases = []
    if badcase_n > 0:
        # 收集所有错误预测，按置信度降序排列（高置信度错判最值得关注）
        errors = []
        for idx, sample in enumerate(samples):
            for dim in dims:
                if not sample["dims"][dim]["correct"]:
                    errors.append({
                        "sample_idx": idx,
                        "dimension": dim,
                        "true": sample["dims"][dim]["true"],
                        "pred": sample["dims"][dim]["pred"],
                        "confidence": sample["dims"][dim]["confidence"],
                    })
        errors.sort(key=lambda x: x["confidence"], reverse=True)
        badcases = errors[:badcase_n]

    return metrics, matrices, calibration, badcases


def print_report(metrics, matrices, calibration, badcases):
    """打印格式化的评估报告"""
    print("\n" + "=" * 70)
    print("📊 模型评估报告")
    print("=" * 70)

    # 综合指标
    print(f"\n  ╔══ 综合得分 ══════════════════════════╗")
    print(f"  ║  Avg Macro-F1:  {metrics['avg_f1_macro']:5.1f}%                 ║")
    print(f"  ╚══════════════════════════════════════╝")

    # 各维度详细指标
    dims = [d for d in metrics if d != "avg_f1_macro"]
    for dim in dims:
        m = metrics[dim]
        print(f"\n  ── {dim.upper()} 维度 ──")
        print(f"     指标      │  未提及  │   负向   │   中性   │   正向   │")
        print(f"  ─────────────┼──────────┼──────────┼──────────┼──────────┤")
        for metric_name in ["precision", "recall", "f1"]:
            label = {"precision": "Precision", "recall": "Recall   ", "f1": "F1-score "}[metric_name]
            vals = " │ ".join(
                f"{m['per_class'][cls][metric_name]:5.1f}%" if m['per_class'][cls][metric_name] is not None else "   -  "
                for cls in ["未提及", "负向", "中性", "正向"]
            )
            print(f"     {label}  │  {vals}  │")
        print(f"  ─────────────┴──────────┴──────────┴──────────┴──────────┘")
        print(f"     Macro:  Acc={m['accuracy']:.1f}%  P={m['precision_macro']:.1f}%  "
              f"R={m['recall_macro']:.1f}%  F1={m['f1_macro']:.1f}%")

    # ECE
    print(f"\n  ── 置信度校准 (ECE) ──")
    for dim in dims:
        ece = calibration[dim]["ece"]
        quality = "✅ 良好" if ece < 0.05 else ("⚠️ 一般" if ece < 0.10 else "❌ 需校准")
        print(f"     {dim:>8}: ECE={ece:.4f}  {quality}")

    # 混淆矩阵
    print(f"\n  ── 混淆矩阵 (行=真实, 列=预测) ──")
    for dim in dims:
        cm = matrices[dim]
        print(f"\n     {dim}:")
        print(f"              预测→ 未提及  负向  中性  正向")
        for i, name in enumerate(["未提及", "负向", "中性", "正向"]):
            row = "  ".join(f"{v:5d}" for v in cm[i])
            print(f"      真实 {name}:       {row}")

    # Badcase
    if badcases:
        print(f"\n  ── Top {len(badcases)} 高置信度错判 ──")
        for i, bc in enumerate(badcases[:10]):
            true_name = LABEL_ID_TO_NAME.get(bc["true"], "?")
            pred_name = LABEL_ID_TO_NAME.get(bc["pred"], "?")
            print(f"     #{bc['sample_idx']:>5} [{bc['dimension']:>8}] "
                  f"真实={true_name}  预测={pred_name}  置信度={bc['confidence']:.4f}")

    print("\n" + "=" * 70)


# ==================== CLI 入口 ====================

def main():
    parser = argparse.ArgumentParser(description="评估多维度情感分析模型")
    parser.add_argument("--model", type=str, default="model/best",
                        help="模型路径（默认: model/best）")
    parser.add_argument("--data", type=str, default=None,
                        help="评估数据 CSV（默认: 验证集）")
    parser.add_argument("--output", type=str, default=None,
                        help="结果输出 JSON 文件路径")
    parser.add_argument("--badcase", type=int, default=10,
                        help="输出高置信度错判样本数（默认: 10）")
    parser.add_argument("--cpu", action="store_true",
                        help="强制使用 CPU")

    args = parser.parse_args()

    if args.cpu:
        os.environ["CUDA_VISIBLE_DEVICES"] = ""

    # ---- 设备 ----
    device = torch.device("cuda" if torch.cuda.is_available() else "cpu")
    print(f"设备: {device}")

    # ---- 加载模型 ----
    model_dir = args.model
    if not os.path.exists(model_dir):
        print(f"❌ 模型路径不存在: {model_dir}")
        print(f"   请先运行 train.py 训练模型，或指定 --model 参数")
        sys.exit(1)

    print(f"加载模型: {model_dir}")
    model = MultiHeadSentimentClassifier.load(model_dir)
    model.to(device)
    model.eval()

    # ---- 加载 tokenizer ----
    tokenizer_path = model_dir if os.path.exists(
        os.path.join(model_dir, "tokenizer_config.json")
    ) else BASE_MODEL
    tokenizer = AutoTokenizer.from_pretrained(tokenizer_path)

    # ---- 加载数据 ----
    if args.data:
        data_csv = args.data
    else:
        data_csv = os.path.join(PROCESSED_DIR, "val_processed.csv")

    if not os.path.exists(data_csv):
        print(f"❌ 数据文件不存在: {data_csv}")
        sys.exit(1)

    dataset = SentimentDataset(data_csv, tokenizer, MAX_LENGTH)
    loader = DataLoader(dataset, batch_size=BATCH_SIZE, shuffle=False, num_workers=0)
    print(f"评估数据: {len(dataset)} 条")

    # ---- 评估 ----
    metrics, matrices, calibration, badcases = evaluate(
        model, loader, device, badcase_n=args.badcase,
    )

    print_report(metrics, matrices, calibration, badcases)

    # ---- 保存结果 ----
    if args.output:
        result = {
            "model_path": model_dir,
            "data_path": data_csv,
            "n_samples": len(dataset),
            "metrics": metrics,
            "confusion_matrices": matrices,
            "calibration": calibration,
            "badcases": badcases,
        }
        with open(args.output, "w", encoding="utf-8") as f:
            json.dump(result, f, ensure_ascii=False, indent=2)
        print(f"\n📁 评估结果已保存: {args.output}")

    return metrics


if __name__ == "__main__":
    main()
