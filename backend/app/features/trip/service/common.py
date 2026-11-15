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
from app.features.trip.models import CargoLine, CargoUnit, Issue, Observation, Pickup, Stop, Trip, TripEvent
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
    CargoUnitOut,
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

def request_hash(payload: dict) -> str:
    return hashlib.sha256(json.dumps(payload, sort_keys=True, ensure_ascii=False, default=str).encode()).hexdigest()




class CommonMixin:
    # ------------------------------------------------------------ 공통 변경 처리

    def _change(self, trip: Trip, body, type_: str, apply, actor_id: str | None, stop_id: str | None = None,
                event_source: str | None = None) -> ChangeResultOut:
        """멱등 키 확인 → 규칙 검사·적용(apply) → 이력 저장. apply 는 (delta, detail) 을 돌려준다.
        event_source 를 주면 이력 출처로 쓴다(기사가 아닌 카메라·샘플이 만든 기록)."""
        payload = body.model_dump(exclude={"idempotency_key"}, mode="json")
        h = request_hash({"type": type_, "stop_id": stop_id, **payload})
        existing = self.repo.event_by_key(trip.id, body.idempotency_key)
        if existing is not None:
            self._check_same(existing.request_hash, h)
            return ChangeResultOut(trip=self._trip_out(trip), replayed=True)
        delta, detail = apply()
        self.repo.add(TripEvent(
            trip_id=trip.id, idempotency_key=body.idempotency_key, request_hash=h, type=type_, stop_id=stop_id,
            delta=delta, detail=detail, source=event_source or body.source, actor_id=actor_id, created_at=self.now(),
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
        return IssueOut(id=i.id, kind=i.kind, zone=i.zone, unit_id=i.unit_id, note=i.note, source=i.source, observed_at=as_utc(i.observed_at),
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
            units=[self._unit_out(u) for u in self.repo.units(trip.id)],
        )

    # ------------------------------------------------------------ 적재 자리 (F12·F13)

    @staticmethod
    def _unit_out(u: CargoUnit) -> CargoUnitOut:
        return CargoUnitOut(
            id=u.id, stop_id=u.stop_id, pickup_id=u.pickup_id, kind=u.kind, item_name=u.item_name, no=u.no, state=u.state, load_seq=u.load_seq,
            zone=u.zone, lane=u.lane, layer=u.layer, planned_zone=u.planned_zone, planned_lane=u.planned_lane, planned_layer=u.planned_layer,
            position_source=u.position_source, updated_at=as_utc(u.updated_at),
        )

    def _add_units(self, trip_id: str, qty: int, *, stop_id: str | None, kind: str, item_name: str, loaded: bool,
                   cargo_line_id: int | None = None, pickup_id: int | None = None, preferred: str | None = None) -> None:
        """빈 칸을 배정해 단위를 만든다. loaded 면 이미 실린 것으로 기록한다 (기사 입력 출처)."""
        existing = self.repo.units(trip_id)
        held: set[rules.Slot] = set()
        for u in existing:
            if u.state == "loaded" and u.zone is not None:
                held.add((u.zone, u.lane, u.layer))
            elif u.state == "planned" and u.planned_zone is not None:
                held.add((u.planned_zone, u.planned_lane, u.planned_layer))
        same = [u.no for u in existing if (pickup_id is not None and u.pickup_id == pickup_id) or (pickup_id is None and u.stop_id == stop_id and u.pickup_id is None)]
        no = max(same, default=0)
        seq = max((u.load_seq for u in existing), default=0)
        for i, slot in enumerate(rules.free_slots(held, qty, preferred), start=1):
            z, lane, layer = slot if slot is not None else (None, None, None)
            self.repo.add(CargoUnit(
                trip_id=trip_id, stop_id=stop_id, cargo_line_id=cargo_line_id, pickup_id=pickup_id, kind=kind, item_name=item_name,
                no=no + i, load_seq=seq + i, state="loaded" if loaded else "planned",
                planned_zone=z, planned_lane=lane, planned_layer=layer,
                zone=z if loaded else None, lane=lane if loaded else None, layer=layer if loaded else None,
                position_source="manual" if loaded else None, updated_at=self.now(),
            ))
        self.s.flush()

    def _unload_units(self, trip_id: str, targets: list[CargoUnit]) -> None:
        """하차·반납이 확정된 단위를 빼고, 남은 단위를 아래로 내려앉힌다."""
        now = self.now()
        for u in targets:
            u.state, u.zone, u.lane, u.layer, u.updated_at = "unloaded", None, None, None, now
        loaded = [u for u in self.repo.units(trip_id) if u.state == "loaded" and u.zone is not None]
        changed = rules.settle_layers([(u.id, u.zone, u.lane, u.layer) for u in loaded])
        for u in loaded:
            if u.id in changed:
                u.layer, u.updated_at = changed[u.id], now


def stop_planned(stop: Stop) -> dict[str, int]:
    out: dict[str, int] = {}
    for l in stop.cargo:
        out[l.kind] = out.get(l.kind, 0) + l.planned_qty - l.delivered_qty
    return out


ZONE_NAMES = {"rear": "뒷문 쪽", "middle": "가운데", "front": "앞쪽"}
_ISSUE_KIND = {"shift": "이동 의심", "fall": "넘어짐 의심", "leak": "누액 의심", "other": "기타 이상"}
_RESOLUTION = {"matched": "관측 일치", "driver_confirmed": "기사 확인", "no_issue": "이상 없음", "rearranged": "재배치함",
               "needs_action": "처리 필요"}

_EVENT_LABEL = {
    "baseline_confirmed": "최초 적재 기준 확정", "stop_completed": "하차 완료", "stop_added": "방문지 추가",
    "stops_reordered": "방문 순서 변경", "stop_place_changed": "방문지 수정", "stop_cancelled": "방문지 취소", "cargo_qty_changed": "물품 수량 변경",
    "trip_ended": "운행 종료", "pickup_added": "회수·추가 상차", "pickup_returned": "회수품 반납",
    "issue_recorded": "적재 이상 의심 기록", "issue_resolved": "이상 처리", "handover_accepted": "인계 인수",
}


def kind_display_names(cargo: list[tuple[str, str]]) -> dict[str, str]:
    """kind → 화면 이름. 한 종류에 품목명이 하나뿐이면 그 이름, 여러 개면 "물품" (무조건 박스가 아니다, 2026-11-15)."""
    names: dict[str, set[str]] = {}
    for kind, item_name in cargo:
        names.setdefault(kind, set()).add(item_name)
    return {k: (next(iter(v)) if len(v) == 1 else GENERIC_ITEM) for k, v in names.items()}


GENERIC_ITEM = "물품"


def summarize_event(e: TripEvent, stop_labels: dict[str, str], kind_names: dict[str, str] | None = None,
                    pickup_names: dict[int, str] | None = None) -> str:
    """이력 한 줄. 수량은 종류 코드(box) 대신 품목명으로 쓴다. 회수품은 그 회수품의 이름을 쓴다."""
    kind_names = kind_names or {}
    item = e.detail.get("item_name") or (pickup_names or {}).get(e.detail.get("pickup_id"))
    name = lambda k: item or kind_names.get(k, GENERIC_ITEM)  # noqa: E731
    parts = [_EVENT_LABEL.get(e.type, e.type)]
    if e.stop_id and e.stop_id in stop_labels:
        parts.append(stop_labels[e.stop_id])
    if e.delta:
        parts.append(" ".join(f"{name(k)} {'+' if v > 0 else ''}{v}" for k, v in e.delta.items()))
    if e.detail.get("kind"):
        if e.type == "cargo_qty_changed":
            parts.append(f"{name(e.detail['kind'])} {e.detail.get('before')}→{e.detail.get('after')}")
        else:
            parts.append(_ISSUE_KIND.get(e.detail["kind"], e.detail["kind"]))
    if e.detail.get("resolution"):
        parts.append(_RESOLUTION.get(e.detail["resolution"], e.detail["resolution"]))
    if e.detail.get("reason"):
        parts.append(f"사유: {e.detail['reason']}")
    return " · ".join(parts)
