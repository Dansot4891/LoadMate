"""단계 A(운행 관리)·B(회수)·F(이상) + 수정 이력 (F04·F07·F18·F29·F44~F47·F30·F34·F64)."""

import uuid
from datetime import datetime, timezone

T = "/trips/sample-trip-1"
D1 = {"X-Driver-Id": "driver-1"}


def key():
    return str(uuid.uuid4())


def now_iso():
    return datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")


def post(client, path, body, headers=D1):
    return client.post(f"{T}{path}", json={"idempotency_key": key(), **body}, headers=headers)


def baseline(client):
    assert client.post(f"{T}/baseline", json={"idempotency_key": key(), "counts": {"box": 10}}, headers=D1).status_code == 200


def observe(client, counts):
    client.post(f"{T}/observations", json={"idempotency_key": key(), "source": "sample", "status": "ok", "counts": counts, "observed_at": now_iso()})


def test_baseline_sets_driver_and_sample_has_vehicle_zones(client):
    baseline(client)
    t = client.get(T).json()
    assert t["driver_id"] == "driver-1" and t["vehicle_id"] == "vehicle-1"
    assert [s["cargo"][0]["zone"] for s in t["stops"]] == ["rear", "middle", "front"]
    assert [s["place_key"] for s in t["stops"]] == ["place-pangyo", "place-jeongja", "place-seohyeon"]


def test_add_stop_after_baseline_changes_route_only(client):
    r = post(client, "/stops", {"name": "D 납품처", "latitude": 37.4, "longitude": 127.1, "cargo": [{"item_name": "박스", "kind": "box", "qty": 2}]})
    t = r.json()["trip"]
    assert [s["label"] for s in t["stops"]] == ["A", "B", "C", "D"] and t["expected_remaining"] is None
    baseline(client)  # 이제 계획 12 중 10 만 확정했다고 가정
    t = post(client, "/stops", {"name": "E", "latitude": 37.4, "longitude": 127.1, "cargo": [{"item_name": "용기", "kind": "container", "qty": 1}]}).json()["trip"]
    assert t["expected_remaining"] == {"box": 10}  # 경로만 늘고 실린 짐은 그대로


def test_reorder_only_pending_and_complete_list(client):
    baseline(client)
    observe(client, {"box": 7})
    client.post(f"{T}/stops/stop-a/complete", json={"idempotency_key": key(), "delivered": {"box": 3}})
    r = post(client, "/stops/reorder", {"stop_ids": ["stop-c"]})
    assert r.status_code == 422 and r.json()["detail"]["code"] == "reorder_must_list_pending_stops"
    t = post(client, "/stops/reorder", {"stop_ids": ["stop-c", "stop-b"]}).json()["trip"]
    assert [s["label"] for s in t["stops"]] == ["A", "C", "B"]


def test_cancel_stop_keeps_cargo_on_board_and_finishes_trip(client):
    baseline(client)
    for stop, left in [("stop-a", 7), ("stop-b", 4)]:
        observe(client, {"box": left})
        client.post(f"{T}/stops/{stop}/complete", json={"idempotency_key": key(), "delivered": {"box": 3}})
    t = post(client, "/stops/stop-c/cancel", {"reason": "수령인 부재"}).json()["trip"]
    assert t["stops"][2]["status"] == "cancelled" and t["stops"][2]["cancel_reason"] == "수령인 부재"
    assert t["expected_remaining"] == {"box": 4}  # 취소 물품은 차에 남음
    assert t["status"] == "completed"


def test_stop_added_after_baseline_can_be_completed_without_boxes(client):
    baseline(client)
    sid = post(client, "/stops", {"name": "D", "latitude": 37.4, "longitude": 127.1, "cargo": [{"item_name": "박스", "kind": "box", "qty": 1}]}).json()["trip"]["stops"][-1]["id"]
    r = post(client, f"/stops/{sid}/complete", {"delivered": {"box": 1}, "resolution": "driver_confirmed"}).json()
    assert r["result"] == "completed" and r["expected_remaining"] == {"box": 9}


def test_cargo_qty_change_is_plan_change(client):
    baseline(client)
    t = client.get(T).json()
    line = t["stops"][0]["cargo"][0]["id"]
    t = post(client, "/stops/stop-a/cargo", {"cargo_line_id": line, "planned_qty": 2}).json()["trip"]
    assert t["stops"][0]["cargo"][0]["planned_qty"] == 2 and t["expected_remaining"] == {"box": 10}


def test_pickup_adds_to_expected_and_return_removes(client):
    # 15장: B 에서 빈 용기 2개 회수 → 배송품 4 + 회수품 2 분리
    baseline(client)
    for stop, left in [("stop-a", 7), ("stop-b", 4)]:
        observe(client, {"box": left})
        client.post(f"{T}/stops/{stop}/complete", json={"idempotency_key": key(), "delivered": {"box": 3}})
    t = post(client, "/pickups", {"stop_id": "stop-b", "item_name": "빈 용기", "kind": "container", "qty": 2, "return_place": "창고", "source": "voice"}).json()["trip"]
    assert t["expected_remaining"] == {"box": 4, "container": 2}
    assert t["pickups"][0]["status"] == "loaded"
    observe(client, {"box": 4, "container": 2})
    assert client.get(T).json()["comparison"]["state"] == "match"
    # C 완료해도 회수품이 남아 있으면 운행 완료 아님 (F48)
    observe(client, {"container": 2, "box": 0})
    client.post(f"{T}/stops/stop-c/complete", json={"idempotency_key": key(), "delivered": {"box": 4}})
    assert client.get(T).json()["status"] == "in_progress"
    pid = t["pickups"][0]["id"]
    t = post(client, f"/pickups/{pid}/return", {}).json()["trip"]
    assert t["expected_remaining"] == {"box": 0, "container": 0} and t["status"] == "completed"
    r = post(client, f"/pickups/{pid}/return", {})
    assert r.status_code == 409


def test_end_trip_requires_reason_when_unfinished(client):
    baseline(client)
    r = post(client, "/end", {})
    assert r.status_code == 422 and r.json()["detail"]["code"] == "end_reason_required"
    t = post(client, "/end", {"reason": "차량 교대로 인계"}).json()["trip"]
    assert t["status"] == "ended_incomplete" and t["end_reason"] == "차량 교대로 인계"
    r = post(client, "/end", {"reason": "x"})
    assert r.status_code == 409


def test_issue_record_and_resolve(client):
    baseline(client)
    t = post(client, "/issues", {"kind": "fall", "zone": "middle", "note": "박스 기울어짐", "observation_source": "sample", "observed_at": now_iso()}).json()["trip"]
    iid = t["issues"][0]["id"]
    assert t["issues"][0]["status"] == "open" and t["issues"][0]["source"] == "sample"
    t = post(client, f"/issues/{iid}/resolve", {"resolution": "rearranged", "note": "다시 고정"}).json()["trip"]
    assert t["issues"][0]["status"] == "resolved" and t["issues"][0]["resolved_by"] == "driver-1"
    summaries = [i["summary"] for i in client.get(f"{T}/history").json()["items"]]
    assert "넘어짐 의심" in summaries[-2] and "재배치함" in summaries[-1]


def test_history_has_source_actor_and_summary(client):
    baseline(client)
    post(client, "/pickups", {"stop_id": "stop-a", "item_name": "빈 용기", "kind": "container", "qty": 1, "return_place": "창고", "source": "voice"})
    items = client.get(f"{T}/history").json()["items"]
    assert [i["type"] for i in items] == ["baseline_confirmed", "pickup_added"]
    assert items[1]["source"] == "voice" and items[1]["actor_id"] == "driver-1"
    assert "회수" in items[1]["summary"] and "A" in items[1]["summary"]


def test_change_idempotent_replay(client):
    baseline(client)
    body = {"idempotency_key": key(), "stop_id": "stop-a", "item_name": "빈 용기", "kind": "container", "qty": 1, "return_place": "창고"}
    a = client.post(f"{T}/pickups", json=body, headers=D1).json()
    b = client.post(f"{T}/pickups", json=body, headers=D1).json()
    assert a["replayed"] is False and b["replayed"] is True and len(b["trip"]["pickups"]) == 1
    r = client.post(f"{T}/pickups", json={**body, "qty": 2}, headers=D1)
    assert r.status_code == 409
