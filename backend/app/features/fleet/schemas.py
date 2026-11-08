from datetime import datetime
from typing import Literal

from pydantic import BaseModel, Field, field_serializer
from app.core.time import iso_utc

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
