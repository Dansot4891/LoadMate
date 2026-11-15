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


from app.features.trip.service.common import request_hash, stop_planned, summarize_event, kind_display_names, ZONE_NAMES, _ISSUE_KIND, _RESOLUTION

class TripsMixin:
    def list_trips(self) -> TripListOut:
        return TripListOut(
            trips=[
                TripSummaryOut(id=t.id, name=t.name, status=t.status, data_source=t.data_source, stop_count=len(t.stops))
                for t in self.repo.list_trips()
            ]
        )

    def get_trip(self, trip_id: str) -> TripOut:
        return self._trip_out(self._trip(trip_id))

    def end_trip(self, trip_id: str, body: TripEndIn, actor_id: str | None) -> ChangeResultOut:
        trip = self._trip(trip_id, for_update=True)

        def apply() -> tuple[dict, dict]:
            if trip.status in ("completed", "ended_incomplete"):
                raise AppError(409, "trip_already_ended", "이미 종료된 운행입니다")
            pending = [st.label for st in trip.stops if st.status == "pending"]
            loaded = [p.id for p in self.repo.pickups(trip.id) if p.status == "loaded"]
            open_issues = [i.id for i in self.repo.issues(trip.id) if i.status == "open"]
            unfinished = bool(pending or loaded or open_issues)
            if unfinished and not body.reason:
                raise AppError(422, "end_reason_required", "남은 업무가 있어 종료 사유가 필요합니다 (인계 또는 미완료 사유)")
            trip.status = "ended_incomplete" if unfinished else "completed"
            trip.end_reason = body.reason
            trip.ended_at = self.now()
            return {}, {"pending_stops": pending, "loaded_pickups": loaded, "open_issues": open_issues, "reason": body.reason}

        return self._change(trip, body, "trip_ended", apply, actor_id)

    # ------------------------------------------------------------ 회수 (F44~F47)
    # ------------------------------------------------------------ 이력 (F64)

    def history(self, trip_id: str) -> HistoryOut:
        trip = self._trip(trip_id)
        labels = {st.id: st.label for st in trip.stops}
        names = kind_display_names([(c.kind, c.item_name) for st in trip.stops for c in st.cargo])
        pickup_names = {p.id: p.item_name for p in self.repo.pickups(trip.id)}
        return HistoryOut(items=[
            HistoryItemOut(id=e.id, type=e.type, stop_id=e.stop_id, delta=e.delta, source=e.source, actor_id=e.actor_id,
                           created_at=as_utc(e.created_at), summary=summarize_event(e, labels, names, pickup_names))
            for e in self.repo.events(trip.id)
        ])

