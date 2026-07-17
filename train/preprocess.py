"""
数据预处理脚本：读原始 CSV → 聚合标签 → 输出处理后的数据集
运行方式: cd train && python preprocess.py
"""

import os
import pandas as pd
import numpy as np
from config import (
    TRAIN_FILE, VAL_FILE, TEST_FILE, PROCESSED_DIR,
    DIMENSION_MAP, LABEL_MAP, AGGREGATION_STRATEGY, SEED,
)

np.random.seed(SEED)


def aggregate_label(values, strategy=AGGREGATION_STRATEGY):
    """
    将同一维度的多个子标签聚合成一个粗标签。

    参数:
        values: list[int], 如 [-2, 1, -2, 0] 代表 4 个子标签
        strategy:
            "pessimistic": 优先报忧，帮商家发现痛点
            "any_mentioned": 忽略 -2 后按多数投票
            "strict": 所有非 -2 标签必须一致，否则判中性

    返回:
        int: 聚合后的标签 (-2/-1/0/1)
    """
    mentioned = [v for v in values if v != -2]

    if not mentioned:          # 全部未提及
        return -2

    if strategy == "pessimistic":
        if -1 in mentioned:    # 有负向 → 判负
            return -1
        if 1 in mentioned:     # 有正向 → 判正
            return 1
        return 0               # 只剩中性

    elif strategy == "any_mentioned":
        # 众数投票
        return max(set(mentioned), key=mentioned.count)

    elif strategy == "strict":
        # 所有非 -2 标签必须一致
        if len(set(mentioned)) == 1:
            return mentioned[0]
        return 0  # 不一致 → 中性

    else:
        raise ValueError(f"Unknown strategy: {strategy}")


def preprocess_file(csv_path, output_path):
    """
    处理单个 CSV 文件：
    1. 读取原始数据
    2. 按 DIMENSION_MAP 对每个维度做标签聚合
    3. 映射标签值 -2/-1/0/1 → 0/1/2/3
    4. 保存处理后的数据

    返回:
        DataFrame, dict(维度→类别分布)
    """
    print(f"  读取: {csv_path}")
    df = pd.read_csv(csv_path)
    original_count = len(df)
    print(f"  原始行数: {original_count}")

    # ---- 步骤 1: 聚合每个维度 ----
    processed = df[["id", "content"]].copy()

    for dim_name, sub_cols in DIMENSION_MAP.items():
        # 检查列是否存在
        missing = [c for c in sub_cols if c not in df.columns]
        if missing:
            raise KeyError(f"CSV 缺少列: {missing}")

        # 聚合
        raw_labels = df[sub_cols].apply(
            lambda row: aggregate_label(row.values), axis=1
        )
        processed[f"{dim_name}_raw"] = raw_labels

        # 映射到 0/1/2/3
        processed[f"{dim_name}_label"] = raw_labels.map(LABEL_MAP)

    # ---- 步骤 2: 统计分布 ----
    print(f"  聚合后分布 ({AGGREGATION_STRATEGY} 策略):")
    stats = {}
    for dim_name in DIMENSION_MAP:
        counts = processed[f"{dim_name}_label"].value_counts().sort_index()
        stats[dim_name] = counts.to_dict()
        print(f"    {dim_name}: 未提及={counts.get(0,0):>6}  "
              f"负向={counts.get(1,0):>6}  "
              f"中性={counts.get(2,0):>6}  "
              f"正向={counts.get(3,0):>6}")

    # ---- 步骤 3: 去重后的内容列（id+content 可能重复） ----
    before = len(processed)
    processed = processed.drop_duplicates(subset=["content"])
    after = len(processed)
    if before != after:
        print(f"  去重: {before} → {after} (移除 {before - after} 条重复)")

    # ---- 步骤 4: 保存 ----
    os.makedirs(os.path.dirname(output_path), exist_ok=True)
    processed.to_csv(output_path, index=False, encoding="utf-8-sig")
    print(f"  已保存: {output_path} ({len(processed)} 行)")

    return processed, stats


def compute_class_weights(combined_stats):
    """
    根据训练集分布自动计算类别权重。
    权重公式: w_c = N / (K * n_c)
      其中 N = 总样本数, K = 类别数(4), n_c = 类别 c 的样本数
    少数类获得更高权重。
    """
    weights = {}
    for dim_name, counts in combined_stats.items():
        total = sum(counts.values())
        weights[dim_name] = []
        for c in range(4):  # 0,1,2,3
            n_c = counts.get(c, 1)  # 避免除零
            w = total / (4 * n_c)
            weights[dim_name].append(round(w, 4))
        print(f"    {dim_name}: {weights[dim_name]}")
    return weights


def main():
    print("=" * 60)
    print("FoodAdvisor 数据预处理")
    print(f"聚合策略: {AGGREGATION_STRATEGY}")
    print(f"标签映射: 原始(-2/-1/0/1) → 映射后(0/1/2/3)")
    print("=" * 60)

    # 处理三个数据集
    print("\n[1/3] 处理训练集...")
    train_df, train_stats = preprocess_file(
        TRAIN_FILE, f"{PROCESSED_DIR}/train_processed.csv"
    )

    print("\n[2/3] 处理验证集...")
    val_df, val_stats = preprocess_file(
        VAL_FILE, f"{PROCESSED_DIR}/val_processed.csv"
    )

    print("\n[3/3] 处理测试集...")
    if os.path.exists(TEST_FILE):
        test_df, test_stats = preprocess_file(
            TEST_FILE, f"{PROCESSED_DIR}/test_processed.csv"
        )
    else:
        print(f"  测试集不存在 ({TEST_FILE})，跳过")

    # 计算类别权重
    print("\n[自动计算] 类别权重 (基于训练集分布, 用于处理正负不平衡):")
    weights = compute_class_weights(train_stats)

    # 保存统计信息
    import json
    stats_file = f"{PROCESSED_DIR}/stats.json"
    os.makedirs(PROCESSED_DIR, exist_ok=True)
    with open(stats_file, "w", encoding="utf-8") as f:
        json.dump({
            "strategy": AGGREGATION_STRATEGY,
            "train_stats": {k: {str(k2): v2 for k2, v2 in v.items()} for k, v in train_stats.items()},
            "class_weights": weights,
        }, f, ensure_ascii=False, indent=2)
    print(f"\n统计信息已保存: {stats_file}")

    print("\n" + "=" * 60)
    print("预处理完成！输出目录:", PROCESSED_DIR)
    print("=" * 60)


if __name__ == "__main__":
    main()
