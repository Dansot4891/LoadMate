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
    status: Mapped[str] = mapped_column(String(32), default="planned")  # planned | in_progress | completed | ended_incomplete
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True))
    driver_id: Mapped[str | None] = mapped_column(String(64), nullable=True)
    vehicle_id: Mapped[str | None] = mapped_column(String(64), nullable=True)
    ended_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True), nullable=True)
    # 미완료 업무가 남은 채 종료한 사유 (R13)
    end_reason: Mapped[str | None] = mapped_column(String(500), nullable=True)

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
    # 장소 식별자. 같은 장소의 메모를 운행이 바뀌어도 다시 보여 준다 (F50)
    place_key: Mapped[str] = mapped_column(String(64), default="")
    status: Mapped[str] = mapped_column(String(32), default="pending")  # pending | completed | partial | cancelled
    cancel_reason: Mapped[str | None] = mapped_column(String(500), nullable=True)
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
    # 적재 구역 (F13). 관측·기사 입력으로 정해지며 불확실하면 null
    zone: Mapped[str | None] = mapped_column(String(64), nullable=True)

    stop: Mapped[Stop] = relationship(back_populates="cargo")


class CargoUnit(Base):
    """박스 한 개의 자리 기록 (F12·F13). 표시용이며 잔량·완료 판정에 쓰지 않는다 (원칙 2)."""

    __tablename__ = "cargo_units"

    id: Mapped[int] = mapped_column(Integer, primary_key=True, autoincrement=True)
    trip_id: Mapped[str] = mapped_column(ForeignKey("trips.id", ondelete="CASCADE"), index=True)
    stop_id: Mapped[str | None] = mapped_column(String(64), nullable=True)
    cargo_line_id: Mapped[int | None] = mapped_column(Integer, nullable=True)  # 배송품이면 물품 항목
    pickup_id: Mapped[int | None] = mapped_column(Integer, nullable=True)  # 회수품이면 회수 기록
    kind: Mapped[str] = mapped_column(String(32))
    item_name: Mapped[str] = mapped_column(String(200))
    no: Mapped[int] = mapped_column(Integer)  # 방문지(또는 회수 건) 안의 번호
    load_seq: Mapped[int] = mapped_column(Integer)  # 상차 순서. 먼저 내릴 물품일수록 늦게 싣는다
    state: Mapped[str] = mapped_column(String(16), default="planned")  # planned | loaded | unloaded
    planned_zone: Mapped[str | None] = mapped_column(String(16), nullable=True)
    planned_lane: Mapped[int | None] = mapped_column(Integer, nullable=True)
    planned_layer: Mapped[int | None] = mapped_column(Integer, nullable=True)
    zone: Mapped[str | None] = mapped_column(String(16), nullable=True)
    lane: Mapped[int | None] = mapped_column(Integer, nullable=True)
    layer: Mapped[int | None] = mapped_column(Integer, nullable=True)
    position_source: Mapped[str | None] = mapped_column(String(32), nullable=True)  # sample | manual | recorded_video
    updated_at: Mapped[datetime] = mapped_column(DateTime(timezone=True))


class PlacementRequest(Base):
    """자리 소식 수신 기록. 멱등 처리와 근거 보존용이며 업무 이벤트(TripEvent)와 분리한다."""

    __tablename__ = "placement_requests"
    __table_args__ = (UniqueConstraint("trip_id", "idempotency_key", name="uq_placement_idem"),)

    id: Mapped[int] = mapped_column(Integer, primary_key=True, autoincrement=True)
    trip_id: Mapped[str] = mapped_column(ForeignKey("trips.id", ondelete="CASCADE"), index=True)
    idempotency_key: Mapped[str] = mapped_column(String(64))
    request_hash: Mapped[str] = mapped_column(String(64))
    source: Mapped[str] = mapped_column(String(32))
    events: Mapped[list] = mapped_column(JSON)
    observed_at: Mapped[datetime] = mapped_column(DateTime(timezone=True))
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True))


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
    # 구역별 종류 수량 (F12) 예: {"front": {"box": 3}}. 없으면 null
    zones: Mapped[dict | None] = mapped_column(JSON, nullable=True)


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
    # 수정 이력 출처 (F64): screen | voice | system
    source: Mapped[str] = mapped_column(String(16), default="screen")
    actor_id: Mapped[str | None] = mapped_column(String(64), nullable=True)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True))


class Pickup(Base):
    """회수품·추가 상차 (F44~F47). 배송품과 업무 용도를 구분해 관리한다 (F45)."""

    __tablename__ = "pickups"

    id: Mapped[int] = mapped_column(Integer, primary_key=True, autoincrement=True)
    trip_id: Mapped[str] = mapped_column(ForeignKey("trips.id", ondelete="CASCADE"), index=True)
    stop_id: Mapped[str | None] = mapped_column(String(64), nullable=True)  # 회수한 장소
    item_name: Mapped[str] = mapped_column(String(200))
    kind: Mapped[str] = mapped_column(String(32))
    qty: Mapped[int] = mapped_column(Integer)
    return_place: Mapped[str] = mapped_column(String(200))
    status: Mapped[str] = mapped_column(String(32), default="loaded")  # loaded | returned
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True))
    returned_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True), nullable=True)


class Issue(Base):
    """적재 이상 의심·미해결 문제 (F30·F31·F34·F60). 의심으로 기록하고 책임·파손을 확정하지 않는다."""

    __tablename__ = "issues"

    id: Mapped[int] = mapped_column(Integer, primary_key=True, autoincrement=True)
    trip_id: Mapped[str] = mapped_column(ForeignKey("trips.id", ondelete="CASCADE"), index=True)
    kind: Mapped[str] = mapped_column(String(32))  # shift | fall | leak | other
    zone: Mapped[str | None] = mapped_column(String(64), nullable=True)
    unit_id: Mapped[int | None] = mapped_column(Integer, nullable=True)  # 어느 물품인지 알 때만
    note: Mapped[str] = mapped_column(String(500), default="")
    source: Mapped[str] = mapped_column(String(32))  # sample | manual | recorded_video
    observed_at: Mapped[datetime] = mapped_column(DateTime(timezone=True))
    status: Mapped[str] = mapped_column(String(32), default="open")  # open | resolved
    resolution: Mapped[str | None] = mapped_column(String(32), nullable=True)  # no_issue | rearranged | needs_action
    resolution_note: Mapped[str | None] = mapped_column(String(500), nullable=True)
    resolved_by: Mapped[str | None] = mapped_column(String(64), nullable=True)
    resolved_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True), nullable=True)
