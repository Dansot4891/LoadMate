from __future__ import annotations

from datetime import datetime
from typing import Literal

from pydantic import BaseModel, Field, field_serializer, model_validator

Counts = dict[str, int]


def _iso(dt: datetime) -> str:
    return dt.strftime("%Y-%m-%dT%H:%M:%SZ")


class _Out(BaseModel):
    @field_serializer("*", when_used="json", check_fields=False)
    def _ser(self, v):  # 모든 datetime 을 UTC Z 형식으로
        return _iso(v) if isinstance(v, datetime) else v


def _non_negative(counts: Counts | None) -> Counts | None:
    if counts is not None and any(n < 0 for n in counts.values()):
        raise ValueError("수량은 0 이상이어야 합니다")
    return counts


# ---------- 요청

class ObservationIn(BaseModel):
    idempotency_key: str = Field(min_length=8, max_length=64)
    source: Literal["sample", "manual", "recorded_video"]
    status: Literal["ok", "unavailable"]
    counts: Counts | None = None
    observed_at: datetime
    note: str | None = Field(default=None, max_length=500)

    @model_validator(mode="after")
    def _check(self):
        _non_negative(self.counts)
        if self.status == "ok" and self.counts is None:
            raise ValueError("status=ok 이면 counts 가 필요합니다")
        if self.status == "unavailable" and self.counts is not None:
            raise ValueError("status=unavailable 이면 counts 는 null 이어야 합니다")
        return self


class BaselineIn(BaseModel):
    idempotency_key: str = Field(min_length=8, max_length=64)
    counts: Counts
    observation_id: int | None = None

    @model_validator(mode="after")
    def _check(self):
        _non_negative(self.counts)
        return self


class CompleteIn(BaseModel):
    idempotency_key: str = Field(min_length=8, max_length=64)
    delivered: Counts
    resolution: Literal["driver_confirmed"] | None = None

    @model_validator(mode="after")
    def _check(self):
        _non_negative(self.delivered)
        return self


# ---------- 응답

class ObservationOut(_Out):
    id: int
    source: str
    status: str
    counts: Counts | None
    observed_at: datetime


class DifferenceOut(BaseModel):
    kind: str
    expected: int
    observed: int


class ComparisonOut(_Out):
    state: Literal["match", "mismatch", "no_observation", "unavailable", "stale"]
    expected: Counts | None
    observed: Counts | None
    differences: list[DifferenceOut]
    observation: ObservationOut | None
    count_only: bool = True
    stale_after_seconds: int


class CargoOut(BaseModel):
    id: int
    item_name: str
    kind: str
    planned_qty: int
    delivered_qty: int


class StopOut(_Out):
    id: str
    seq: int
    label: str
    name: str
    address: str
    latitude: float
    longitude: float
    status: str
    completed_at: datetime | None
    resolution: str | None
    cargo: list[CargoOut]


class BaselineOut(_Out):
    counts: Counts
    confirmed_at: datetime
    observation_id: int | None


class TripOut(_Out):
    id: str
    name: str
    data_source: str
    status: str
    baseline: BaselineOut | None
    stops: list[StopOut]
    expected_remaining: Counts | None
    comparison: ComparisonOut


class TripSummaryOut(BaseModel):
    id: str
    name: str
    status: str
    data_source: str
    stop_count: int


class TripListOut(BaseModel):
    trips: list[TripSummaryOut]


class ObservationCreatedOut(BaseModel):
    observation: ObservationOut
    replayed: bool
    comparison: ComparisonOut


class BaselineResultOut(BaseModel):
    trip: TripOut
    replayed: bool


class CompleteResultOut(BaseModel):
    result: Literal["completed", "needs_confirmation"]
    replayed: bool
    stop: StopOut
    expected_remaining: Counts
    comparison: ComparisonOut
