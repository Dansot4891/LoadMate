"""음성 업무 조회·요청 (F35~F41, R08). 조회는 즉시 답변, 변경은 요약 후 확인 요청. 저장은 앱이 확인 후 기존 API 로 한다."""

from __future__ import annotations

import re

from sqlalchemy.orm import Session

from app.features.assistant.repository import AssistantRepository
from app.features.trip.service import TripService
from app.integrations.assistant.note_ai import Fact, NoteAi, NoteAiUnavailable, NoteContext, NoteDecision, PendingTurn
from app.integrations.assistant.rule_based import Intent, interpret, parse_item, parse_qty, parse_return_place

METHOD = "rule_based"
NOT_FOUND = "저장된 기록에서 찾지 못했습니다."


def _counts_text(c: dict, names: dict) -> str:
    return ", ".join(f"{names.get(k, k)} {v}개" for k, v in c.items() if v) or "없음"


def _names(trip) -> dict:
    return {c.kind: c.item_name for st in trip.stops for c in st.cargo} | {"container": "빈 용기"}


def _facts(trip) -> list[Fact]:
    """AI 에 넘기는 운행 정보. 납품처 이름·주소는 넣지 않는다."""
    names = _names(trip)
    pending = [st for st in trip.stops if st.status == "pending"]
    facts = []
    for i, st in enumerate(pending):
        left = {c.kind: c.planned_qty - c.delivered_qty for c in st.cargo}
        zone = next((c.zone for c in st.cargo if c.zone), None)
        where = f", 적재 위치 {_ZONE.get(zone, zone)}" if zone else ""
        facts.append(Fact(f"stop:{st.label}", f"{'현재' if i == 0 else '이후'} 방문지 {st.label}: 내릴 물품 {_counts_text(left, names)}{where}"))
    if not pending:
        facts.append(Fact("stops", "남은 배송지 없음"))
    remaining = "아직 최초 적재 기준 미확정" if trip.expected_remaining is None else _counts_text(trip.expected_remaining, names)
    facts.append(Fact("remaining", f"예상 잔량: {remaining}"))
    facts += [Fact(f"pickup:{p.id}", f"실려 있는 회수품 {p.item_name} {p.qty}개, {p.return_place} 반납 예정") for p in trip.pickups if p.status == "loaded"]
    return facts


def _pickup_reply(trip, current, slots: dict, method: str) -> dict:
    """회수 기록 초안. 부족한 항목은 하나씩 되묻는다 (F40)."""
    if current is not None:
        slots.setdefault("stop_id", current.id)
    missing = [k for k in ("item_name", "qty", "return_place") if not slots.get(k)]
    if missing:
        q = _QUESTION[missing[0]]
        return {"kind": "clarify", "speech": q, "display": q, "missing": missing, "intent": "add_pickup", "method": method,
                "action": {"type": "add_pickup", "payload": slots, "summary": None}}
    label = next((st.label for st in trip.stops if st.id == slots.get("stop_id")), "현재 장소")
    summary = f"{label}에서 {slots['item_name']} {slots['qty']}개 회수, {slots['return_place']} 반납 예정으로 기록할까요?"
    return {"kind": "confirm", "speech": summary, "display": summary, "intent": "add_pickup", "method": method,
            "action": {"type": "add_pickup", "payload": slots, "summary": summary}}


def _navigate_reply(current, method: str) -> dict:
    if current is None:
        return {"kind": "answer", "speech": "남은 배송지가 없습니다.", "display": "남은 배송지가 없습니다.", "action": None, "intent": "navigate_next", "method": method}
    summary = f"{current.label} {current.name}부터 안내를 시작할까요?"
    return {"kind": "confirm", "speech": summary, "display": summary, "intent": "navigate_next", "method": method,
            "action": {"type": "navigate_next", "payload": {"stop_id": current.id}, "summary": summary}}


def _arrived_reply(current, method: str) -> dict:
    """도착했다는 말. 저장하는 것이 없어 확인을 받지 않고, 앱은 적재 화면만 연다 (배송 완료와 무관)."""
    if current is None:
        return {"kind": "answer", "speech": "남은 배송지가 없습니다.", "display": "남은 배송지가 없습니다.", "action": None, "intent": "arrived", "method": method}
    speech = f"{current.label} 도착 확인했습니다."
    return {"kind": "answer", "speech": speech, "display": speech, "intent": "arrived", "method": method,
            "action": {"type": "arrived", "payload": {"stop_id": current.id}, "summary": speech}}


_QUESTION = {"item_name": "어떤 물품을 회수했나요?", "qty": "몇 개인가요?", "return_place": "어디로 반납할까요?"}


def _pending_turn(pending: dict | None) -> PendingTurn | None:
    if not pending or pending.get("type") != "add_pickup":
        return None
    payload = dict(pending.get("payload") or {})
    missing = next((k for k in ("item_name", "qty", "return_place") if not payload.get(k)), None)
    return PendingTurn(_QUESTION.get(missing, "확인할 내용이 있습니다"), "add_pickup", payload)


def _merge_pending(d: NoteDecision, turn: PendingTurn) -> NoteDecision:
    """AI 가 대답에서 읽은 값을 우선하고, 말하지 않은 값은 앞서 정리된 초안 값을 쓴다."""
    p = turn.payload
    return d.model_copy(update={
        "item_name": d.item_name or p.get("item_name"),
        "item_kind": d.item_kind or p.get("kind"),
        "qty": d.qty if d.qty is not None else p.get("qty"),
        "return_place": d.return_place or p.get("return_place"),
    })


class AssistantService:
    def __init__(self, session: Session, note_ai: NoteAi | None = None):
        self.s = session
        self.trips = TripService(session)
        self.repo = AssistantRepository(session)
        self.note_ai = note_ai

    def _notes(self, trip, current, actor: str | None) -> list:
        """(scope, memo) 목록."""
        vehicle = self.repo.memo.vehicle_notes(trip.vehicle_id, actor) if trip.vehicle_id else []
        place = self.repo.memo.relevant_place_memos(current.place_key, trip.id, actor) if current else []
        return [("vehicle", m) for m in vehicle] + [("current_place", m) for m in place]

    def handle(self, trip_id: str, text: str, actor: str | None, pending: dict | None, pending_kind: str = "clarify") -> dict:
        if pending and pending_kind == "confirm":
            return self._confirm_answer(text, pending)
        ai_status = "not_configured" if self.note_ai is None else "ok"
        if self.note_ai is not None:
            trip = self.trips.get_trip(trip_id)
            current = next((st for st in trip.stops if st.status == "pending"), None)
            notes = self._notes(trip, current, actor)
            context = [NoteContext(m.id, scope, m.category, m.body, m.location) for scope, m in notes]
            turn = _pending_turn(pending)
            try:
                facts = _facts(trip)
                decision = self.note_ai.decide(text, context, current.label if current else None, facts, turn)
            except NoteAiUnavailable:
                ai_status = "unavailable"
            else:
                if turn is not None and decision.action == "add_pickup":
                    decision = _merge_pending(decision, turn)
                reply = self._ai_reply(decision, trip, current, [m for _, m in notes], facts)
                if reply is not None:
                    return {**reply, "ai_status": "ok"}
        # AI 를 쓸 수 없거나 AI 가 판단하지 못한 말(other)은 규칙 기반으로 처리하고 ai_status 로 알린다
        return {**self._by_rules(trip_id, text, actor, pending), "ai_status": ai_status}

    def _confirm_answer(self, text: str, pending: dict) -> dict:
        """저장 확인 창에 대한 기사의 대답. 저장은 하지 않고 판단만 돌려준다. 앱이 yes 일 때 기존 API 로 저장한다 (R08)."""
        base = {"kind": "confirm_answer", "speech": "", "display": "", "action": pending, "intent": "confirm_answer"}
        if self.note_ai is None:
            return {**base, "decision": "unclear", "method": METHOD, "ai_status": "not_configured"}
        try:
            d = self.note_ai.confirm(text, pending.get("summary") or "")
        except NoteAiUnavailable:
            return {**base, "decision": "unclear", "method": METHOD, "ai_status": "unavailable"}
        return {**base, "decision": d.decision, "method": "ai", "ai_status": "ok"}

    @staticmethod
    def _ai_reply(d: NoteDecision, trip, current, notes: list, facts: list[Fact]) -> dict | None:
        """AI 초안을 저장된 기록과 대조해 응답으로 만든다. 확인 문구는 AI 문장이 아니라 실제 저장될 값으로 만든다."""
        def reply(kind: str, speech: str, display: str | None = None, action: dict | None = None) -> dict:
            return {"kind": kind, "speech": speech, "display": display or speech, "action": action, "intent": f"note_{d.action}", "method": "ai"}

        by_id = {m.id: m for m in notes}
        if d.action == "other":
            return None
        if d.action == "ignore":
            return {"kind": "ignore", "speech": "", "display": "", "action": None, "intent": "ignore", "method": "ai"}
        if d.action == "navigate_next":
            return _navigate_reply(current, "ai")
        if d.action == "arrived":
            return _arrived_reply(current, "ai")
        if d.action == "add_pickup":
            valid_qty = d.qty if d.qty is not None and 1 <= d.qty <= 999 else None
            # 종류를 모르면 저장할 수 없으므로 물품부터 다시 묻는다
            return _pickup_reply(trip, current, {"item_name": d.item_name if d.item_kind else None, "kind": d.item_kind,
                                                 "qty": valid_qty, "return_place": d.return_place}, "ai")
        if d.action == "answer":
            sources = [by_id[i] for i in d.source_memo_ids if i in by_id]
            fact_by_id = {f.id: f.text for f in facts}
            cited_facts = [fact_by_id[i] for i in d.source_fact_ids if i in fact_by_id]
            if not sources and not cited_facts:
                return reply("answer", NOT_FOUND)
            cited = " / ".join([m.body + (f" ({m.location})" if m.location else "") for m in sources] + cited_facts)
            # 수량은 서버 값이 기준이다. 근거에 없는 숫자를 말하면 AI 문장을 쓰지 않는다
            grounded = d.speech.strip() and set(re.findall(r"\d+", d.speech)) <= set(re.findall(r"\d+", cited))
            speech = d.speech if grounded else cited
            return reply("answer", speech, f"{speech} · 기록: {cited}")
        if d.action == "add":
            if not d.body:
                return None
            if d.category == "place" and current is not None:
                scope, where = {"scope_type": "place", "scope_id": current.place_key}, f"{current.label} 장소"
            elif trip.vehicle_id:
                scope, where = {"scope_type": "vehicle", "scope_id": trip.vehicle_id}, "차량 메모"
            else:
                scope, where = {"scope_type": "trip", "scope_id": trip.id}, "이번 운행"
            payload = {**scope, "body": d.body, "visibility": "shared", "persistence": "always",
                       "category": d.category or "other", "location": d.location}
            summary = f"{where}에 저장할까요? \"{d.body}\"" + (f" 위치: {d.location}" if d.location else "")
            return reply("confirm", summary, action={"type": "add_memo", "payload": payload, "summary": summary})
        target = by_id.get(d.memo_id) if d.memo_id is not None else None
        if target is None:
            return reply("answer", NOT_FOUND)
        if d.action == "delete":
            summary = f"이 메모를 삭제할까요? \"{target.body}\""
            return reply("confirm", summary, action={"type": "delete_memo", "payload": {"memo_id": target.id}, "summary": summary})
        before = target.body + (f" ({target.location})" if target.location else "")
        after = (d.body or target.body) + (f" ({d.location or target.location})" if d.location or target.location else "")
        summary = f"\"{before}\"을 \"{after}\"로 바꿀까요?"
        payload = {"memo_id": target.id, "body": d.body, "category": d.category, "location": d.location}
        return reply("confirm", summary, action={"type": "update_memo", "payload": payload, "summary": summary})

    def _by_rules(self, trip_id: str, text: str, actor: str | None, pending: dict | None) -> dict:
        trip = self.trips.get_trip(trip_id)
        names = _names(trip)
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
        if intent.type == "query_memo" and intent.slots.get("target") == "vehicle":
            rows = [m for scope, m in self._notes(trip, current, actor) if scope == "vehicle" and m.category != "tool"]
            if not rows:
                return answer("차량에 저장된 주의 사항이 없습니다.")
            return answer(f"차량 확인 {len(rows)}건. " + " / ".join(m.body for m in rows[:3]))
        if intent.type == "query_memo":
            if current is None:
                return answer("남은 배송지가 없습니다.")
            memos = self.repo.memo.relevant_place_memos(current.place_key, trip_id, actor)
            if not memos:
                return answer(f"{current.label} 장소에 저장된 메모가 없습니다.")
            return answer(f"{current.label} 메모 {len(memos)}건. " + " / ".join(m.body for m in memos[:2]))
        if intent.type == "query_note":
            word = intent.slots["word"]
            rows = [m for _, m in self._notes(trip, current, actor) if word in m.body or word in (m.location or "")]
            if not rows:
                return answer(f"{word} 기록이 없습니다.")
            m = rows[0]
            return answer(m.body + (f". 위치는 {m.location}입니다." if m.location else ""))
        if intent.type == "add_pickup":
            return _pickup_reply(trip, current, intent.slots, METHOD)
        if intent.type == "add_memo":
            st = current
            scope = {"scope_type": "place", "scope_id": st.place_key} if st else {"scope_type": "trip", "scope_id": trip_id}
            where = f"{st.label} 장소" if st else "이번 운행"
            payload = {**scope, "body": intent.slots["body"], "visibility": "shared", "persistence": "always"}
            summary = f"{where}에 메모로 저장할까요? \"{intent.slots['body']}\""
            return {"kind": "confirm", "speech": summary, "display": summary, "intent": intent.type, "method": METHOD,
                    "action": {"type": "add_memo", "payload": payload, "summary": summary}}
        if intent.type == "navigate_next":
            return _navigate_reply(current, METHOD)
        if intent.type == "arrived":
            return _arrived_reply(current, METHOD)
        msg = "잘 알아듣지 못했어요. 다시 말하거나 화면에서 입력해 주세요."
        return {"kind": "unknown", "speech": msg, "display": msg, "action": None, "intent": "unknown", "method": METHOD}


_ZONE = {"rear": "뒷문 쪽", "middle": "가운데", "front": "앞쪽"}
