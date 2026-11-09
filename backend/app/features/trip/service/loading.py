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

class LoadingMixin:
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
        # 기사가 적재를 확정했으므로 소식이 없던 단위도 계획 자리에 실린 것으로 기록한다
        for u in self.repo.units(trip.id):
            if u.state == "planned":
                u.state, u.zone, u.lane, u.layer = "loaded", u.planned_zone, u.planned_lane, u.planned_layer
                u.position_source, u.updated_at = "manual", self.now()
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

        expected_after = {k: max(v, 0) for k, v in rules.add_counts(self._expected(trip.id) or {}, body.delivered, sign=-1).items()}
        cmp = self._compare(trip.id, expected_after)
        if cmp.state != "match" and body.resolution is None:
            # 반영하지 않음. 키도 소비하지 않는다 (api-contract 공통 규칙)
            return CompleteResultOut(
                result="needs_confirmation", replayed=False, stop=self._stop_out(stop),
                expected_remaining=self._expected(trip.id) or {}, comparison=cmp,
            )

        self._apply_delivery(stop, body.delivered)
        self._unload_units(trip.id, self._delivered_units(trip.id, stop.id, body.delivered))
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

    def _delivered_units(self, trip_id: str, stop_id: str, delivered: dict[str, int]) -> list:
        """내릴 단위: 위층부터, 나중에 실린 것부터 (손이 먼저 닿는 순서)."""
        out = []
        loaded = [u for u in self.repo.units(trip_id) if u.stop_id == stop_id and u.pickup_id is None and u.state == "loaded"]
        for kind, n in delivered.items():
            same = sorted((u for u in loaded if u.kind == kind), key=lambda u: (u.layer or 0, u.load_seq), reverse=True)
            out.extend(same[:n])
        return out
