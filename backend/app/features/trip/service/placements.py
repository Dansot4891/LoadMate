"""적재 자리 소식 (F12·F13). 카메라·샘플·기사 입력이 같은 경로로 보낸다. 잔량·완료는 바꾸지 않는다 (원칙 2)."""

from __future__ import annotations

from app.core.errors import AppError
from app.core.time import as_utc
from app.features.trip.models import CargoUnit, PlacementRequest
from app.features.trip.schemas import ChangeResultOut, PlacementsIn
from app.features.trip.service.common import request_hash


class PlacementsMixin:
    def record_placements(self, trip_id: str, body: PlacementsIn) -> ChangeResultOut:
        trip = self._trip(trip_id, for_update=True)
        h = request_hash(body.model_dump(exclude={"idempotency_key"}, mode="json"))
        existing = self.repo.placement_by_key(trip.id, body.idempotency_key)
        if existing is not None:
            self._check_same(existing.request_hash, h)
            return ChangeResultOut(trip=self._trip_out(trip), replayed=True)
        self._require_open(trip)

        units = {u.id: u for u in self.repo.units(trip.id)}
        taken = {(u.zone, u.lane, u.layer): u.id for u in units.values() if u.state == "loaded" and u.zone is not None}
        locked = self.repo.baseline_event(trip.id) is not None
        now = self.now()

        def occupy(u: CargoUnit, slot: tuple[str, int, int]) -> None:
            holder = taken.get(slot)
            if holder is not None and holder != u.id:
                raise AppError(409, "slot_occupied", "그 자리에는 이미 다른 물품이 기록되어 있습니다")
            taken.pop((u.zone, u.lane, u.layer), None)
            taken[slot] = u.id
            u.zone, u.lane, u.layer = slot

        for e in body.events:
            u = units.get(e.unit_id)
            if u is None:
                raise AppError(404, "unit_not_found", "물품 단위를 찾을 수 없습니다")
            given = (e.slot.zone, e.slot.lane, e.slot.layer) if e.slot is not None else None
            if e.type == "placed":
                if u.state != "planned":
                    raise AppError(409, "unit_state_conflict", "이미 실린 물품입니다")
                slot = given or ((u.planned_zone, u.planned_lane, u.planned_layer) if u.planned_zone is not None else None)
                if slot is None:
                    raise AppError(422, "slot_required", "자리를 지정해야 합니다")
                occupy(u, slot)
                u.state = "loaded"
            elif e.type == "moved":
                if u.state != "loaded":
                    raise AppError(409, "unit_state_conflict", "실린 물품만 옮길 수 있습니다")
                if given is None:
                    raise AppError(422, "slot_required", "자리를 지정해야 합니다")
                occupy(u, given)
            elif e.type == "removed":
                if locked:
                    raise AppError(409, "placement_locked", "기준 확정 후 물품을 빼는 것은 하차 완료로만 기록합니다")
                if u.state != "loaded":
                    raise AppError(409, "unit_state_conflict", "실린 물품만 되돌릴 수 있습니다")
                taken.pop((u.zone, u.lane, u.layer), None)
                u.state, u.zone, u.lane, u.layer = "planned", None, None, None
            else:  # accepted
                if u.state != "loaded":
                    raise AppError(409, "unit_state_conflict", "실린 물품만 자리를 인정할 수 있습니다")
                u.planned_zone, u.planned_lane, u.planned_layer = u.zone, u.lane, u.layer
            u.position_source = body.source if u.state == "loaded" else None
            u.updated_at = now

        self.repo.add(PlacementRequest(
            trip_id=trip.id, idempotency_key=body.idempotency_key, request_hash=h, source=body.source,
            events=[e.model_dump(mode="json") for e in body.events], observed_at=as_utc(body.observed_at), created_at=now,
        ))
        self._commit()
        return ChangeResultOut(trip=self._trip_out(trip), replayed=False)
