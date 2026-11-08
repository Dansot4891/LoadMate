from __future__ import annotations

from datetime import datetime

from sqlalchemy import JSON, DateTime, Float, ForeignKey, Integer, String, UniqueConstraint
from sqlalchemy.orm import Mapped, mapped_column, relationship

from app.core.db import Base


class Trip(Base):
    __tablename__ = "trips"

    id: Mapped[str] = mapped_column(String(64), primary_key=True)
    name: Mapped[str] = mapped_column(String(200))
    # 출처 표시 (원칙 5). 지금은 "sample" 만 사용
    data_source: Mapped[str] = mapped_column(String(32))
    status: Mapped[str] = mapped_column(String(32), default="planned")
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True))

    stops: Mapped[list[Stop]] = relationship(back_populates="trip", order_by="Stop.seq", cascade="all, delete-orphan")


class Stop(Base):
    __tablename__ = "stops"

    id: Mapped[str] = mapped_column(String(64), primary_key=True)
    trip_id: Mapped[str] = mapped_column(ForeignKey("trips.id", ondelete="CASCADE"), index=True)
    seq: Mapped[int] = mapped_column(Integer)
    label: Mapped[str] = mapped_column(String(16))
    name: Mapped[str] = mapped_column(String(200))
    address: Mapped[str] = mapped_column(String(300), default="")
    latitude: Mapped[float] = mapped_column(Float)
    longitude: Mapped[float] = mapped_column(Float)
    status: Mapped[str] = mapped_column(String(32), default="pending")  # pending | completed | partial
    completed_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True), nullable=True)
    resolution: Mapped[str | None] = mapped_column(String(32), nullable=True)  # matched | driver_confirmed

    trip: Mapped[Trip] = relationship(back_populates="stops")
    cargo: Mapped[list[CargoLine]] = relationship(back_populates="stop", order_by="CargoLine.id", cascade="all, delete-orphan")


class CargoLine(Base):
    """목적지별 배송 물품 계획. 개별 물품 ID 가 아니라 종류·수량 단위 (count_only)."""

    __tablename__ = "cargo_lines"

    id: Mapped[int] = mapped_column(Integer, primary_key=True, autoincrement=True)
    trip_id: Mapped[str] = mapped_column(ForeignKey("trips.id", ondelete="CASCADE"), index=True)
    stop_id: Mapped[str] = mapped_column(ForeignKey("stops.id", ondelete="CASCADE"), index=True)
    item_name: Mapped[str] = mapped_column(String(200))
    kind: Mapped[str] = mapped_column(String(32))
    planned_qty: Mapped[int] = mapped_column(Integer)
    delivered_qty: Mapped[int] = mapped_column(Integer, default=0)

    stop: Mapped[Stop] = relationship(back_populates="cargo")


class Observation(Base):
    """관측(카메라·샘플·수동). 업무 상태를 직접 바꾸지 않는다 (원칙 2)."""

    __tablename__ = "observations"
    __table_args__ = (UniqueConstraint("trip_id", "idempotency_key", name="uq_observation_idem"),)

    id: Mapped[int] = mapped_column(Integer, primary_key=True, autoincrement=True)
    trip_id: Mapped[str] = mapped_column(ForeignKey("trips.id", ondelete="CASCADE"), index=True)
    idempotency_key: Mapped[str] = mapped_column(String(64))
    request_hash: Mapped[str] = mapped_column(String(64))
    source: Mapped[str] = mapped_column(String(32))  # sample | manual | recorded_video
    status: Mapped[str] = mapped_column(String(32))  # ok | unavailable
    counts: Mapped[dict | None] = mapped_column(JSON, nullable=True)
    note: Mapped[str | None] = mapped_column(String(500), nullable=True)
    observed_at: Mapped[datetime] = mapped_column(DateTime(timezone=True))
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True))


class TripEvent(Base):
    """업무 확정 기록(기준 확정·하차 완료). 예상 잔량은 delta 합산 (R05). 멱등 키로 중복 반영 방지."""

    __tablename__ = "trip_events"
    __table_args__ = (UniqueConstraint("trip_id", "idempotency_key", name="uq_trip_event_idem"),)

    id: Mapped[int] = mapped_column(Integer, primary_key=True, autoincrement=True)
    trip_id: Mapped[str] = mapped_column(ForeignKey("trips.id", ondelete="CASCADE"), index=True)
    idempotency_key: Mapped[str] = mapped_column(String(64))
    request_hash: Mapped[str] = mapped_column(String(64))
    type: Mapped[str] = mapped_column(String(32))  # baseline_confirmed | stop_completed
    stop_id: Mapped[str | None] = mapped_column(String(64), nullable=True)
    delta: Mapped[dict] = mapped_column(JSON)
    # 확인 당시 근거(비교 결과·관측 ID·resolution). 근거와 확인 기록 분리 (R07)
    detail: Mapped[dict] = mapped_column(JSON, default=dict)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True))
