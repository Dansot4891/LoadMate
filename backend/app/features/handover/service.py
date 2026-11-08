"""인계 요약·인계·인수 (F59~F62, R12·R13)."""

from __future__ import annotations


from sqlalchemy import or_, select
from sqlalchemy.orm import Session

from app.core.errors import AppError
from app.core.time import iso_utc, utcnow
from app.features.fleet.models import Driver, Equipment
from app.features.handover.models import Handover
from app.features.memo.models import Memo
from app.features.trip.models import TripEvent
from app.features.trip.service import ZONE_NAMES, TripService, request_hash

_ISSUE = {"shift": "적재물 이동 의심", "fall": "넘어짐 의심", "leak": "누액 의심", "other": "기타 이상"}
_EQUIP = {"missing": "없음", "broken": "고장", "lent": "대여 중", "not_returned": "미반납"}


class HandoverService:
    def __init__(self, session: Session):
        self.s = session
        self.trips = TripService(session)

    def preview(self, trip_id: str) -> dict:
        """공유 대상 기록과 미완료 업무만 요약한다. 개인 기록은 자동 포함하지 않는다 (R12)."""
        t = self.trips.get_trip(trip_id)
        names = {d.id: d.name for d in self.s.scalars(select(Driver))}
        kind_names = {c.kind: c.item_name for st in t.stops for c in st.cargo}
        kn = lambda k: kind_names.get(k, k)  # noqa: E731
        remaining = [
            {"label": st.label, "name": st.name,
             "cargo": {c.kind: c.planned_qty - c.delivered_qty for c in st.cargo if c.planned_qty > c.delivered_qty}}
            for st in t.stops if st.status == "pending"
        ]
        pickups = [{"item_name": p.item_name, "qty": p.qty, "return_place": p.return_place} for p in t.pickups if p.status == "loaded"]
        issues = [{"kind": i.kind, "zone": i.zone, "note": i.note} for i in t.issues if i.status == "open"]
        equipment = []
        if t.vehicle_id:
            for e in self.s.scalars(select(Equipment).where(Equipment.vehicle_id == t.vehicle_id, Equipment.status != "ok")):
                equipment.append({"name": e.name, "status": e.status, "location": e.location, "note": e.note})
        place_keys = [st.place_key for st in t.stops if st.status == "pending"]
        memo_q = select(Memo).where(Memo.visibility == "shared").where(
            or_(Memo.scope_type == "trip", Memo.scope_type == "vehicle", Memo.scope_id.in_(place_keys))
        ).where(or_(Memo.persistence == "always", Memo.trip_id == trip_id))
        memos = [{"scope_type": m.scope_type, "scope_id": m.scope_id, "body": m.body, "author": names.get(m.author_id, m.author_id)}
                 for m in self.s.scalars(memo_q.order_by(Memo.updated_at.desc()))]

        lines = []
        if remaining:
            lines.append("남은 배송: " + ", ".join(
                f"{r['label']} {r['name']} " + " ".join(f"{kn(k)} {v}" for k, v in r["cargo"].items()) for r in remaining))
        else:
            lines.append("남은 배송 없음")
        if pickups:
            lines.append("반납 예정 회수품: " + ", ".join(f"{p['item_name']} {p['qty']} → {p['return_place']}" for p in pickups))
        if issues:
            lines.append("미해결 문제: " + ", ".join(f"{_ISSUE.get(i['kind'], i['kind'])}" + (f"({ZONE_NAMES.get(i['zone'], i['zone'])})" if i["zone"] else "") for i in issues))
        if equipment:
            lines.append("장비 확인: " + ", ".join(f"{e['name']} {_EQUIP.get(e['status'], e['status'])}" for e in equipment))
        if memos:
            lines.append(f"공유 메모 {len(memos)}건")
        lines.append("개인 기록은 포함하지 않았습니다.")
        return {
            "trip_id": trip_id, "current_driver_id": t.driver_id, "expected_remaining": t.expected_remaining,
            "remaining_stops": remaining, "pickups_loaded": pickups, "open_issues": issues,
            "equipment_alerts": equipment, "shared_memos": memos,
            "summary_lines": lines, "summary_method": "rule_based",
        }

    def create(self, trip_id: str, key: str, to_driver_id: str, note: str, actor: str | None) -> dict:
        if not actor:
            raise AppError(422, "driver_required", "기사를 먼저 선택하세요 (X-Driver-Id)")
        trip = self.trips._trip(trip_id, for_update=True)
        existing = self.s.scalars(select(Handover).where(Handover.trip_id == trip_id, Handover.idempotency_key == key)).first()
        h = request_hash({"to": to_driver_id, "note": note})
        if existing is not None:
            self.trips._check_same(request_hash({"to": existing.to_driver_id, "note": existing.note}), h)
            return {"handover": self._out(existing), "replayed": True}
        if trip.driver_id and trip.driver_id != actor:
            raise AppError(403, "not_current_driver", "현재 운행 기사만 인계할 수 있습니다")
        if self.s.get(Driver, to_driver_id) is None or to_driver_id == actor:
            raise AppError(422, "invalid_to_driver", "인수할 다른 기사를 선택하세요")
        if self.s.scalars(select(Handover).where(Handover.trip_id == trip_id, Handover.status == "pending")).first():
            raise AppError(409, "handover_pending", "인수 대기 중인 인계가 있습니다")
        ho = Handover(trip_id=trip_id, idempotency_key=key, from_driver_id=actor, to_driver_id=to_driver_id, note=note,
                      summary=self.preview(trip_id), created_at=utcnow())
        self.s.add(ho)
        self.trips._commit()
        return {"handover": self._out(ho), "replayed": False}

    def latest(self, trip_id: str) -> dict:
        self.trips._trip(trip_id)
        ho = self.s.scalars(select(Handover).where(Handover.trip_id == trip_id).order_by(Handover.id.desc())).first()
        return {"handover": self._out(ho) if ho else None}

    def accept(self, handover_id: int, actor: str | None) -> dict:
        ho = self.s.get(Handover, handover_id)
        if ho is None:
            raise AppError(404, "handover_not_found", "인계를 찾을 수 없습니다")
        if ho.status == "accepted":
            return {"handover": self._out(ho), "replayed": True}
        if actor != ho.to_driver_id:
            raise AppError(403, "not_handover_target", "인수 대상 기사만 인수할 수 있습니다")
        trip = self.trips._trip(ho.trip_id, for_update=True)
        trip.driver_id = ho.to_driver_id
        ho.status = "accepted"
        ho.accepted_at = utcnow()
        self.s.add(TripEvent(trip_id=trip.id, idempotency_key=f"handover-{ho.id}", request_hash="-", type="handover_accepted",
                             delta={}, detail={"from": ho.from_driver_id, "to": ho.to_driver_id}, actor_id=actor,
                             created_at=ho.accepted_at))
        self.trips._commit()
        return {"handover": self._out(ho), "replayed": False}

    @staticmethod
    def _out(ho: Handover) -> dict:
        return {"id": ho.id, "trip_id": ho.trip_id, "from_driver_id": ho.from_driver_id, "to_driver_id": ho.to_driver_id,
                "note": ho.note, "summary": ho.summary, "status": ho.status, "created_at": iso_utc(ho.created_at), "accepted_at": iso_utc(ho.accepted_at)}
