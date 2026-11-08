from __future__ import annotations

from datetime import datetime

from sqlalchemy import DateTime, ForeignKey, String
from sqlalchemy.orm import Mapped, mapped_column

from app.core.db import Base


class Driver(Base):
    """기사. 인증 대신 사용자 선택(F56). 얼굴 인식 등 자동 판별을 하지 않는다 (15장)."""

    __tablename__ = "drivers"

    id: Mapped[str] = mapped_column(String(64), primary_key=True)
    name: Mapped[str] = mapped_column(String(100))


class Vehicle(Base):
    __tablename__ = "vehicles"

    id: Mapped[str] = mapped_column(String(64), primary_key=True)
    name: Mapped[str] = mapped_column(String(100))


class Equipment(Base):
    """공용 장비 (F57·F58)."""

    __tablename__ = "equipment"

    id: Mapped[str] = mapped_column(String(64), primary_key=True)
    vehicle_id: Mapped[str] = mapped_column(ForeignKey("vehicles.id", ondelete="CASCADE"), index=True)
    name: Mapped[str] = mapped_column(String(100))
    location: Mapped[str] = mapped_column(String(200))
    status: Mapped[str] = mapped_column(String(32), default="ok")  # ok | missing | broken | lent | not_returned
    note: Mapped[str] = mapped_column(String(500), default="")
    updated_by: Mapped[str | None] = mapped_column(String(64), nullable=True)
    updated_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True), nullable=True)
