"""
FoodAdvisor AI 服务配置
所有敏感信息从环境变量加载
"""
import os
from dotenv import load_dotenv

# 加载项目根目录的 .env
load_dotenv(os.path.join(os.path.dirname(__file__), "..", "..", ".env"))

# LLM 配置
LLM_API_KEY = os.getenv("LLM_API_KEY", "")
LLM_BASE_URL = os.getenv("LLM_BASE_URL", "https://api.deepseek.com")
LLM_MODEL = os.getenv("LLM_MODEL", "deepseek-v4-pro")

# OpenSearch 配置
OPENSEARCH_HOST = os.getenv("OPENSEARCH_HOST", "localhost")
OPENSEARCH_PORT = int(os.getenv("OPENSEARCH_PORT", "9200"))

# 服务端口
AI_SERVICE_PORT = int(os.getenv("AI_SERVICE_PORT", "8000"))

# 情感分析配置
SENTIMENT_LOW_CONFIDENCE_THRESHOLD = 0.6  # 低于此值标记为低置信度
