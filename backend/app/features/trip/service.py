"""운행 업무 규칙과 트랜잭션 경계 (기획서 22장 Service)."""

from __future__ import annotations

import hashlib
import json
from datetime import datetime, timedelta, timezone

from sqlalchemy.exc import IntegrityError
from sqlalchemy.orm import Session

from app.core.errors import AppError
from app.features.trip import rules
from app.features.trip.models import Observation, Stop, Trip, TripEvent
from app.features.trip.repository import TripRepository
from app.features.trip.schemas import (
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
        )
        self.repo.add(obs)
        self._commit()
        return ObservationCreatedOut(observation=self._obs_out(obs), replayed=False, comparison=self._comparison(trip))

    # ------------------------------------------------------------ 최초 기준 (R02)

    def confirm_baseline(self, trip_id: str, body: BaselineIn) -> BaselineResultOut:
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
            delta=dict(body.counts), detail={"observation_id": body.observation_id}, created_at=self.now(),
        ))
        trip.status = "in_progress"
        self._commit()
        return BaselineResultOut(trip=self._trip_out(trip), replayed=False)

    # ------------------------------------------------------------ 하차 완료 (R04~R07)

    def complete_stop(self, trip_id: str, stop_id: str, body: CompleteIn) -> CompleteResultOut:
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
            created_at=self.now(),
        ))
        if all(st.status != "pending" for st in trip.stops):
            trip.status = "completed"
        self._commit()
        return CompleteResultOut(
            result="completed", replayed=False, stop=self._stop_out(stop),
            expected_remaining=self._expected(trip.id) or {}, comparison=cmp,
        )

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
        return ObservationOut(id=o.id, source=o.source, status=o.status, counts=o.counts, observed_at=as_utc(o.observed_at))

    @staticmethod
    def _stop_out(st: Stop) -> StopOut:
        return StopOut(
            id=st.id, seq=st.seq, label=st.label, name=st.name, address=st.address, latitude=st.latitude,
            longitude=st.longitude, status=st.status, completed_at=as_utc(st.completed_at) if st.completed_at else None,
            resolution=st.resolution,
            cargo=[CargoOut(id=l.id, item_name=l.item_name, kind=l.kind, planned_qty=l.planned_qty, delivered_qty=l.delivered_qty) for l in st.cargo],
        )

    def _trip_out(self, trip: Trip) -> TripOut:
        base = self.repo.baseline_event(trip.id)
        return TripOut(
            id=trip.id, name=trip.name, data_source=trip.data_source, status=trip.status,
            baseline=BaselineOut(counts=base.delta, confirmed_at=as_utc(base.created_at), observation_id=base.detail.get("observation_id")) if base else None,
            stops=[self._stop_out(st) for st in trip.stops],
            expected_remaining=self._expected(trip.id),
            comparison=self._comparison(trip),
        )
