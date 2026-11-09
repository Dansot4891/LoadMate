"""대표 시나리오(기획서 14장)와 예외(16장)를 API 로 재현한다."""

import uuid
from datetime import datetime, timedelta, timezone

T = "/trips/sample-trip-1"


def key():
    return str(uuid.uuid4())


def now_iso(offset_s: int = 0):
    return (datetime.now(timezone.utc) + timedelta(seconds=offset_s)).strftime("%Y-%m-%dT%H:%M:%SZ")


def observe(client, counts, status="ok", source="sample", observed_at=None):
    r = client.post(f"{T}/observations", json={
        "idempotency_key": key(), "source": source, "status": status, "counts": counts,
        "observed_at": observed_at or now_iso(),
    })
    assert r.status_code == 201, r.text
    return r.json()


def confirm_baseline(client, counts=None):
    r = client.post(f"{T}/baseline", json={"idempotency_key": key(), "counts": counts or {"box": 10}})
    assert r.status_code == 200, r.text
    return r.json()


def complete(client, stop, delivered, k=None, resolution=None):
    return client.post(f"{T}/stops/{stop}/complete", json={
        "idempotency_key": k or key(), "delivered": delivered, "resolution": resolution,
    })


def test_list_and_get_sample_trip(client):
    trips = client.get("/trips").json()["trips"]
    assert trips == [{"id": "sample-trip-1", "name": "분당 납품 운행 · A·B·C", "status": "planned", "data_source": "sample", "stop_count": 3}]
    t = client.get(T).json()
    assert [s["label"] for s in t["stops"]] == ["A", "B", "C"]
    assert [s["cargo"][0]["planned_qty"] for s in t["stops"]] == [3, 3, 4]
    assert t["baseline"] is None and t["expected_remaining"] is None
    assert t["comparison"]["count_only"] is True


def test_before_baseline_compares_with_plan(client):
    # R02·S05: 기준 확정 전에는 검수 목록(계획 10개)과 관측을 비교
    assert observe(client, {"box": 10})["comparison"]["state"] == "match"
    c = observe(client, {"box": 9})["comparison"]
    assert c["state"] == "mismatch" and c["expected"] == {"box": 10}
    assert client.get(T).json()["expected_remaining"] is None


def test_unknown_trip_404(client):
    r = client.get("/trips/nope")
    assert r.status_code == 404 and r.json()["detail"]["code"] == "trip_not_found"


def test_representative_scenario_a_missed_one(client):
    # 1·2 적재 확인: 관측 10 → 기사 확정
    o = observe(client, {"box": 10})
    t = client.post(f"{T}/baseline", json={"idempotency_key": key(), "counts": {"box": 10}, "observation_id": o["observation"]["id"]}).json()["trip"]
    assert t["status"] == "in_progress" and t["expected_remaining"] == {"box": 10}
    assert t["comparison"]["state"] == "match"

    # 5 A 도착, 실제 2개만 하차 → 관측 8
    observe(client, {"box": 8})
    k = key()
    r = complete(client, "stop-a", {"box": 3}, k=k).json()
    # 6 예상 7 / 관측 8 → 확인 필요, 저장 안 함
    assert r["result"] == "needs_confirmation"
    assert r["comparison"]["state"] == "mismatch"
    assert r["comparison"]["differences"] == [{"kind": "box", "expected": 7, "observed": 8}]
    assert r["expected_remaining"] == {"box": 10}
    assert client.get(T).json()["stops"][0]["status"] == "pending"

    # 7 남은 1개를 찾아 하차 → 관측 7 → 같은 키로 재요청 (needs_confirmation 은 키를 소비하지 않음)
    observe(client, {"box": 7})
    r = complete(client, "stop-a", {"box": 3}, k=k).json()
    assert r["result"] == "completed" and r["replayed"] is False
    assert r["stop"]["status"] == "completed" and r["stop"]["resolution"] == "matched"
    assert r["expected_remaining"] == {"box": 7}

    # 8 완료 중복 반영 없음: 같은 키 재전송 → replayed, 잔량 그대로
    r2 = complete(client, "stop-a", {"box": 3}, k=k).json()
    assert r2["replayed"] is True and r2["expected_remaining"] == {"box": 7}
    # 다른 키로 다시 완료 요청 → 이미 완료
    r3 = complete(client, "stop-a", {"box": 3})
    assert r3.status_code == 409 and r3.json()["detail"]["code"] == "stop_already_completed"
    # 재조회(앱 재실행 복원)도 같은 상태
    t = client.get(T).json()
    assert t["expected_remaining"] == {"box": 7} and t["stops"][0]["cargo"][0]["delivered_qty"] == 3


def test_driver_confirms_despite_mismatch_keeps_evidence(client):
    confirm_baseline(client)
    observe(client, {"box": 8})
    r = complete(client, "stop-a", {"box": 3}, resolution="driver_confirmed").json()
    assert r["result"] == "completed"
    assert r["stop"]["resolution"] == "driver_confirmed"
    # 관측 결과를 보존한 채 진행 (14장 분기): 비교 근거가 응답에 남는다
    assert r["comparison"]["state"] == "mismatch" and r["comparison"]["observed"] == {"box": 8}
    assert r["expected_remaining"] == {"box": 7}


def test_unavailable_observation_needs_confirmation(client):
    # 16장 카메라 가림: 확인 불가 → 정상 판정하지 않고 확인 요청
    confirm_baseline(client)
    observe(client, None, status="unavailable")
    r = complete(client, "stop-a", {"box": 3}).json()
    assert r["result"] == "needs_confirmation" and r["comparison"]["state"] == "unavailable"


def test_latest_observation_is_by_observed_at(client):
    # 늦게 도착한 과거 관측이 최신 관측을 덮지 않는다
    confirm_baseline(client)
    observe(client, None, status="unavailable")
    observe(client, {"box": 10}, observed_at=now_iso(-60))
    assert client.get(T).json()["comparison"]["state"] == "unavailable"


def test_stale_observation(client):
    confirm_baseline(client)
    observe(client, {"box": 7}, observed_at=now_iso(-600))
    r = complete(client, "stop-a", {"box": 3}).json()
    assert r["result"] == "needs_confirmation" and r["comparison"]["state"] == "stale"


def test_no_observation_then_manual_check(client):
    confirm_baseline(client)
    r = complete(client, "stop-a", {"box": 3}).json()
    assert r["result"] == "needs_confirmation" and r["comparison"]["state"] == "no_observation"
    r = complete(client, "stop-a", {"box": 3}, resolution="driver_confirmed").json()
    assert r["result"] == "completed" and r["stop"]["resolution"] == "driver_confirmed"


def test_partial_delivery_keeps_remaining(client):
    # 16장 부분 납품: A 3개 중 2개만 납품 → 남은 1개는 잔량에 남음
    confirm_baseline(client)
    observe(client, {"box": 8})
    r = complete(client, "stop-a", {"box": 2}).json()
    assert r["result"] == "completed" and r["stop"]["status"] == "partial"
    assert r["expected_remaining"] == {"box": 8}


def test_complete_requires_baseline(client):
    r = complete(client, "stop-a", {"box": 3})
    assert r.status_code == 409 and r.json()["detail"]["code"] == "baseline_not_confirmed"


def test_baseline_once_and_idempotent(client):
    k = key()
    body = {"idempotency_key": k, "counts": {"box": 10}}
    assert client.post(f"{T}/baseline", json=body).json()["replayed"] is False
    assert client.post(f"{T}/baseline", json=body).json()["replayed"] is True
    r = client.post(f"{T}/baseline", json={**body, "counts": {"box": 9}})
    assert r.status_code == 409 and r.json()["detail"]["code"] == "idempotency_key_conflict"
    r = client.post(f"{T}/baseline", json={"idempotency_key": key(), "counts": {"box": 10}})
    assert r.status_code == 409 and r.json()["detail"]["code"] == "baseline_already_confirmed"


def test_baseline_driver_correction_differs_from_observation(client):
    # R02: 관측 9 를 기사가 10 으로 정정해 확정
    o = observe(client, {"box": 9})
    t = client.post(f"{T}/baseline", json={"idempotency_key": key(), "counts": {"box": 10}, "observation_id": o["observation"]["id"]}).json()["trip"]
    assert t["baseline"]["counts"] == {"box": 10} and t["baseline"]["observation_id"] == o["observation"]["id"]
    assert t["comparison"]["state"] == "mismatch"


def test_validation_errors(client):
    confirm_baseline(client)
    r = complete(client, "stop-a", {"box": 4})
    assert r.status_code == 422 and r.json()["detail"]["code"] == "delivered_exceeds_planned"
    r = complete(client, "stop-a", {"container": 1})
    assert r.status_code == 422 and r.json()["detail"]["code"] == "unknown_kind"
    r = complete(client, "stop-x", {"box": 1})
    assert r.status_code == 404 and r.json()["detail"]["code"] == "stop_not_found"
    r = client.post(f"{T}/observations", json={"idempotency_key": key(), "source": "sample", "status": "ok", "counts": None, "observed_at": now_iso()})
    assert r.status_code == 422


def test_observation_idempotent(client):
    body = {"idempotency_key": key(), "source": "manual", "status": "ok", "counts": {"box": 10}, "observed_at": now_iso()}
    a = client.post(f"{T}/observations", json=body).json()
    b = client.post(f"{T}/observations", json=body).json()
    assert b["replayed"] is True and a["observation"]["id"] == b["observation"]["id"]


def test_full_trip_completes(client):
    confirm_baseline(client)
    for stop, n, left in [("stop-a", 3, 7), ("stop-b", 3, 4), ("stop-c", 4, 0)]:
        observe(client, {"box": left})
        assert complete(client, stop, {"box": n}).json()["result"] == "completed"
    assert client.get(T).json()["status"] == "completed"
