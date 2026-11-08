"""음성 업무 조회·요청 (F35~F41, R08). 조회는 즉시 답변, 변경은 요약 후 확인 요청. 저장은 앱이 확인 후 기존 API 로 한다."""

from __future__ import annotations

from sqlalchemy.orm import Session

from app.features.assistant.repository import AssistantRepository
from app.features.trip.service import TripService
from app.integrations.assistant.rule_based import Intent, interpret, parse_item, parse_qty, parse_return_place

METHOD = "rule_based"


def _counts_text(c: dict, names: dict) -> str:
    return ", ".join(f"{names.get(k, k)} {v}개" for k, v in c.items() if v) or "없음"


class AssistantService:
    def __init__(self, session: Session):
        self.s = session
        self.trips = TripService(session)
        self.repo = AssistantRepository(session)

    def handle(self, trip_id: str, text: str, actor: str | None, pending: dict | None) -> dict:
        trip = self.trips.get_trip(trip_id)
        names = {c.kind: c.item_name for st in trip.stops for c in st.cargo} | {"container": "빈 용기"}
        pending_stops = [st for st in trip.stops if st.status == "pending"]
        current = pending_stops[0] if pending_stops else None

        # 재질문에 대한 답: 부족한 항목만 채운다 (F40)
        if pending and pending.get("type") == "add_pickup":
            intent = Intent("add_pickup", dict(pending.get("payload", {})), text)
            for k, fn in (("qty", parse_qty), ("return_place", lambda x: parse_return_place(x, loose=False) or x.strip().rstrip("."))):
                if not intent.slots.get(k):
                    intent.slots[k] = fn(text)
                    break
            if not intent.slots.get("item_name") and (item := parse_item(text)):
                intent.slots["item_name"], intent.slots["kind"] = item
        else:
            intent = interpret(text)

        def answer(speech: str) -> dict:
            return {"kind": "answer", "speech": speech, "display": speech, "action": None, "intent": intent.type, "method": METHOD}

        if intent.type in ("query_next_stop", "query_here"):
            st = current
            if st is None:
                return answer("남은 배송지가 없습니다.")
            left = {c.kind: c.planned_qty - c.delivered_qty for c in st.cargo}
            zone = next((c.zone for c in st.cargo if c.zone), None)
            memos = self.repo.memo.relevant_place_memos(st.place_key, trip_id, actor)
            parts = [f"{st.label} {st.name}에서 {_counts_text(left, names)}를 내립니다."]
            if zone:
                parts.append(f"적재 위치는 {_ZONE.get(zone, zone)}입니다.")
            if memos:
                parts.append(f"메모: {memos[0].body}")
            return answer(" ".join(parts))
        if intent.type == "query_remaining":
            if trip.expected_remaining is None:
                return answer("아직 최초 적재 기준이 확정되지 않았습니다.")
            loaded = [p for p in trip.pickups if p.status == "loaded"]
            s = f"예상 잔량은 {_counts_text(trip.expected_remaining, names)}입니다."
            if loaded:
                s += " 이 중 회수품 " + ", ".join(f"{p.item_name} {p.qty}개" for p in loaded) + "가 있습니다."
            return answer(s)
        if intent.type == "query_memo":
            if current is None:
                return answer("남은 배송지가 없습니다.")
            memos = self.repo.memo.relevant_place_memos(current.place_key, trip_id, actor)
            if not memos:
                return answer(f"{current.label} 장소에 저장된 메모가 없습니다.")
            return answer(f"{current.label} 메모 {len(memos)}건. " + " / ".join(m.body for m in memos[:2]))
        if intent.type == "query_equipment":
            word = intent.slots["word"]
            rows = [e for e in self.repo.fleet.equipment(trip.vehicle_id) if word in e.name]
            if not rows:
                return answer(f"{word} 장비 기록이 없습니다.")
            e = rows[0]
            status = {"ok": "", "missing": " 현재 없음으로 기록되어 있습니다.", "broken": " 고장으로 기록되어 있습니다.",
                      "lent": " 대여 중입니다.", "not_returned": " 미반납 상태입니다."}.get(e.status, "")
            return answer(f"{e.name}은 {e.location}에 있습니다.{status}")
        if intent.type == "add_pickup":
            sl = intent.slots
            if current is not None:
                sl.setdefault("stop_id", current.id)
            missing = [k for k in ("item_name", "qty", "return_place") if not sl.get(k)]
            if missing:
                q = {"item_name": "어떤 물품을 회수했나요?", "qty": "몇 개인가요?", "return_place": "어디로 반납할까요?"}[missing[0]]
                return {"kind": "clarify", "speech": q, "display": q, "missing": missing, "intent": intent.type, "method": METHOD,
                        "action": {"type": "add_pickup", "payload": sl, "summary": None}}
            label = next((st.label for st in trip.stops if st.id == sl.get("stop_id")), "현재 장소")
            summary = f"{label}에서 {sl['item_name']} {sl['qty']}개 회수, {sl['return_place']} 반납 예정으로 기록할까요?"
            return {"kind": "confirm", "speech": summary, "display": summary, "intent": intent.type, "method": METHOD,
                    "action": {"type": "add_pickup", "payload": sl, "summary": summary}}
        if intent.type == "add_memo":
            st = current
            scope = {"scope_type": "place", "scope_id": st.place_key} if st else {"scope_type": "trip", "scope_id": trip_id}
            where = f"{st.label} 장소" if st else "이번 운행"
            payload = {**scope, "body": intent.slots["body"], "visibility": "private", "persistence": "always"}
            summary = f"{where}에 개인 메모로 저장할까요? \"{intent.slots['body']}\""
            return {"kind": "confirm", "speech": summary, "display": summary, "intent": intent.type, "method": METHOD,
                    "action": {"type": "add_memo", "payload": payload, "summary": summary}}
        if intent.type == "navigate_next":
            if current is None:
                return answer("남은 배송지가 없습니다.")
            summary = f"{current.label} {current.name}부터 내비 안내를 보낼까요?"
            return {"kind": "confirm", "speech": summary, "display": summary, "intent": intent.type, "method": METHOD,
                    "action": {"type": "navigate_next", "payload": {"stop_id": current.id}, "summary": summary}}
        msg = "잘 알아듣지 못했어요. 다시 말하거나 화면에서 입력해 주세요."
        return {"kind": "unknown", "speech": msg, "display": msg, "action": None, "intent": "unknown", "method": METHOD}


_ZONE = {"rear": "뒷문 쪽", "middle": "가운데", "front": "앞쪽"}
