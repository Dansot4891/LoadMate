from __future__ import annotations

from datetime import datetime
from typing import Literal

from pydantic import BaseModel, Field, field_serializer, model_validator
from app.core.time import iso_utc

Counts = dict[str, int]


class _Out(BaseModel):
    @field_serializer("*", when_used="json", check_fields=False)
    def _ser(self, v):  # 모든 datetime 을 UTC Z 형식으로
        return iso_utc(v) if isinstance(v, datetime) else v


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
    # 구역별 수량 (F12). 예: {"rear": {"box": 3}}
    zones: dict[str, Counts] | None = None

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
    source: Literal["screen", "voice"] = "screen"

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
    zones: dict[str, Counts] | None = None


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
    zone: str | None = None


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
    place_key: str = ""
    cancel_reason: str | None = None
    cargo: list[CargoOut]


class BaselineOut(_Out):
    counts: Counts
    confirmed_at: datetime
    observation_id: int | None


class CargoUnitOut(_Out):
    id: int
    stop_id: str | None
    pickup_id: int | None
    kind: str
    item_name: str
    no: int
    state: Literal["planned", "loaded", "unloaded"]
    load_seq: int
    zone: str | None
    lane: int | None
    layer: int | None
    planned_zone: str | None
    planned_lane: int | None
    planned_layer: int | None
    position_source: str | None
    updated_at: datetime


class TripOut(_Out):
    id: str
    name: str
    data_source: str
    status: str
    baseline: BaselineOut | None
    stops: list[StopOut]
    expected_remaining: Counts | None
    comparison: ComparisonOut
    driver_id: str | None = None
    vehicle_id: str | None = None
    end_reason: str | None = None
    pickups: list["PickupOut"] = []
    issues: list["IssueOut"] = []
    units: list[CargoUnitOut] = []


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


# ---------- 운행 관리·회수·이상 (단계 A·B·F)

Source = Literal["screen", "voice"]


class _Change(BaseModel):
    idempotency_key: str = Field(min_length=8, max_length=64)
    source: Source = "screen"


class CargoIn(BaseModel):
    item_name: str = Field(min_length=1, max_length=200)
    kind: str = Field(min_length=1, max_length=32)
    qty: int = Field(ge=1, le=999)
    zone: str | None = None


class StopAddIn(_Change):
    name: str = Field(min_length=1, max_length=200)
    address: str = ""
    latitude: float
    longitude: float
    place_key: str | None = None
    cargo: list[CargoIn] = Field(min_length=1)


class ReorderIn(_Change):
    stop_ids: list[str] = Field(min_length=1)


class StopCancelIn(_Change):
    reason: str = Field(min_length=1, max_length=500)


class CargoQtyIn(_Change):
    cargo_line_id: int
    planned_qty: int = Field(ge=0, le=999)


class TripEndIn(_Change):
    reason: str | None = Field(default=None, max_length=500)


class PickupIn(_Change):
    stop_id: str | None = None
    item_name: str = Field(min_length=1, max_length=200)
    kind: str = Field(min_length=1, max_length=32)
    qty: int = Field(ge=1, le=999)
    return_place: str = Field(min_length=1, max_length=200)


class SimpleChangeIn(_Change):
    pass


class IssueIn(_Change):
    kind: Literal["shift", "fall", "leak", "other"]
    zone: str | None = None
    unit_id: int | None = None
    note: str = Field(default="", max_length=500)
    observation_source: Literal["sample", "manual", "recorded_video"]
    observed_at: datetime


class IssueResolveIn(_Change):
    resolution: Literal["no_issue", "rearranged", "needs_action"]
    note: str | None = Field(default=None, max_length=500)


class SlotIn(BaseModel):
    zone: Literal["front", "middle", "rear"]
    lane: int = Field(ge=0, le=2)
    layer: int = Field(ge=1, le=3)


class PlacementEventIn(BaseModel):
    unit_id: int
    type: Literal["placed", "moved", "removed", "accepted"]
    slot: SlotIn | None = None


class PlacementsIn(BaseModel):
    idempotency_key: str = Field(min_length=8, max_length=64)
    source: Literal["sample", "manual", "recorded_video"]
    observed_at: datetime
    events: list[PlacementEventIn] = Field(min_length=1, max_length=100)


class PickupOut(_Out):
    id: int
    stop_id: str | None
    item_name: str
    kind: str
    qty: int
    return_place: str
    status: str
    created_at: datetime
    returned_at: datetime | None


class IssueOut(_Out):
    id: int
    kind: str
    zone: str | None
    unit_id: int | None
    note: str
    source: str
    observed_at: datetime
    status: str
    resolution: str | None
    resolution_note: str | None
    resolved_by: str | None
    resolved_at: datetime | None


class HistoryItemOut(_Out):
    id: int
    type: str
    stop_id: str | None
    delta: Counts
    source: str
    actor_id: str | None
    created_at: datetime
    summary: str


class HistoryOut(BaseModel):
    items: list[HistoryItemOut]


class ChangeResultOut(BaseModel):
    trip: TripOut
    replayed: bool


TripOut.model_rebuild()
BaselineResultOut.model_rebuild()
ChangeResultOut.model_rebuild()
