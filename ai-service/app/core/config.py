import os
from functools import lru_cache

from dotenv import load_dotenv
from pydantic_settings import BaseSettings, SettingsConfigDict

# 显式加载项目根目录的 .env（与 CWD 无关）
load_dotenv(os.path.join(os.path.dirname(__file__), "..", "..", "..", ".env"))


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

    request_timeout_seconds: int = 30

    # ---- 内容处理配置 ----
    content_processing_enabled: bool = True
    content_default_max_chunk_length: int = 512
    content_default_chunk_overlap: int = 64
    content_batch_max_size: int = 500

    model_config = SettingsConfigDict(
        case_sensitive=False,
        extra="ignore",
    )


@lru_cache
def get_settings() -> Settings:
    return Settings()


settings = get_settings()
