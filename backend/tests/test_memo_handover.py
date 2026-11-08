"""메모(F49~F55, R12)·장비(F57·F58)·인계(F59~F62, R13)."""

import uuid

T = "/trips/sample-trip-1"
D1 = {"X-Driver-Id": "driver-1"}
D2 = {"X-Driver-Id": "driver-2"}


def key():
    return str(uuid.uuid4())


def memo(client, headers, **kw):
    body = {"idempotency_key": key(), "scope_type": "place", "scope_id": "place-pangyo", "body": "후문으로 진입", **kw}
    return client.post("/memos", json=body, headers=headers)


def test_drivers_and_equipment(client):
    assert [d["id"] for d in client.get("/drivers").json()["drivers"]] == ["driver-1", "driver-2"]
    eq = client.get("/vehicles/vehicle-1/equipment").json()["equipment"]
    assert {e["id"] for e in eq} == {"equip-cart", "equip-strap", "equip-toolbox"}
    e = client.post("/equipment/equip-cart/status", json={"status": "broken", "note": "바퀴 파손"}, headers=D1).json()
    assert e["status"] == "broken" and e["updated_by"] == "driver-1"


def test_private_memo_visible_only_to_author(client):
    assert memo(client, D1, visibility="private").status_code == 201
    memo(client, D1, visibility="shared", body="정문 혼잡")
    mine = client.get("/memos", params={"scope_type": "place", "scope_id": "place-pangyo"}, headers=D1).json()["memos"]
    other = client.get("/memos", params={"scope_type": "place", "scope_id": "place-pangyo"}, headers=D2).json()["memos"]
    assert len(mine) == 2 and [m["body"] for m in other] == ["정문 혼잡"]


def test_memo_requires_driver_and_is_idempotent(client):
    assert memo(client, {}).status_code == 422
    body = {"idempotency_key": key(), "scope_type": "trip", "scope_id": "sample-trip-1", "body": "메모"}
    a = client.post("/memos", json=body, headers=D1).json()
    b = client.post("/memos", json=body, headers=D1).json()
    assert b["replayed"] is True and a["memo"]["id"] == b["memo"]["id"]


def test_stop_memos_relevant_place_and_this_trip(client):
    memo(client, D1, visibility="shared", body="상시 메모")
    memo(client, D1, visibility="shared", body="이번 운행만", persistence="this_trip", trip_id="sample-trip-1")
    memo(client, D1, visibility="shared", body="다른 운행", persistence="this_trip", trip_id="other-trip")
    bodies = [m["body"] for m in client.get(f"{T}/stops/stop-a/memos", headers=D2).json()["memos"]]
    assert set(bodies) == {"상시 메모", "이번 운행만"}
    # 시드: 정자역(B) 공유 메모
    assert "(샘플)" in client.get(f"{T}/stops/stop-b/memos").json()["memos"][0]["body"]


def test_shared_memo_update_tracks_editor_private_hidden(client):
    m = memo(client, D1, visibility="shared").json()["memo"]
    u = client.put(f"/memos/{m['id']}", json={"body": "후문 공사 중"}, headers=D2).json()
    assert u["body"] == "후문 공사 중" and u["updated_by"] == "driver-2" and u["author_id"] == "driver-1"
    assert client.put(f"/memos/{m['id']}", json={"visibility": "private"}, headers=D2).status_code == 403
    p = memo(client, D1, visibility="private").json()["memo"]
    assert client.put(f"/memos/{p['id']}", json={"body": "x"}, headers=D2).status_code == 404


def test_handover_summary_excludes_private_and_accept_flow(client):
    client.post(f"{T}/baseline", json={"idempotency_key": key(), "counts": {"box": 10}}, headers=D1)
    client.post(f"{T}/pickups", json={"idempotency_key": key(), "stop_id": "stop-a", "item_name": "빈 용기", "kind": "container", "qty": 2, "return_place": "창고"}, headers=D1)
    client.post("/equipment/equip-strap/status", json={"status": "missing"}, headers=D1)
    memo(client, D1, visibility="private", body="개인 비밀 메모", scope_id="place-seohyeon")
    memo(client, D1, visibility="shared", body="C 주차 주의", scope_id="place-seohyeon")

    p = client.get(f"{T}/handover/preview").json()
    assert [r["label"] for r in p["remaining_stops"]] == ["A", "B", "C"]
    assert p["pickups_loaded"][0]["qty"] == 2 and p["equipment_alerts"][0]["status"] == "missing"
    bodies = [m["body"] for m in p["shared_memos"]]
    assert "C 주차 주의" in bodies and "개인 비밀 메모" not in bodies
    assert p["summary_method"] == "rule_based" and p["summary_lines"][-1] == "개인 기록은 포함하지 않았습니다."

    assert client.post(f"{T}/handover", json={"idempotency_key": key(), "to_driver_id": "driver-2"}, headers=D2).status_code == 403
    k = key()
    h = client.post(f"{T}/handover", json={"idempotency_key": k, "to_driver_id": "driver-2", "note": "C 남음"}, headers=D1).json()
    assert h["handover"]["status"] == "pending"
    assert client.post(f"{T}/handover", json={"idempotency_key": k, "to_driver_id": "driver-2", "note": "C 남음"}, headers=D1).json()["replayed"] is True
    hid = h["handover"]["id"]
    assert client.post(f"/handovers/{hid}/accept", headers=D1).status_code == 403  # 인계자 ≠ 인수자
    a = client.post(f"/handovers/{hid}/accept", headers=D2).json()
    assert a["handover"]["status"] == "accepted"
    assert client.get(T).json()["driver_id"] == "driver-2"
    assert client.get(f"{T}/history").json()["items"][-1]["type"] == "handover_accepted"
    assert client.post(f"/handovers/{hid}/accept", headers=D2).json()["replayed"] is True
