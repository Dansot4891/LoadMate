from functools import lru_cache

from pydantic import Field
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    """환경 변수 또는 backend/.env 에서 읽는다. 실제 값은 .env(Git 제외), 예시는 .env.example."""

    model_config = SettingsConfigDict(env_file=".env", env_prefix="LOADMATE_", extra="ignore")

    app_version: str = "0.1.0"
    database_url: str = "postgresql+psycopg://loadmate:loadmate@localhost:5432/loadmate"
    gemini_api_key: str = Field(default="", validation_alias="GEMINI_API_KEY")  # 없으면 규칙 기반으로만 동작
    ai_model: str = "gemini-3.5-flash-lite"  # 무료 등급 지원, 기본 사고량이 최소라 음성 응답에 맞다
    ai_timeout_seconds: float = 5.0  # 운전 중 대화라 오래 기다리지 않고 규칙 기반으로 넘어간다


@lru_cache
def get_settings() -> Settings:
    return Settings()
