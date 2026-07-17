"""
推理用配置常量（从 train/config.py 精简而来，仅保留推理所需）
"""

# 预训练模型名（推理时可能不需要重新下载，但 SentimentPredictor 会用到）
BASE_MODEL = "hfl/chinese-roberta-wwm-ext"

# Tokenizer 最大长度
MAX_LENGTH = 128

# 4 分类标签映射
LABEL_ID_TO_NAME = {0: "未提及", 1: "负向", 2: "中性", 3: "正向"}

# 维度名列表（5 维度：整体/服务/菜品/价格/环境）
DIMENSIONS = ["overall", "service", "dish", "price", "environment"]

# 维度中文名
DIM_NAMES_CN = {
    "overall": "整体",
    "service": "服务",
    "dish": "菜品",
    "price": "价格",
    "environment": "环境",
}
