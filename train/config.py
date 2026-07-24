"""
集中管理所有配置：路径、模型参数、维度标签映射
"""

# ==================== 路径配置 ====================
DATA_DIR = "datasets"
TRAIN_FILE = f"{DATA_DIR}/sentiment_analysis_trainingset.csv"
VAL_FILE = f"{DATA_DIR}/sentiment_analysis_validationset.csv"
TEST_FILE = f"{DATA_DIR}/sentiment_analysis_testa.csv"
PROCESSED_DIR = f"{DATA_DIR}/processed"
MODEL_DIR = "model"

# ==================== 预训练模型 ====================
# huggingface 模型名，首次运行会自动下载到 ~/.cache/huggingface
BASE_MODEL = "hfl/chinese-roberta-wwm-ext"
# 如果网络不好，可替换为：
#   bert-base-chinese             (更小、更快、效果略差)
#   hfl/chinese-macbert-base      (对口语化文本更好)

# ==================== 维度映射 ====================
# 20 个细粒度标签 → 3 个粗维度
# 不在以下映射中的列（位置/价格/环境相关 10 列）将被丢弃
DIMENSION_MAP = {
    "overall": [
        "others_overall_experience",
        "others_willing_to_consume_again",
    ],
    "service": [
        "service_wait_time",
        "service_waiters_attitude",
        "service_parking_convenience",
        "service_serving_speed",
    ],
    "dish": [
        "dish_portion",
        "dish_taste",
        "dish_look",
        "dish_recommendation",
    ],
    "price": [
        "price_level",
        "price_cost_effective",
        "price_discount",
    ],
    "environment": [
        "environment_decoration",
        "environment_noise",
        "environment_space",
        "environment_cleaness",
    ],
    # 以下两维暂不启用 — 数据有标签，但覆盖率较低，可根据后续需求打开
    # "location": [
    #     "location_traffic_convenience",
    #     "location_distance_from_business_district",
    #     "location_easy_to_find",
    # ],
}

# ==================== 标签映射 ====================
# 原始值: -2(未提及) -1(负向) 0(中性) 1(正向)
# 映射后:  0(未提及)  1(负向) 2(中性) 3(正向)  ← PyTorch CrossEntropyLoss 要求
LABEL_MAP = {-2: 0, -1: 1, 0: 2, 1: 3}
NUM_LABELS = 4  # 四分类

# 反向映射（推理时将模型输出还原为可读标签）
LABEL_ID_TO_NAME = {0: "未提及", 1: "负向", 2: "中性", 3: "正向"}

# ==================== 聚合策略 ====================
# "pessimistic"（默认/推荐）: 优先报忧 → 任一子标签为负则整体判负，帮商家发现痛点
# "any_mentioned"         : 只要有子标签被提及，就以提及中最高频的情感为准
# "strict"                : 所有子标签必须一致，否则判中性
AGGREGATION_STRATEGY = "pessimistic"

# ==================== 训练数据量控制 ====================
# 设为 None 使用全量数据（105K），设整数则随机采样指定条数
# 建议：开发调试用 5000~10000，正式训练用 30000~50000
TRAIN_SUBSET = None  # 使用全量 105K 数据训练

# ==================== 训练超参数 ====================
BATCH_SIZE = 32
MAX_LENGTH = 256
EPOCHS = 3
LEARNING_RATE = 3e-5
WARMUP_RATIO = 0.1
WEIGHT_DECAY = 0.01

# 类别权重：不指定则自动根据训练集分布计算
# 设为 None → 自动计算；设为 {dim: [w0,w1,w2,w3]} → 手动指定
CLASS_WEIGHTS = None

# ==================== 随机种子 ====================
SEED = 42
