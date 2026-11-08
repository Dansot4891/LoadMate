"""기사·차량·공용 장비 (F56~F58). 규칙이 단순해 router 에서 바로 처리한다 (Service 를 미리 만들지 않음)."""

from __future__ import annotations

from datetime import datetime
from typing import Literal

from fastapi import APIRouter, Depends
from pydantic import BaseModel, Field, field_serializer
from sqlalchemy import select
from sqlalchemy.orm import Session

from app.core.actor import actor_id
from app.core.db import get_session
from app.core.errors import AppError
from app.core.time import as_utc, iso_utc, utcnow
from app.features.fleet.models import Driver, Equipment, Vehicle

router = APIRouter(tags=["fleet"])

EquipmentStatus = Literal["ok", "missing", "broken", "lent", "not_returned"]


class DriverOut(BaseModel):
    id: str
    name: str


class EquipmentOut(BaseModel):
    id: str
    vehicle_id: str
    name: str
    location: str
    status: str
    note: str
    updated_by: str | None
    updated_at: datetime | None

    @field_serializer("updated_at", when_used="json")
    def _ser(self, v):
        return iso_utc(v)


class EquipmentUpdateIn(BaseModel):
    status: EquipmentStatus
    location: str | None = Field(default=None, max_length=200)
    note: str | None = Field(default=None, max_length=500)


def equipment_out(e: Equipment) -> EquipmentOut:
    at = as_utc(e.updated_at) if e.updated_at else None
    return EquipmentOut(id=e.id, vehicle_id=e.vehicle_id, name=e.name, location=e.location, status=e.status, note=e.note,
                        updated_by=e.updated_by, updated_at=at)


@router.get("/drivers", response_model=dict[str, list[DriverOut]])
def list_drivers(s: Session = Depends(get_session)):
    return {"drivers": [DriverOut(id=d.id, name=d.name) for d in s.scalars(select(Driver).order_by(Driver.id))]}


@router.get("/vehicles/{vehicle_id}/equipment", response_model=dict[str, list[EquipmentOut]])
def list_equipment(vehicle_id: str, s: Session = Depends(get_session)):
    if s.get(Vehicle, vehicle_id) is None:
        raise AppError(404, "vehicle_not_found", "차량을 찾을 수 없습니다")
    rows = s.scalars(select(Equipment).where(Equipment.vehicle_id == vehicle_id).order_by(Equipment.id))
    return {"equipment": [equipment_out(e) for e in rows]}


@router.post("/equipment/{equipment_id}/status", response_model=EquipmentOut)
def update_equipment(equipment_id: str, body: EquipmentUpdateIn, s: Session = Depends(get_session), actor: str | None = Depends(actor_id)):
    """같은 값으로 다시 보내도 결과가 같다(상태 지정) → 멱등 키 불필요."""
    e = s.get(Equipment, equipment_id)
    if e is None:
        raise AppError(404, "equipment_not_found", "장비를 찾을 수 없습니다")
    e.status = body.status
    if body.location is not None:
        e.location = body.location
    if body.note is not None:
        e.note = body.note
    e.updated_by = actor
    e.updated_at = utcnow()
    s.commit()
    return equipment_out(e)
