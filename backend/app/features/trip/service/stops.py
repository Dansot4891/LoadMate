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

class StopsMixin:
    # ------------------------------------------------------------ 운행 관리 (F04·F07·F18·F29)

    def add_stop(self, trip_id: str, body: StopAddIn, actor_id: str | None) -> ChangeResultOut:
        trip = self._trip(trip_id, for_update=True)
        self._require_open(trip)
        baseline = self.repo.baseline_event(trip.id) is not None

        def apply() -> tuple[dict, dict]:
            seq = max((st.seq for st in trip.stops), default=0) + 1
            used = {st.label for st in trip.stops}
            label = next(ch for ch in "ABCDEFGHIJKLMNOPQRSTUVWXYZ" if ch not in used)
            sid = f"stop-{trip.id}-{seq}"
            st = Stop(id=sid, seq=seq, label=label, name=body.name, address=body.address, latitude=body.latitude,
                      longitude=body.longitude, place_key=body.place_key or sid)
            for c in body.cargo:
                st.cargo.append(CargoLine(trip_id=trip.id, item_name=c.item_name, kind=c.kind, planned_qty=c.qty, zone=c.zone))
            trip.stops.append(st)
            added: dict[str, int] = {}
            for c in body.cargo:
                added[c.kind] = added.get(c.kind, 0) + c.qty
            # 기준 확정 후 추가는 추가 상차로 예상 잔량에 더한다 (R05). 확정 전에는 계획만 바뀐다
            return (added if baseline else {}), {"stop_id": sid, "label": label, "name": body.name, "added_cargo": added}

        return self._change(trip, body, "stop_added", apply, actor_id)

    def reorder_stops(self, trip_id: str, body: ReorderIn, actor_id: str | None) -> ChangeResultOut:
        trip = self._trip(trip_id, for_update=True)
        self._require_open(trip)
        pending = [st for st in trip.stops if st.status == "pending"]
        if sorted(body.stop_ids) != sorted(st.id for st in pending):
            raise AppError(422, "reorder_must_list_pending_stops", "남은 방문지를 빠짐없이 한 번씩 보내야 합니다")

        def apply() -> tuple[dict, dict]:
            done = [st for st in trip.stops if st.status != "pending"]
            by_id = {st.id: st for st in pending}
            for i, st in enumerate(done + [by_id[x] for x in body.stop_ids], start=1):
                st.seq = i
            trip.stops.sort(key=lambda st: st.seq)
            return {}, {"order": body.stop_ids}

        return self._change(trip, body, "stops_reordered", apply, actor_id)

    def cancel_stop(self, trip_id: str, stop_id: str, body: StopCancelIn, actor_id: str | None) -> ChangeResultOut:
        trip = self._trip(trip_id, for_update=True)
        stop = self._stop(trip, stop_id)

        def apply() -> tuple[dict, dict]:
            if stop.status != "pending":
                raise AppError(409, "stop_already_completed", "이미 처리된 방문지입니다")
            stop.status = "cancelled"
            stop.cancel_reason = body.reason
            stop.completed_at = self.now()
            self._maybe_finish(trip)
            # 취소된 배송품은 차에 남는다 → 예상 잔량 변화 없음 (16장 부분 납품과 취소)
            return {}, {"reason": body.reason, "remaining_on_board": stop_planned(stop)}

        return self._change(trip, body, "stop_cancelled", apply, actor_id, stop_id=stop.id)

    def change_cargo_qty(self, trip_id: str, stop_id: str, body: CargoQtyIn, actor_id: str | None) -> ChangeResultOut:
        trip = self._trip(trip_id, for_update=True)
        stop = self._stop(trip, stop_id)
        line = next((l for l in stop.cargo if l.id == body.cargo_line_id), None)
        if line is None:
            raise AppError(404, "cargo_not_found", "물품 항목을 찾을 수 없습니다")

        def apply() -> tuple[dict, dict]:
            if stop.status != "pending":
                raise AppError(409, "stop_already_completed", "이미 처리된 방문지입니다")
            before = line.planned_qty
            line.planned_qty = body.planned_qty
            # 계획 수량 변경. 실제 적재 기준(확정값)은 바꾸지 않는다
            return {}, {"cargo_line_id": line.id, "kind": line.kind, "before": before, "after": body.planned_qty}

        return self._change(trip, body, "cargo_qty_changed", apply, actor_id, stop_id=stop.id)

