"""차량 노트와 메모 AI. 실제 AI 호출 없이 어댑터 자리에 가짜를 넣어 서버 규칙을 검증한다."""

import json
import uuid

import httpx2
import pytest

from app.integrations.assistant.note_ai import Fact, GeminiNoteAi, NoteAiUnavailable, NoteClassification, NoteDecision, get_note_ai

T = "sample-trip-1"
V = "vehicle-1"


def decision(action, **kw):
    return NoteDecision(**{"action": action, "speech": "", "source_memo_ids": [], "source_fact_ids": [], "memo_id": None, "body": None, "category": None, "location": None,
                           "item_name": None, "item_kind": None, "qty": None, "return_place": None, **kw})


class FakeAi:
    def __init__(self, result=None, error=False):
        self.result, self.error, self.calls = result, error, []

    def decide(self, text, notes, stop_label, facts):
        self.calls.append((text, notes, stop_label, facts))
        if self.error:
            raise NoteAiUnavailable
        return self.result

    def classify(self, body):
        if self.error:
            raise NoteAiUnavailable
        return NoteClassification(category="issue", location="뒷좌석")


@pytest.fixture
def ai(client):
    fake = FakeAi()
    client.app.dependency_overrides[get_note_ai] = lambda: fake
    return fake


def ask(client, text):
    return client.post("/assistant/interpret", json={"trip_id": T, "text": text, "pending": None}).json()


def notes(client):
    return client.get("/memos", params={"scope_type": "vehicle", "scope_id": V}).json()["memos"]


def note_id(client, word):
    return next(n["id"] for n in notes(client) if word in n["body"])


def add(client, body="공구함 잠금 고장", **kw):
    return client.post("/memos", json={"idempotency_key": str(uuid.uuid4()), "scope_type": "vehicle", "scope_id": V, "body": body, "visibility": "shared", **kw})


def test_shared_note_needs_no_driver_and_private_still_does(client):
    r = add(client, category="tool", location="앞쪽")
    assert r.status_code == 201 and r.json()["memo"]["author_id"] == "shared" and r.json()["memo"]["location"] == "앞쪽"
    private = client.post("/memos", json={"idempotency_key": str(uuid.uuid4()), "scope_type": "vehicle", "scope_id": V, "body": "x"})
    assert private.status_code == 422


def test_note_without_ai_is_saved_as_other(client):
    assert add(client).json()["memo"]["category"] == "other"


def test_note_is_classified_by_ai_and_failure_does_not_block_save(client, ai):
    m = add(client).json()["memo"]
    assert m["category"] == "issue" and m["location"] == "뒷좌석"
    ai.error = True
    assert add(client, "다른 메모").json()["memo"]["category"] == "other"


def test_edit_and_delete(client):
    i = note_id(client, "끊어짐")
    assert "resolved_at" not in client.get(f"/memos?scope_type=vehicle&scope_id=vehicle-1").json()["memos"][0]
    assert client.put(f"/memos/{i}", json={"location": ""}).json()["location"] is None
    assert client.delete(f"/memos/{i}").json() == {"deleted": True}
    assert client.delete(f"/memos/{i}").status_code == 404


def test_ai_answer_must_cite_saved_note(client, ai):
    i = note_id(client, "핸드카트")
    ai.result = decision("answer", speech="뒷문 오른쪽에 있습니다.", source_memo_ids=[i])
    r = ask(client, "핸드카트 어딨어")
    assert r["method"] == "ai" and r["speech"] == "뒷문 오른쪽에 있습니다." and "핸드카트" in r["display"]
    ai.result = decision("answer", speech="창고 선반에 있습니다.", source_memo_ids=[99999])
    r = ask(client, "빨간 테이프 어딨어")
    assert r["speech"] == "저장된 기록에서 찾지 못했습니다."


def test_ai_context_excludes_customer_place_names(client, ai):
    i = note_id(client, "끊어짐")
    ai.result = decision("other")
    ask(client, "다음 장소에 뭐 내려야 해")
    _, context, stop, facts = ai.calls[0]
    assert stop == "A" and i in [n.id for n in context]
    assert not any("판교" in repr(n) for n in context + facts)


def test_ai_add_needs_confirmation_and_saves_nothing(client, ai):
    before = len(notes(client))
    ai.result = decision("add", body="빨간 테이프", category="tool", location="조수석 서랍")
    r = ask(client, "빨간 테이프 조수석 서랍에 뒀어")
    assert r["kind"] == "confirm" and r["action"]["type"] == "add_memo"
    assert r["action"]["payload"]["scope_type"] == "vehicle" and r["action"]["payload"]["visibility"] == "shared"
    assert "조수석 서랍" in r["speech"] and len(notes(client)) == before


def test_ai_place_note_goes_to_current_stop(client, ai):
    ai.result = decision("add", body="후문 진입", category="place")
    assert ask(client, "여기 후문으로 들어가")["action"]["payload"]["scope_id"] == "place-pangyo"


def test_ai_update_and_delete_show_real_values_and_reject_unknown_id(client, ai):
    i = note_id(client, "핸드카트")
    ai.result = decision("update", memo_id=i, location="조수석 뒤", speech="AI 문장")
    r = ask(client, "핸드카트 조수석 뒤로 옮겼어")
    assert r["action"]["type"] == "update_memo" and r["action"]["payload"]["memo_id"] == i
    assert "적재함 뒷문 오른쪽" in r["speech"] and "조수석 뒤" in r["speech"] and "AI 문장" not in r["speech"]
    before = len(notes(client))
    ai.result = decision("delete", memo_id=i)
    r = ask(client, "핸드카트 메모 지워")
    assert r["kind"] == "confirm" and r["action"]["type"] == "delete_memo" and r["action"]["payload"] == {"memo_id": i}
    assert "삭제" in r["speech"] and "핸드카트" in r["speech"] and len(notes(client)) == before
    ai.result = decision("update", memo_id=99999, location="x")
    assert ask(client, "바꿔")["kind"] == "answer"


def test_ai_other_and_outage_fall_back_to_rules_with_status(client, ai):
    ai.result = decision("other")
    r = ask(client, "다음 장소에 뭘 내려야 해?")
    assert r["method"] == "rule_based" and "박스 3개" in r["speech"] and r["ai_status"] == "ok"
    ai.error = True
    r = ask(client, "핸드카트 어디 있어")
    assert r["method"] == "rule_based" and r["ai_status"] == "unavailable" and "뒷문 오른쪽" in r["speech"]


def test_ai_answers_trip_questions_only_from_cited_facts(client, ai):
    ai.result = decision("answer", speech="A에서 박스 3개 내리시면 됩니다.", source_fact_ids=["stop:A"])
    r = ask(client, "여기서 뭐 내려")
    assert r["method"] == "ai" and r["speech"] == "A에서 박스 3개 내리시면 됩니다." and "박스 3개" in r["display"]
    ai.result = decision("answer", speech="박스 3개입니다.", source_fact_ids=["stop:Z"])
    assert ask(client, "뭐 내려")["speech"] == "저장된 기록에서 찾지 못했습니다."


def test_ai_number_not_in_cited_fact_is_replaced_by_server_value(client, ai):
    ai.result = decision("answer", speech="A에서 박스 99개 내리세요.", source_fact_ids=["stop:A"])
    r = ask(client, "여기서 뭐 내려")
    assert "99" not in r["speech"] and "박스 3개" in r["speech"]


def test_ai_pickup_needs_confirmation_and_asks_for_missing(client, ai):
    ai.result = decision("add_pickup", item_name="빈 용기", item_kind="container", qty=2, return_place="창고", speech="AI 문장")
    r = ask(client, "빈 용기 두 개 회수했어 창고로 반납해")
    assert r["kind"] == "confirm" and r["method"] == "ai" and r["action"]["type"] == "add_pickup" and "AI 문장" not in r["speech"]
    assert r["action"]["payload"]["stop_id"] == "stop-a" and r["action"]["payload"]["kind"] == "container"
    assert client.get(f"/trips/{T}").json()["pickups"] == []
    ai.result = decision("add_pickup", item_name="빈 용기", item_kind="container", qty=2)
    r = ask(client, "빈 용기 두 개 회수했어")
    assert r["kind"] == "clarify" and r["missing"] == ["return_place"]
    ai.result = decision("add_pickup", item_name="무언가", qty=0, return_place="창고")
    assert ask(client, "뭔가 회수했어")["missing"] == ["item_name", "qty"]


def test_ai_arrived_opens_cargo_without_saving(client, ai):
    before = client.get(f"/trips/{T}").json()
    ai.result = decision("arrived")
    r = ask(client, "도착했어")
    assert r["kind"] == "answer" and r["action"] == {"type": "arrived", "payload": {"stop_id": "stop-a"}, "summary": r["speech"]}
    assert client.get(f"/trips/{T}").json() == before


def test_ai_navigate_needs_confirmation(client, ai):
    ai.result = decision("navigate_next")
    r = ask(client, "다음 장소로 안내해 줘")
    assert r["kind"] == "confirm" and r["action"] == {"type": "navigate_next", "payload": {"stop_id": "stop-a"}, "summary": r["speech"]}


def test_ai_ignore_stays_silent(client, ai):
    ai.result = decision("ignore", speech="네")
    r = ask(client, "아 오늘 진짜 덥네")
    assert r["kind"] == "ignore" and r["speech"] == "" and r["action"] is None and r["ai_status"] == "ok"


def test_pending_clarify_answer_does_not_call_ai(client, ai):
    pending = {"type": "add_pickup", "payload": {"item_name": "빈 용기", "kind": "container", "qty": 2}, "summary": None}
    r = client.post("/assistant/interpret", json={"trip_id": T, "text": "창고", "pending": pending}).json()
    assert r["kind"] == "confirm" and r["method"] == "rule_based" and ai.calls == []


def _gemini_with(handler):
    return GeminiNoteAi("test-key", "gemini-3.5-flash-lite", 5, httpx2.Client(transport=httpx2.MockTransport(handler)))


def _reply(text):
    return httpx2.Response(200, json={"id": "i1", "status": "completed", "steps": [{"type": "model_output", "content": [{"type": "text", "text": text}]}]})


def test_gemini_adapter_request_and_parse():
    seen = {}

    def handler(request):
        seen["body"] = json.loads(request.content)
        return _reply(json.dumps({"action": "answer", "speech": "있음", "source_memo_ids": [1], "source_fact_ids": [], "memo_id": None, "body": None, "category": None, "location": None,
                                  "item_name": None, "item_kind": None, "qty": None, "return_place": None}))

    d = _gemini_with(handler).decide("어딨어", [], None, [Fact("remaining", "예상 잔량: 박스 10개")])
    assert d.action == "answer" and d.source_memo_ids == [1]
    b = seen["body"]
    assert b["model"] == "gemini-3.5-flash-lite" and "업무 도우미" in b["system_instruction"]
    assert "예상 잔량: 박스 10개" in json.dumps(b["input"], ensure_ascii=False)
    assert b["response_format"]["mime_type"] == "application/json" and "action" in b["response_format"]["schema"]["properties"]


@pytest.mark.parametrize("handler", [
    lambda r: httpx2.Response(429, json={"error": {"code": 429, "message": "quota", "status": "RESOURCE_EXHAUSTED"}}),
    lambda r: httpx2.Response(503, json={"error": {"code": 503, "message": "busy", "status": "UNAVAILABLE"}}),
    lambda r: _reply("형식이 틀린 답"),
])
def test_gemini_adapter_failures_are_unavailable(handler):
    with pytest.raises(NoteAiUnavailable):
        _gemini_with(handler).classify("a")


def test_gemini_adapter_timeout_is_unavailable():
    def handler(request):
        raise httpx2.ReadTimeout("slow", request=request)

    with pytest.raises(NoteAiUnavailable):
        _gemini_with(handler).classify("a")


def test_rules_read_vehicle_notes_for_this_car(client):
    r = ask(client, "이 차 주의할 거 있어")
    assert r["kind"] == "answer" and "끊어짐" in r["speech"] and "핸드카트" not in r["speech"]
