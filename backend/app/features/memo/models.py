from __future__ import annotations

from datetime import datetime

from sqlalchemy import JSON, DateTime, String, Text, UniqueConstraint
from sqlalchemy.orm import Mapped, mapped_column

from app.core.db import Base


class Memo(Base):
    """현장·차량·장비·운행 메모 (F49~F54). 개인 기록은 기본 비공유, 자동 인계하지 않는다 (R12)."""

    __tablename__ = "memos"
    __table_args__ = (UniqueConstraint("author_id", "idempotency_key", name="uq_memo_idem"),)

    id: Mapped[int] = mapped_column(primary_key=True, autoincrement=True)
    idempotency_key: Mapped[str] = mapped_column(String(64))
    scope_type: Mapped[str] = mapped_column(String(16))  # place | vehicle | equipment | trip
    scope_id: Mapped[str] = mapped_column(String(64), index=True)
    body: Mapped[str] = mapped_column(Text)
    tags: Mapped[list] = mapped_column(JSON, default=list)
    visibility: Mapped[str] = mapped_column(String(16), default="private")  # private | shared
    persistence: Mapped[str] = mapped_column(String(16), default="always")  # always | this_trip
    trip_id: Mapped[str | None] = mapped_column(String(64), nullable=True)  # this_trip 일 때 대상 운행
    source: Mapped[str] = mapped_column(String(16), default="screen")  # screen | voice
    author_id: Mapped[str] = mapped_column(String(64))
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True))
    updated_at: Mapped[datetime] = mapped_column(DateTime(timezone=True))
    updated_by: Mapped[str] = mapped_column(String(64))
