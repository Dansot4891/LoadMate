"""운행 업무 규칙과 트랜잭션 경계 (기획서 22장 Service)."""

from __future__ import annotations

import hashlib
import json
from datetime import datetime, timedelta, timezone

from sqlalchemy.exc import IntegrityError
from sqlalchemy.orm import Session

from app.core.errors import AppError
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

DEFAULT_STALE_AFTER = timedelta(seconds=300)


def utcnow() -> datetime:
    return datetime.now(timezone.utc)


def as_utc(dt: datetime) -> datetime:
    """SQLite 는 tz 를 잃으므로 naive 는 UTC 로 본다."""
    return dt.replace(tzinfo=timezone.utc) if dt.tzinfo is None else dt.astimezone(timezone.utc)


def request_hash(payload: dict) -> str:
    return hashlib.sha256(json.dumps(payload, sort_keys=True, ensure_ascii=False, default=str).encode()).hexdigest()


class TripService:
    def __init__(self, session: Session, now=utcnow, stale_after: timedelta = DEFAULT_STALE_AFTER):
        self.s = session
        self.repo = TripRepository(session)
        self.now = now
        self.stale_after = stale_after

    # ------------------------------------------------------------ 조회

    def list_trips(self) -> TripListOut:
        return TripListOut(
            trips=[
                TripSummaryOut(id=t.id, name=t.name, status=t.status, data_source=t.data_source, stop_count=len(t.stops))
                for t in self.repo.list_trips()
            ]
        )

    def get_trip(self, trip_id: str) -> TripOut:
        return self._trip_out(self._trip(trip_id))

    # ------------------------------------------------------------ 관측

    def record_observation(self, trip_id: str, body: ObservationIn) -> ObservationCreatedOut:
        trip = self._trip(trip_id, for_update=True)
        h = request_hash(body.model_dump(exclude={"idempotency_key"}, mode="json"))
        existing = self.repo.observation_by_key(trip.id, body.idempotency_key)
        if existing is not None:
            self._check_same(existing.request_hash, h)
            return ObservationCreatedOut(observation=self._obs_out(existing), replayed=True, comparison=self._comparison(trip))
        obs = Observation(
            trip_id=trip.id, idempotency_key=body.idempotency_key, request_hash=h, source=body.source,
            status=body.status, counts=body.counts, note=body.note, observed_at=as_utc(body.observed_at), created_at=self.now(),
            zones=body.zones,
        )
        self.repo.add(obs)
        self._commit()
        return ObservationCreatedOut(observation=self._obs_out(obs), replayed=False, comparison=self._comparison(trip))

    # ------------------------------------------------------------ 최초 기준 (R02)

    def confirm_baseline(self, trip_id: str, body: BaselineIn, actor_id: str | None = None) -> BaselineResultOut:
        trip = self._trip(trip_id, for_update=True)
        h = request_hash(body.model_dump(exclude={"idempotency_key"}, mode="json"))
        existing = self.repo.event_by_key(trip.id, body.idempotency_key)
        if existing is not None:
            self._check_same(existing.request_hash, h)
            return BaselineResultOut(trip=self._trip_out(trip), replayed=True)
        if self.repo.baseline_event(trip.id) is not None:
            raise AppError(409, "baseline_already_confirmed", "최초 적재 기준이 이미 확정되었습니다")
        if body.observation_id is not None and self.repo.observation(trip.id, body.observation_id) is None:
            raise AppError(422, "observation_not_found", "해당 운행의 관측 기록이 아닙니다")
        self.repo.add(TripEvent(
            trip_id=trip.id, idempotency_key=body.idempotency_key, request_hash=h, type="baseline_confirmed",
            delta=dict(body.counts), detail={"observation_id": body.observation_id}, actor_id=actor_id, created_at=self.now(),
        ))
        trip.status = "in_progress"
        if actor_id and trip.driver_id is None:
            trip.driver_id = actor_id
        self._commit()
        return BaselineResultOut(trip=self._trip_out(trip), replayed=False)

    # ------------------------------------------------------------ 하차 완료 (R04~R07)

    def complete_stop(self, trip_id: str, stop_id: str, body: CompleteIn, actor_id: str | None = None) -> CompleteResultOut:
        trip = self._trip(trip_id, for_update=True)
        stop = next((st for st in trip.stops if st.id == stop_id), None)
        if stop is None:
            raise AppError(404, "stop_not_found", "방문지를 찾을 수 없습니다")
        h = request_hash({"stop_id": stop_id, **body.model_dump(exclude={"idempotency_key"}, mode="json")})
        existing = self.repo.event_by_key(trip.id, body.idempotency_key)
        if existing is not None:
            self._check_same(existing.request_hash, h)
            return CompleteResultOut(
                result="completed", replayed=True, stop=self._stop_out(stop),
                expected_remaining=self._expected(trip.id) or {}, comparison=ComparisonOut(**existing.detail["comparison"]),
            )
        if self.repo.baseline_event(trip.id) is None:
            raise AppError(409, "baseline_not_confirmed", "최초 적재 기준을 먼저 확정하세요")
        if stop.status != "pending":
            raise AppError(409, "stop_already_completed", "이미 완료된 방문지입니다")

        planned: dict[str, int] = {}
        for line in stop.cargo:
            planned[line.kind] = planned.get(line.kind, 0) + line.planned_qty
        for kind, n in body.delivered.items():
            if kind not in planned:
                raise AppError(422, "unknown_kind", f"이 방문지에 없는 물품 종류입니다: {kind}")
            if n > planned[kind]:
                raise AppError(422, "delivered_exceeds_planned", f"{kind} 하차 수량이 계획({planned[kind]})보다 많습니다")

        expected_after = rules.add_counts(self._expected(trip.id) or {}, body.delivered, sign=-1)
        cmp = self._compare(trip.id, expected_after)
        if cmp.state != "match" and body.resolution is None:
            # 반영하지 않음. 키도 소비하지 않는다 (api-contract 공통 규칙)
            return CompleteResultOut(
                result="needs_confirmation", replayed=False, stop=self._stop_out(stop),
                expected_remaining=self._expected(trip.id) or {}, comparison=cmp,
            )

        self._apply_delivery(stop, body.delivered)
        stop.status = "completed" if all(l.delivered_qty == l.planned_qty for l in stop.cargo) else "partial"
        stop.completed_at = self.now()
        stop.resolution = "matched" if cmp.state == "match" else "driver_confirmed"
        self.repo.add(TripEvent(
            trip_id=trip.id, idempotency_key=body.idempotency_key, request_hash=h, type="stop_completed", stop_id=stop.id,
            delta={k: -n for k, n in body.delivered.items()},
            detail={"resolution": stop.resolution, "comparison": cmp.model_dump(mode="json")},
            source=body.source, actor_id=actor_id, created_at=self.now(),
        ))
        if all(st.status != "pending" for st in trip.stops) and not any(p.status == "loaded" for p in self.repo.pickups(trip.id)):
            trip.status = "completed"
        self._commit()
        return CompleteResultOut(
            result="completed", replayed=False, stop=self._stop_out(stop),
            expected_remaining=self._expected(trip.id) or {}, comparison=cmp,
        )

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

    def record_issue(self, trip_id: str, body: IssueIn, actor_id: str | None) -> ChangeResultOut:
        trip = self._trip(trip_id, for_update=True)

        def apply() -> tuple[dict, dict]:
            i = Issue(trip_id=trip.id, kind=body.kind, zone=body.zone, note=body.note, source=body.observation_source,
                      observed_at=as_utc(body.observed_at))
            self.repo.add(i)
            self.s.flush()
            return {}, {"issue_id": i.id, "kind": body.kind, "zone": body.zone}

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

    # ------------------------------------------------------------ 이력 (F64)

    def history(self, trip_id: str) -> HistoryOut:
        trip = self._trip(trip_id)
        labels = {st.id: st.label for st in trip.stops}
        return HistoryOut(items=[
            HistoryItemOut(id=e.id, type=e.type, stop_id=e.stop_id, delta=e.delta, source=e.source, actor_id=e.actor_id,
                           created_at=as_utc(e.created_at), summary=summarize_event(e, labels))
            for e in self.repo.events(trip.id)
        ])

    # ------------------------------------------------------------ 공통 변경 처리

    def _change(self, trip: Trip, body, type_: str, apply, actor_id: str | None, stop_id: str | None = None) -> ChangeResultOut:
        """멱등 키 확인 → 규칙 검사·적용(apply) → 이력 저장. apply 는 (delta, detail) 을 돌려준다."""
        payload = body.model_dump(exclude={"idempotency_key"}, mode="json")
        h = request_hash({"type": type_, "stop_id": stop_id, **payload})
        existing = self.repo.event_by_key(trip.id, body.idempotency_key)
        if existing is not None:
            self._check_same(existing.request_hash, h)
            return ChangeResultOut(trip=self._trip_out(trip), replayed=True)
        delta, detail = apply()
        self.repo.add(TripEvent(
            trip_id=trip.id, idempotency_key=body.idempotency_key, request_hash=h, type=type_, stop_id=stop_id,
            delta=delta, detail=detail, source=body.source, actor_id=actor_id, created_at=self.now(),
        ))
        self._commit()
        return ChangeResultOut(trip=self._trip_out(trip), replayed=False)

    def _stop(self, trip: Trip, stop_id: str) -> Stop:
        stop = next((st for st in trip.stops if st.id == stop_id), None)
        if stop is None:
            raise AppError(404, "stop_not_found", "방문지를 찾을 수 없습니다")
        return stop

    @staticmethod
    def _require_open(trip: Trip) -> None:
        if trip.status in ("completed", "ended_incomplete"):
            raise AppError(409, "trip_already_ended", "이미 종료된 운행입니다")

    def _maybe_finish(self, trip: Trip) -> None:
        if all(st.status != "pending" for st in trip.stops) and not any(p.status == "loaded" for p in self.repo.pickups(trip.id)):
            if self.repo.baseline_event(trip.id) is not None:
                trip.status = "completed"

    @staticmethod
    def _pickup_out(p: Pickup) -> PickupOut:
        return PickupOut(id=p.id, stop_id=p.stop_id, item_name=p.item_name, kind=p.kind, qty=p.qty, return_place=p.return_place,
                         status=p.status, created_at=as_utc(p.created_at), returned_at=as_utc(p.returned_at) if p.returned_at else None)

    @staticmethod
    def _issue_out(i: Issue) -> IssueOut:
        return IssueOut(id=i.id, kind=i.kind, zone=i.zone, note=i.note, source=i.source, observed_at=as_utc(i.observed_at),
                        status=i.status, resolution=i.resolution, resolution_note=i.resolution_note, resolved_by=i.resolved_by,
                        resolved_at=as_utc(i.resolved_at) if i.resolved_at else None)


    # ------------------------------------------------------------ 내부

    def _trip(self, trip_id: str, for_update: bool = False) -> Trip:
        trip = self.repo.get_trip(trip_id, for_update=for_update and self.s.bind.dialect.name == "postgresql")
        if trip is None:
            raise AppError(404, "trip_not_found", "운행을 찾을 수 없습니다")
        return trip

    @staticmethod
    def _check_same(stored: str, new: str) -> None:
        if stored != new:
            raise AppError(409, "idempotency_key_conflict", "같은 요청 ID 로 다른 내용을 보냈습니다")

    def _commit(self) -> None:
        try:
            self.s.commit()
        except IntegrityError as e:  # 동시 요청이 같은 키로 먼저 저장된 경우
            self.s.rollback()
            raise AppError(409, "idempotency_key_conflict", "같은 요청이 이미 처리 중이거나 처리되었습니다. 다시 조회하세요") from e

    @staticmethod
    def _apply_delivery(stop: Stop, delivered: dict[str, int]) -> None:
        for kind, n in delivered.items():
            left = n
            for line in stop.cargo:
                if line.kind == kind and left > 0:
                    take = min(left, line.planned_qty - line.delivered_qty)
                    line.delivered_qty += take
                    left -= take

    def _expected(self, trip_id: str) -> rules.Counts | None:
        events = self.repo.events(trip_id)
        if not any(e.type == "baseline_confirmed" for e in events):
            return None
        return rules.expected_remaining([e.delta for e in events])

    def _compare(self, trip_id: str, expected: rules.Counts | None) -> ComparisonOut:
        obs = self.repo.latest_observation(trip_id)
        view = None
        if obs is not None:
            view = rules.ObservationView(obs.id, obs.source, obs.status, obs.counts, as_utc(obs.observed_at))
        c = rules.compare(expected, view, self.now(), self.stale_after)
        return ComparisonOut(
            state=c.state, expected=c.expected, observed=c.observed,
            differences=[DifferenceOut(kind=d.kind, expected=d.expected, observed=d.observed) for d in c.differences],
            observation=self._obs_out(obs) if obs is not None else None,
            stale_after_seconds=int(self.stale_after.total_seconds()),
        )

    def _comparison(self, trip: Trip) -> ComparisonOut:
        """기준 확정 전에는 계획(검수 목록) 수량과 관측을 비교한다 (R02, S05)."""
        expected = self._expected(trip.id)
        if expected is None:
            expected = {}
            for st in trip.stops:
                for line in st.cargo:
                    expected[line.kind] = expected.get(line.kind, 0) + line.planned_qty
        return self._compare(trip.id, expected)

    @staticmethod
    def _obs_out(o: Observation) -> ObservationOut:
        return ObservationOut(id=o.id, source=o.source, status=o.status, counts=o.counts, observed_at=as_utc(o.observed_at), zones=o.zones)

    @staticmethod
    def _stop_out(st: Stop) -> StopOut:
        return StopOut(
            id=st.id, seq=st.seq, label=st.label, name=st.name, address=st.address, latitude=st.latitude,
            longitude=st.longitude, status=st.status, completed_at=as_utc(st.completed_at) if st.completed_at else None,
            resolution=st.resolution,
            place_key=st.place_key, cancel_reason=st.cancel_reason,
            cargo=[CargoOut(id=l.id, item_name=l.item_name, kind=l.kind, planned_qty=l.planned_qty, delivered_qty=l.delivered_qty, zone=l.zone) for l in st.cargo],
        )

    def _trip_out(self, trip: Trip) -> TripOut:
        base = self.repo.baseline_event(trip.id)
        return TripOut(
            id=trip.id, name=trip.name, data_source=trip.data_source, status=trip.status,
            baseline=BaselineOut(counts=base.delta, confirmed_at=as_utc(base.created_at), observation_id=base.detail.get("observation_id")) if base else None,
            stops=[self._stop_out(st) for st in trip.stops],
            expected_remaining=self._expected(trip.id),
            comparison=self._comparison(trip),
            driver_id=trip.driver_id, vehicle_id=trip.vehicle_id, end_reason=trip.end_reason,
            pickups=[self._pickup_out(p) for p in self.repo.pickups(trip.id)],
            issues=[self._issue_out(i) for i in self.repo.issues(trip.id)],
        )

def stop_planned(stop: Stop) -> dict[str, int]:
    out: dict[str, int] = {}
    for l in stop.cargo:
        out[l.kind] = out.get(l.kind, 0) + l.planned_qty - l.delivered_qty
    return out


_EVENT_LABEL = {
    "baseline_confirmed": "최초 적재 기준 확정", "stop_completed": "하차 완료", "stop_added": "방문지 추가",
    "stops_reordered": "방문 순서 변경", "stop_cancelled": "방문지 취소", "cargo_qty_changed": "물품 수량 변경",
    "trip_ended": "운행 종료", "pickup_added": "회수·추가 상차", "pickup_returned": "회수품 반납",
    "issue_recorded": "적재 이상 의심 기록", "issue_resolved": "이상 처리", "handover_accepted": "인계 인수",
}


def summarize_event(e: TripEvent, stop_labels: dict[str, str]) -> str:
    parts = [_EVENT_LABEL.get(e.type, e.type)]
    if e.stop_id and e.stop_id in stop_labels:
        parts.append(stop_labels[e.stop_id])
    if e.delta:
        parts.append(" ".join(f"{k} {'+' if v > 0 else ''}{v}" for k, v in e.delta.items()))
    if e.detail.get("resolution"):
        parts.append({"matched": "관측 일치", "driver_confirmed": "기사 확인"}.get(e.detail["resolution"], e.detail["resolution"]))
    if e.detail.get("reason"):
        parts.append(f"사유: {e.detail['reason']}")
    return " · ".join(parts)
