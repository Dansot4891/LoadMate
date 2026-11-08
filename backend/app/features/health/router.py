from typing import Literal

from fastapi import APIRouter
from pydantic import BaseModel

from app.core.config import get_settings
from app.core.db import ping_database

router = APIRouter(tags=["health"])


class HealthResponse(BaseModel):
    status: Literal["ok", "degraded"]
    version: str
    database: Literal["ok", "unavailable"]


@router.get("/health", response_model=HealthResponse)
def health() -> HealthResponse:
    """앱 S10(연결 상태)이 사용. DB가 죽어도 200으로 응답하고 database 필드로 구분한다."""
    db_ok = ping_database()
    return HealthResponse(
        status="ok" if db_ok else "degraded",
        version=get_settings().app_version,
        database="ok" if db_ok else "unavailable",
    )
