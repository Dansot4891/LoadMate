"""음성 조회·요청 (F35~F41, R08). 규칙 기반 해석기."""

import uuid

import pytest

from app.integrations.assistant.rule_based import interpret, parse_qty

T = "sample-trip-1"
D1 = {"X-Driver-Id": "driver-1"}


def ask(client, text, pending=None):
    return client.post("/assistant/interpret", json={"trip_id": T, "text": text, "pending": pending}, headers=D1).json()


def baseline(client):
    client.post(f"/trips/{T}/baseline", json={"idempotency_key": str(uuid.uuid4()), "counts": {"box": 10}}, headers=D1)


@pytest.mark.parametrize("text,intent", [
    ("다음 장소에 뭘 내려야 해?", "query_next_stop"),  # 에뮬레이터 STT 실제 출력 (2026-11-08)
    ("다음 장소에 뭐 내려야 해", "query_next_stop"),
    ("여기서 뭐 내려", "query_here"),
    ("지금 몇 개 남았어", "query_remaining"),
    ("핸드카트 어디 있어", "query_equipment"),
    ("여기 주의할 거 있어", "query_memo"),
    ("여기서 빈 용기 두 개 회수했어", "add_pickup"),
    ("다음부터 여기는 후문으로 들어와", "add_memo"),
    ("다음 장소 안내해줘", "navigate_next"),
    ("오늘 날씨 어때", "unknown"),
])
def test_interpret_intents(text, intent):
    assert interpret(text).type == intent


def test_parse_numbers():
    assert parse_qty("두 개") == 2 and parse_qty("3개") == 3 and parse_qty("열 개") == 10 and parse_qty("용기") is None


def test_query_next_stop_answers_items_zone_and_memo(client):
    r = ask(client, "다음 장소에 뭘 내려야 해?")
    assert r["kind"] == "answer" and r["method"] == "rule_based" and r["action"] is None
    assert "A" in r["speech"] and "박스 3개" in r["speech"] and "뒷문 쪽" in r["speech"]


def test_query_remaining_before_and_after_baseline(client):
    assert "확정되지" in ask(client, "몇 개 남았어")["speech"]
    baseline(client)
    assert "박스 10개" in ask(client, "몇 개 남았어")["speech"]


def test_equipment_location(client):
    assert "적재함 뒷문 오른쪽" in ask(client, "카트 어디 있어")["speech"]


def test_pickup_full_utterance_needs_confirmation(client):
    baseline(client)
    r = ask(client, "여기서 빈 용기 두 개 회수했어 창고로 반납해")
    assert r["kind"] == "confirm"
    assert r["action"]["type"] == "add_pickup"
    want = {"item_name": "빈 용기", "kind": "container", "qty": 2, "return_place": "창고", "stop_id": "stop-a"}
    assert {k: r["action"]["payload"][k] for k in want} == want
    # 조회·확인 요청만으로는 아무것도 저장되지 않는다 (R08)
    assert client.get(f"/trips/{T}").json()["pickups"] == []


def test_pickup_missing_return_place_asks_only_that(client):
    baseline(client)
    r = ask(client, "빈 용기 두 개 회수했어")
    assert r["kind"] == "clarify" and r["missing"] == ["return_place"] and "반납" in r["speech"]
    r2 = ask(client, "물류 창고", pending=r["action"])
    assert r2["kind"] == "confirm" and r2["action"]["payload"]["return_place"] == "물류 창고"


def test_memo_request_defaults_private_and_needs_confirmation(client):
    r = ask(client, "다음부터 여기는 후문으로 들어와")
    assert r["kind"] == "confirm" and r["action"]["type"] == "add_memo"
    assert r["action"]["payload"]["visibility"] == "private"  # R12 개인 기본
    assert r["action"]["payload"]["scope_id"] == "place-pangyo"


def test_navigate_and_unknown(client):
    assert ask(client, "다음 장소 안내해줘")["action"]["type"] == "navigate_next"
    assert ask(client, "오늘 날씨 어때")["kind"] == "unknown"
