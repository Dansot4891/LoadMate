"""운행 업무 규칙과 트랜잭션 경계 (기획서 22장 Service)."""

from __future__ import annotations

import hashlib
import json
from datetime import datetime, timedelta

from sqlalchemy.exc import IntegrityError
from sqlalchemy.orm import Session

from app.core.errors import AppError
from app.core.time import as_utc, utcnow
from app.features.trip import rules
from app.features.trip.models import CargoLine, Issue, Observation, Pickup, Stop, Trip, TripEvent
from app.features.trip.repository import TripRepository
from app.features.trip.schemas import (
    CargoQtyIn,
    ChangeResultOut,
    HistoryItemOut,
    HistoryOut,
    IssueIn,
    IssueOut,
    IssueResolveIn,
    PickupIn,
    PickupOut,
    ReorderIn,
    SimpleChangeIn,
    StopAddIn,
    StopCancelIn,
    TripEndIn,
    BaselineIn,
    BaselineOut,
    BaselineResultOut,
    CargoOut,
    CompleteIn,
    CompleteResultOut,
    ComparisonOut,
    DifferenceOut,
    ObservationCreatedOut,
    ObservationIn,
    ObservationOut,
    StopOut,
    TripListOut,
    TripOut,
    TripSummaryOut,
)


from app.features.trip.service.common import request_hash, stop_planned, summarize_event, ZONE_NAMES, _ISSUE_KIND, _RESOLUTION

class PickupsMixin:

    def add_pickup(self, trip_id: str, body: PickupIn, actor_id: str | None) -> ChangeResultOut:
        trip = self._trip(trip_id, for_update=True)
        self._require_open(trip)
        if self.repo.baseline_event(trip.id) is None:
            raise AppError(409, "baseline_not_confirmed", "최초 적재 기준을 먼저 확정하세요")
        if body.stop_id is not None:
            self._stop(trip, body.stop_id)

        def apply() -> tuple[dict, dict]:
            p = Pickup(trip_id=trip.id, stop_id=body.stop_id, item_name=body.item_name, kind=body.kind, qty=body.qty,
                       return_place=body.return_place, created_at=self.now())
            self.repo.add(p)
            self.s.flush()
            return {body.kind: body.qty}, {"pickup_id": p.id, "item_name": body.item_name, "return_place": body.return_place}

        return self._change(trip, body, "pickup_added", apply, actor_id, stop_id=body.stop_id)

    def return_pickup(self, trip_id: str, pickup_id: int, body: SimpleChangeIn, actor_id: str | None) -> ChangeResultOut:
        trip = self._trip(trip_id, for_update=True)
        p = next((x for x in self.repo.pickups(trip.id) if x.id == pickup_id), None)
        if p is None:
            raise AppError(404, "pickup_not_found", "회수 기록을 찾을 수 없습니다")

        def apply() -> tuple[dict, dict]:
            if p.status != "loaded":
                raise AppError(409, "pickup_already_returned", "이미 반납 처리되었습니다")
            p.status = "returned"
            p.returned_at = self.now()
            self._maybe_finish(trip)
            return {p.kind: -p.qty}, {"pickup_id": p.id, "return_place": p.return_place}

        return self._change(trip, body, "pickup_returned", apply, actor_id)

    # ------------------------------------------------------------ 적재 이상 (F30·F31·F34)
