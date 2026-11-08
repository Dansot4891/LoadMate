from __future__ import annotations

from datetime import datetime

from sqlalchemy import JSON, DateTime, ForeignKey, String, UniqueConstraint
from sqlalchemy.orm import Mapped, mapped_column

from app.core.db import Base


class Handover(Base):
    """기사 교대 인계 (F59~F62). 인계 확정과 인수는 구분한다 (R13)."""

    __tablename__ = "handovers"
    __table_args__ = (UniqueConstraint("trip_id", "idempotency_key", name="uq_handover_idem"),)

    id: Mapped[int] = mapped_column(primary_key=True, autoincrement=True)
    trip_id: Mapped[str] = mapped_column(ForeignKey("trips.id", ondelete="CASCADE"), index=True)
    idempotency_key: Mapped[str] = mapped_column(String(64))
    from_driver_id: Mapped[str] = mapped_column(String(64))
    to_driver_id: Mapped[str] = mapped_column(String(64))
    note: Mapped[str] = mapped_column(String(1000), default="")
    # 인계 시점의 요약 스냅샷 (공유 기록·미완료 업무만, 개인 기록 제외)
    summary: Mapped[dict] = mapped_column(JSON)
    status: Mapped[str] = mapped_column(String(16), default="pending")  # pending | accepted
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True))
    accepted_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True), nullable=True)
