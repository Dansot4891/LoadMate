from functools import lru_cache

from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    """환경 변수 또는 backend/.env 에서 읽는다. 실제 값은 .env(Git 제외), 예시는 .env.example."""

    model_config = SettingsConfigDict(env_file=".env", env_prefix="LOADMATE_", extra="ignore")

    app_version: str = "0.1.0"
    database_url: str = "postgresql+psycopg://loadmate:loadmate@localhost:5432/loadmate"


@lru_cache
def get_settings() -> Settings:
    return Settings()
