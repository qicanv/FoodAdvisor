"""
训练脚本：加载数据 → 训练多维度情感分类模型 → 验证 → 保存最佳模型
支持断点续训：每个 epoch 结束后自动保存完整检查点，关闭窗口不丢进度。

用法:
    cd train && python train.py                      # 从头开始训练
    cd train && python train.py --epochs 8           # 覆盖训练轮数
    cd train && python train.py --resume             # 自动从最新检查点恢复
    cd train && python train.py --resume checkpoint  # 从指定检查点恢复
"""

import os
import sys
import json
import time
import argparse
import random
from datetime import datetime

import numpy as np
import torch
from torch.utils.data import DataLoader
from transformers import AutoTokenizer
from sklearn.metrics import f1_score, accuracy_score

# 导入项目模块
from config import (
    BASE_MODEL, PROCESSED_DIR, MODEL_DIR,
    DIMENSION_MAP, BATCH_SIZE, MAX_LENGTH, EPOCHS,
    LEARNING_RATE, WARMUP_RATIO, WEIGHT_DECAY, SEED, TRAIN_SUBSET,
)
from data_loader import SentimentDataset
from model import MultiHeadSentimentClassifier

DIMENSIONS = list(DIMENSION_MAP.keys())


# ==================== 路径常量 ====================
CHECKPOINT_DIR = os.path.join(MODEL_DIR, "checkpoint")
BEST_DIR = os.path.join(MODEL_DIR, "best")


# ==================== 工具函数 ====================

def set_seed(seed):
    """固定随机种子"""
    random.seed(seed)
    np.random.seed(seed)
    torch.manual_seed(seed)
    if torch.cuda.is_available():
        torch.cuda.manual_seed_all(seed)
        torch.backends.cudnn.deterministic = True


def load_class_weights(stats_path=None):
    """从 stats.json 加载自动计算的类别权重"""
    if stats_path is None:
        stats_path = os.path.join(PROCESSED_DIR, "stats.json")
    if os.path.exists(stats_path):
        with open(stats_path, "r", encoding="utf-8") as f:
            stats = json.load(f)
        return stats.get("class_weights", None)
    return None


def get_linear_scheduler(optimizer, num_warmup_steps, num_training_steps):
    """创建线性预热 + 线性衰减的学习率调度器"""
    from torch.optim.lr_scheduler import LambdaLR

    def lr_lambda(current_step):
        if current_step < num_warmup_steps:
            return float(current_step) / float(max(1, num_warmup_steps))
        progress = float(current_step - num_warmup_steps) / float(
            max(1, num_training_steps - num_warmup_steps)
        )
        return max(0.0, 1.0 - progress)

    return LambdaLR(optimizer, lr_lambda)


def format_time(elapsed_seconds):
    """将秒数格式化为 mm:ss"""
    m = int(elapsed_seconds // 60)
    s = int(elapsed_seconds % 60)
    return f"{m:02d}:{s:02d}"


# ==================== 检查点管理 ====================

def save_checkpoint(model, optimizer, scheduler, epoch, global_step,
                    best_avg_f1, best_epoch, history, save_dir):
    """保存完整训练状态，用于断点续训"""
    os.makedirs(save_dir, exist_ok=True)

    # 模型权重
    model.save(save_dir)

    # 训练状态
    checkpoint = {
        "epoch": epoch,
        "global_step": global_step,
        "best_avg_f1": best_avg_f1,
        "best_epoch": best_epoch,
        "history": history,
        "optimizer_state_dict": optimizer.state_dict(),
        "scheduler_state_dict": scheduler.state_dict(),
    }
    torch.save(checkpoint, os.path.join(save_dir, "trainer_state.pt"))
    return save_dir


def load_checkpoint(load_dir, model, device):
    """
    加载完整训练状态。
    返回 (optimizer, scheduler, start_epoch, global_step, best_avg_f1, best_epoch, history)
    如果 trainer_state.pt 不存在，则只加载模型权重，返回 None 表示从头开始优化器。
    """
    state_path = os.path.join(load_dir, "trainer_state.pt")

    if os.path.exists(state_path):
        # 完整检查点
        checkpoint = torch.load(state_path, map_location=device)
        print(f"   检查点: Epoch {checkpoint['epoch']}, "
              f"Best F1: {checkpoint['best_avg_f1']:.1f}%")
        return checkpoint
    else:
        # 只有模型权重（旧格式或 best 目录）
        print("   未找到 trainer_state.pt，仅加载模型权重，优化器将从头初始化")
        return None


# ==================== 验证函数 ====================

@torch.no_grad()
def validate(model, val_loader, device):
    """验证集评估"""
    model.eval()
    dims = model.dimensions

    total_loss = 0.0
    all_labels = {dim: [] for dim in dims}
    all_preds = {dim: [] for dim in dims}

    for batch in val_loader:
        input_ids = batch["input_ids"].to(device)
        attention_mask = batch["attention_mask"].to(device)
        labels = {dim: batch["labels"][dim].to(device) for dim in dims}

        outputs = model(input_ids, attention_mask, labels=labels)
        total_loss += outputs["loss"].item()

        for dim in dims:
            all_labels[dim].extend(labels[dim].cpu().tolist())
            all_preds[dim].extend(outputs[dim]["pred"].cpu().tolist())

    n_batches = len(val_loader)
    metrics = {"loss": round(total_loss / n_batches, 4)}
    for dim in dims:
        y_true = all_labels[dim]
        y_pred = all_preds[dim]
        metrics[f"{dim}_acc"] = round(accuracy_score(y_true, y_pred) * 100, 2)
        metrics[f"{dim}_f1"] = round(f1_score(y_true, y_pred, average="macro") * 100, 2)

    f1_scores = [metrics[f"{dim}_f1"] for dim in dims]
    metrics["avg_f1"] = round(np.mean(f1_scores), 2)
    return metrics


# ==================== 训练主循环 ====================

def train(args):
    """主训练函数"""
    set_seed(SEED)

    # ---- 设备检测 ----
    if torch.cuda.is_available():
        device = torch.device("cuda")
        print("🚀 使用 GPU:", torch.cuda.get_device_name(0))
    else:
        device = torch.device("cpu")
        print("💻 使用 CPU（训练会较慢）")

    print(f"   随机种子: {SEED}, 批次大小: {args.batch_size}, 最大长度: {args.max_length}")
    print(f"   学习率: {args.lr}, 训练轮数: {args.epochs}, Warmup: {WARMUP_RATIO}")

    # ---- 加载数据 ----
    print("\n📦 加载数据...")
    tokenizer = AutoTokenizer.from_pretrained(args.base_model)

    train_csv = os.path.join(PROCESSED_DIR, "train_processed.csv")
    val_csv = os.path.join(PROCESSED_DIR, "val_processed.csv")

    train_dataset = SentimentDataset(train_csv, tokenizer, args.max_length)
    val_dataset = SentimentDataset(val_csv, tokenizer, args.max_length)

    # ---- 可选：训练集子采样（加速开发调试） ----
    subset = args.train_subset
    if subset and subset < len(train_dataset):
        import random
        g = torch.Generator().manual_seed(SEED)
        indices = torch.randperm(len(train_dataset), generator=g)[:subset].tolist()
        train_dataset = torch.utils.data.Subset(train_dataset, indices)
        print(f"   🔹 从 {len(indices) + (len(train_dataset) - subset):,} 条中随机采样 {subset:,} 条训练")

    train_loader = DataLoader(
        train_dataset, batch_size=args.batch_size, shuffle=True,
        num_workers=0, pin_memory=(device.type == "cuda"),
    )
    val_loader = DataLoader(
        val_dataset, batch_size=args.batch_size, shuffle=False,
        num_workers=0, pin_memory=(device.type == "cuda"),
    )
    print(f"   训练集: {len(train_dataset)} 条, 验证集: {len(val_dataset)} 条")

    # ---- 初始化或恢复模型 ----
    start_epoch = 0
    global_step = 0
    best_avg_f1 = 0.0
    best_epoch = 0
    history = []
    checkpoint_state = None

    if args.resume:
        resume_path = args.resume if args.resume != "auto" else CHECKPOINT_DIR
        if not os.path.exists(resume_path):
            # 检查点不存在，尝试从 best 模型恢复
            if os.path.exists(BEST_DIR):
                print(f"\n⚠️  检查点 {resume_path} 不存在，尝试从 {BEST_DIR} 恢复...")
                resume_path = BEST_DIR
            else:
                print(f"\n❌ 未找到任何可恢复的模型，请检查路径")
                sys.exit(1)

        print(f"\n🔄 从 {resume_path} 恢复训练...")

        # 加载模型
        model = MultiHeadSentimentClassifier.load(
            resume_path, base_model_name=args.base_model
        )
        model.to(device)

        # 尝试加载完整检查点
        checkpoint_state = load_checkpoint(resume_path, model, device)
        if checkpoint_state is not None:
            start_epoch = checkpoint_state["epoch"]
            global_step = checkpoint_state["global_step"]
            best_avg_f1 = checkpoint_state["best_avg_f1"]
            best_epoch = checkpoint_state["best_epoch"]
            history = checkpoint_state["history"]

        # 确保类别权重已设置
        weights = load_class_weights()
        if weights:
            model.set_class_weights(weights)

        print(f"   从 Epoch {start_epoch + 1} 继续训练")
    else:
        print(f"\n🧠 加载预训练模型: {args.base_model}")
        model = MultiHeadSentimentClassifier(
            base_model_name=args.base_model,
            dimensions=DIMENSIONS
        )

        weights = load_class_weights()
        if weights:
            print("   已加载类别权重（处理正负样本不平衡）")
            model.set_class_weights(weights)

        model.to(device)

    # ---- 优化器 & 调度器 ----
    no_decay = ["bias", "LayerNorm.weight"]
    optimizer_grouped_parameters = [
        {
            "params": [p for n, p in model.named_parameters()
                       if not any(nd in n for nd in no_decay)],
            "weight_decay": WEIGHT_DECAY,
        },
        {
            "params": [p for n, p in model.named_parameters()
                       if any(nd in n for nd in no_decay)],
            "weight_decay": 0.0,
        },
    ]
    optimizer = torch.optim.AdamW(optimizer_grouped_parameters, lr=args.lr)

    # 总训练步数（只算剩余 epoch）
    remaining_epochs = args.epochs - start_epoch
    steps_per_epoch = len(train_loader)
    total_steps = steps_per_epoch * remaining_epochs
    warmup_steps = int(total_steps * WARMUP_RATIO)

    # 恢复优化器和调度器状态
    if checkpoint_state is not None and "optimizer_state_dict" in checkpoint_state:
        try:
            optimizer.load_state_dict(checkpoint_state["optimizer_state_dict"])
            print("   ✅ 已恢复优化器状态")
        except Exception as e:
            print(f"   ⚠️ 优化器状态恢复失败（{e}），使用全新优化器")

    scheduler = get_linear_scheduler(optimizer, warmup_steps, total_steps)

    # 注意：即使恢复了 scheduler state_dict，由于 total_steps 变了（只剩剩余epoch），
    # 这里用从零开始的 scheduler。旧 scheduler 的 step 数已不适合新设置。
    # LR 从 LEARNING_RATE 重新开始 warmup+decay 是合理的。

    print(f"   剩余训练步数: {total_steps}, Warmup 步数: {warmup_steps}")

    # ---- 训练循环 ----
    start_time = time.time()

    print("\n" + "=" * 70)
    if start_epoch > 0:
        print(f"🔄 从 Epoch {start_epoch + 1} 恢复训练 (已训练 {start_epoch} 轮)")
    else:
        print("🚀 开始训练")
    print("=" * 70)

    for epoch in range(start_epoch + 1, args.epochs + 1):
        epoch_start = time.time()
        model.train()
        epoch_loss = 0.0

        for step, batch in enumerate(train_loader):
            input_ids = batch["input_ids"].to(device)
            attention_mask = batch["attention_mask"].to(device)
            labels = {dim: batch["labels"][dim].to(device)
                      for dim in model.dimensions}

            optimizer.zero_grad()
            outputs = model(input_ids, attention_mask, labels=labels)
            loss = outputs["loss"]
            loss.backward()
            torch.nn.utils.clip_grad_norm_(model.parameters(), max_norm=1.0)
            optimizer.step()
            scheduler.step()

            global_step += 1
            epoch_loss += loss.item()

            if (step + 1) % 500 == 0 or step == 0:
                lr_now = scheduler.get_last_lr()[0]
                elapsed = format_time(time.time() - start_time)
                print(f"  Epoch {epoch:2d} | Step {step+1:5d}/{steps_per_epoch} | "
                      f"Loss: {loss.item():.4f} | LR: {lr_now:.2e} | "
                      f"累计: {elapsed}")

        # ---- 验证 ----
        val_metrics = validate(model, val_loader, device)
        epoch_time = format_time(time.time() - epoch_start)

        print(f"\n  ── Epoch {epoch} 完成 ── 用时: {epoch_time}")
        print(f"  Train Loss: {epoch_loss / steps_per_epoch:.4f}")
        print(f"  Val Loss:   {val_metrics['loss']}")
        print(f"  ─── 维度指标 ───")
        for dim in model.dimensions:
            acc = val_metrics[f"{dim}_acc"]
            f1 = val_metrics[f"{dim}_f1"]
            print(f"  {dim:>8}:  Acc={acc:5.1f}%  Macro-F1={f1:5.1f}%")
        print(f"  ─── 综合 ───  Avg Macro-F1: {val_metrics['avg_f1']:.1f}%")

        history.append({"epoch": epoch, **val_metrics})

        # ---- 保存检查点（每个 epoch 结束都存，防止丢失） ----
        save_checkpoint(
            model, optimizer, scheduler,
            epoch=epoch,
            global_step=global_step,
            best_avg_f1=best_avg_f1,
            best_epoch=best_epoch,
            history=history,
            save_dir=CHECKPOINT_DIR,
        )

        # ---- 保存最佳模型 ----
        if val_metrics["avg_f1"] > best_avg_f1:
            best_avg_f1 = val_metrics["avg_f1"]
            best_epoch = epoch
            model.save(BEST_DIR)
            tokenizer.save_pretrained(BEST_DIR)
            print(f"  💾 已保存最佳模型 → {BEST_DIR} (Avg F1: {best_avg_f1:.1f}%)")

    # ==================== 训练结束 ====================
    elapsed_total = format_time(time.time() - start_time)
    print("\n" + "=" * 70)
    print(f"✅ 训练完成！最佳模型在第 {best_epoch} 轮，Avg Macro-F1: {best_avg_f1:.1f}%")
    print(f"   总用时: {elapsed_total}")
    print("=" * 70)

    # ---- 保存最终模型 ----
    final_dir = os.path.join(MODEL_DIR, "final")
    model.save(final_dir)
    tokenizer.save_pretrained(final_dir)
    print(f"   最终模型已保存: {final_dir}")

    # ---- 保存训练历史 ----
    history_path = os.path.join(MODEL_DIR, "training_history.json")
    os.makedirs(MODEL_DIR, exist_ok=True)
    with open(history_path, "w", encoding="utf-8") as f:
        json.dump(history, f, ensure_ascii=False, indent=2)
    print(f"   训练历史已保存: {history_path}")

    return model, history


# ==================== CLI 入口 ====================

if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="训练多维度情感分析模型")
    parser.add_argument("--epochs", type=int, default=EPOCHS,
                        help=f"总训练轮数（默认: {EPOCHS}）")
    parser.add_argument("--batch_size", type=int, default=BATCH_SIZE,
                        help=f"批次大小（默认: {BATCH_SIZE}）")
    parser.add_argument("--lr", type=float, default=LEARNING_RATE,
                        help=f"学习率（默认: {LEARNING_RATE}）")
    parser.add_argument("--max_length", type=int, default=MAX_LENGTH,
                        help=f"最大序列长度（默认: {MAX_LENGTH}）")
    parser.add_argument("--base_model", type=str, default=BASE_MODEL,
                        help=f"预训练模型名（默认: {BASE_MODEL}）")
    parser.add_argument("--resume", type=str, nargs="?", const="auto",
                        help="从检查点恢复训练（默认: model/checkpoint）")
    parser.add_argument("--cpu", action="store_true",
                        help="强制使用 CPU 训练")
    parser.add_argument("--train_subset", type=int, default=TRAIN_SUBSET,
                        help=f"训练集采样条数，None=全量（默认: {TRAIN_SUBSET}）")

    args = parser.parse_args()

    if args.cpu:
        os.environ["CUDA_VISIBLE_DEVICES"] = ""

    train(args)
