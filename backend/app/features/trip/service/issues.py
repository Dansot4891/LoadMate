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

class IssuesMixin:

    def record_issue(self, trip_id: str, body: IssueIn, actor_id: str | None) -> ChangeResultOut:
        trip = self._trip(trip_id, for_update=True)
        zone = body.zone
        if body.unit_id is not None:
            unit = next((u for u in self.repo.units(trip.id) if u.id == body.unit_id), None)
            if unit is None:
                raise AppError(404, "unit_not_found", "물품 단위를 찾을 수 없습니다")
            zone = zone or unit.zone or unit.planned_zone

        def apply() -> tuple[dict, dict]:
            i = Issue(trip_id=trip.id, kind=body.kind, zone=zone, unit_id=body.unit_id, note=body.note, source=body.observation_source,
                      observed_at=as_utc(body.observed_at))
            self.repo.add(i)
            self.s.flush()
            return {}, {"issue_id": i.id, "kind": body.kind, "zone": zone}

        return self._change(trip, body, "issue_recorded", apply, actor_id)

    def resolve_issue(self, trip_id: str, issue_id: int, body: IssueResolveIn, actor_id: str | None) -> ChangeResultOut:
        trip = self._trip(trip_id, for_update=True)
        issue = next((x for x in self.repo.issues(trip.id) if x.id == issue_id), None)
        if issue is None:
            raise AppError(404, "issue_not_found", "이상 기록을 찾을 수 없습니다")

        def apply() -> tuple[dict, dict]:
            if issue.status != "open":
                raise AppError(409, "issue_already_resolved", "이미 처리된 기록입니다")
            issue.status = "resolved"
            issue.resolution = body.resolution
            issue.resolution_note = body.note
            issue.resolved_by = actor_id
            issue.resolved_at = self.now()
            return {}, {"issue_id": issue.id, "resolution": body.resolution}

        return self._change(trip, body, "issue_resolved", apply, actor_id)

