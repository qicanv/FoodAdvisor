from functools import lru_cache
from pathlib import Path

from pydantic_settings import BaseSettings, SettingsConfigDict

AI_SERVICE_ROOT = Path(__file__).resolve().parents[2]


class Settings(BaseSettings):
    app_name: str = "FoodAdvisor AI Service"
    app_env: str = "development"
    app_host: str = "0.0.0.0"
    app_port: int = 8000
    app_debug: bool = True

    internal_api_token: str | None = None

    opensearch_host: str = "localhost"
    opensearch_port: int = 9200
    opensearch_use_ssl: bool = False
    opensearch_verify_certs: bool = False
    opensearch_username: str | None = None
    opensearch_password: str | None = None

    llm_api_key: str | None = None
    llm_base_url: str | None = None
    llm_model: str | None = None
    llm_provider: str = "OPENAI_COMPATIBLE"

    sentiment_low_confidence_threshold: float = 0.6

    sentiment_analysis_mode: str = "local"  # local / llm / hybrid
    ml_model_path: str = "../train/model/best"
    ml_device: str = "auto"  # auto / cpu / cuda

    request_timeout_seconds: int = 90

    # ---- 内容处理配置 ----
    content_processing_enabled: bool = True
    content_default_max_chunk_length: int = 512
    content_default_chunk_overlap: int = 64
    content_batch_max_size: int = 500

    # ---- Embedding 模型配置 ----
    embedding_model_path: str = "app/local_models/bge-base-zh-v1.5"
    embedding_device: str = "auto"  # auto / cpu / cuda
    embedding_batch_size: int = 32

    # ---- Reranker 模型配置 ----
    reranker_model_path: str = "app/local_models/bge-reranker-v2-m3"
    reranker_device: str = "auto"       # auto / cpu / cuda
    reranker_enabled: bool = True       # 可通过环境变量关闭
    reranker_fetch_multiplier: int = 3  # 从 OpenSearch 多取 N 倍结果再重排
    reranker_max_length: int = 512      # Cross-encoder 最大输入长度
    reranker_batch_size: int = 16       # 重排序时的推理批次

    # ---- 知识索引 ----
    knowledge_index_name: str = "foodadvisor_knowledge_v1"

    model_config = SettingsConfigDict(
        env_file=AI_SERVICE_ROOT / ".env",
        env_file_encoding="utf-8",
        case_sensitive=False,
        extra="ignore",
    )


@lru_cache
def get_settings() -> Settings:
    return Settings()


settings = get_settings()
