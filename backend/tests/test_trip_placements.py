import uuid

from app.features.trip import rules

T = "/trips/sample-trip-1"
NOW = "2026-11-09T05:00:00Z"


def key() -> str:
    return uuid.uuid4().hex


def units(client) -> list[dict]:
    return client.get(T).json()["units"]


def send(client, events, source="sample", idem=None):
    return client.post(f"{T}/placements", json={"idempotency_key": idem or key(), "source": source, "observed_at": NOW, "events": events})


def place_all(client):
    r = send(client, [{"unit_id": u["id"], "type": "placed"} for u in units(client)])
    assert r.status_code == 200
    return r.json()["trip"]["units"]


def confirm(client):
    assert client.post(f"{T}/baseline", json={"idempotency_key": key(), "counts": {"box": 10}}).status_code == 200


def observe(client, n):
    assert client.post(f"{T}/observations", json={"idempotency_key": key(), "source": "sample", "status": "ok", "counts": {"box": n}, "observed_at": "2099-01-01T00:00:00Z"}).status_code == 201


def of(us, stop):
    return [u for u in us if u["stop_id"] == stop]


def test_seed_has_a_planned_slot_for_every_box(client):
    us = units(client)
    assert len(us) == 10
    assert all(u["state"] == "planned" and u["zone"] is None for u in us)
    assert {u["planned_zone"] for u in of(us, "stop-a")} == {"rear"}
    assert len({(u["planned_zone"], u["planned_lane"], u["planned_layer"]) for u in us}) == 10
    # 나중에 내릴 C 를 먼저 싣는다
    assert us[0]["stop_id"] == "stop-c" and us[-1]["stop_id"] == "stop-a"


def test_placed_records_slot_and_source_without_touching_remaining(client):
    u = units(client)[0]
    trip = send(client, [{"unit_id": u["id"], "type": "placed"}]).json()["trip"]
    got = next(x for x in trip["units"] if x["id"] == u["id"])
    assert got["state"] == "loaded" and got["zone"] == u["planned_zone"] and got["position_source"] == "sample"
    assert trip["baseline"] is None and trip["expected_remaining"] is None


def test_placements_are_idempotent(client):
    u = units(client)[0]
    k = key()
    ev = [{"unit_id": u["id"], "type": "placed"}]
    assert send(client, ev, idem=k).json()["replayed"] is False
    assert send(client, ev, idem=k).json()["replayed"] is True
    other = [{"unit_id": units(client)[1]["id"], "type": "placed"}]
    r = send(client, other, idem=k)
    assert r.status_code == 409 and r.json()["detail"]["code"] == "idempotency_key_conflict"


def test_occupied_slot_rejects_whole_batch(client):
    a, b = units(client)[:2]
    slot = {"zone": "rear", "lane": 2, "layer": 3}
    r = send(client, [{"unit_id": a["id"], "type": "placed", "slot": slot}, {"unit_id": b["id"], "type": "placed", "slot": slot}])
    assert r.status_code == 409 and r.json()["detail"]["code"] == "slot_occupied"
    assert all(u["state"] == "planned" for u in units(client))


def test_misplaced_then_accepted(client):
    u = units(client)[0]
    slot = {"zone": "rear", "lane": 2, "layer": 1}
    got = next(x for x in send(client, [{"unit_id": u["id"], "type": "placed", "slot": slot}]).json()["trip"]["units"] if x["id"] == u["id"])
    assert (got["zone"], got["planned_zone"]) == ("rear", "front")
    got = next(x for x in send(client, [{"unit_id": u["id"], "type": "accepted"}]).json()["trip"]["units"] if x["id"] == u["id"])
    assert got["planned_zone"] == "rear" and got["planned_lane"] == 2


def test_removed_only_before_baseline(client):
    us = place_all(client)
    r = send(client, [{"unit_id": us[0]["id"], "type": "removed"}])
    assert r.status_code == 200
    assert next(x for x in r.json()["trip"]["units"] if x["id"] == us[0]["id"])["state"] == "planned"
    send(client, [{"unit_id": us[0]["id"], "type": "placed"}])
    confirm(client)
    r = send(client, [{"unit_id": us[0]["id"], "type": "removed"}])
    assert r.status_code == 409 and r.json()["detail"]["code"] == "placement_locked"


def test_baseline_marks_unreported_units_as_loaded_by_driver(client):
    confirm(client)
    us = units(client)
    assert all(u["state"] == "loaded" and u["zone"] == u["planned_zone"] and u["position_source"] == "manual" for u in us)


def test_partial_delivery_unloads_top_first_and_settles(client):
    place_all(client)
    confirm(client)
    observe(client, 8)
    r = client.post(f"{T}/stops/stop-a/complete", json={"idempotency_key": key(), "delivered": {"box": 2}})
    assert r.json()["result"] == "completed"
    us = of(units(client), "stop-a")
    assert sum(u["state"] == "unloaded" for u in us) == 2
    left = [u for u in us if u["state"] == "loaded"]
    assert len(left) == 1 and left[0]["layer"] == 1


def test_needs_confirmation_keeps_units(client):
    place_all(client)
    confirm(client)
    observe(client, 10)
    r = client.post(f"{T}/stops/stop-a/complete", json={"idempotency_key": key(), "delivered": {"box": 3}})
    assert r.json()["result"] == "needs_confirmation"
    assert all(u["state"] == "loaded" for u in of(units(client), "stop-a"))


def test_pickup_adds_rear_units_and_return_removes(client):
    place_all(client)
    confirm(client)
    t = client.post(f"{T}/pickups", json={"idempotency_key": key(), "stop_id": "stop-a", "item_name": "빈 용기", "kind": "container", "qty": 2, "return_place": "창고"}).json()["trip"]
    mine = [u for u in t["units"] if u["pickup_id"] is not None]
    assert len(mine) == 2 and all(u["state"] == "loaded" and u["zone"] == "rear" for u in mine)
    assert len({(u["zone"], u["lane"], u["layer"]) for u in t["units"] if u["state"] == "loaded"}) == 12
    t = client.post(f"{T}/pickups/{mine[0]['pickup_id']}/return", json={"idempotency_key": key()}).json()["trip"]
    assert all(u["state"] == "unloaded" for u in t["units"] if u["pickup_id"] is not None)


def test_add_stop_and_qty_change_do_not_create_boxes(client):
    t = client.post(f"{T}/stops", json={"idempotency_key": key(), "name": "샘플", "latitude": 1.0, "longitude": 2.0, "cargo": [{"item_name": "박스", "kind": "box", "qty": 2}]}).json()["trip"]
    assert len(t["units"]) == 10 and not [u for u in t["units"] if u["stop_id"] not in ("stop-a", "stop-b", "stop-c")]
    line = next(st for st in t["stops"] if st["id"] == "stop-a")["cargo"][0]["id"]
    before = len(of(t["units"], "stop-a"))
    for qty in (1, 5):
        t = client.post(f"{T}/stops/stop-a/cargo", json={"idempotency_key": key(), "cargo_line_id": line, "planned_qty": qty}).json()["trip"]
        assert len(of(t["units"], "stop-a")) == before


def test_free_slots_prefers_zone_and_reports_overflow():
    assert rules.free_slots(set(), 2, "front") == [("front", 0, 1), ("front", 1, 1)]
    full = {(z, lane, layer) for z in rules.ZONES for lane in range(3) for layer in range(1, 4)}
    assert rules.free_slots(full, 1) == [None]


def test_settle_layers_drops_units_above_a_gap():
    assert rules.settle_layers([(1, "rear", 0, 2), (2, "rear", 0, 3), (3, "rear", 1, 1)]) == {1: 1, 2: 2}
