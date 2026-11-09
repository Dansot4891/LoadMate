"""방문지 장소 검색 (VWorld 어댑터 + API). 실제 VWorld 는 호출하지 않는다."""

import uuid

import pytest

from app.core.errors import AppError
from app.features.trip.models import Stop
from app.integrations.places.vworld import Coordinates, VWorldPlaceSearch, get_place_search

T = "/trips/sample-trip-1"
ROAD = "경기도 성남시 분당구 판교역로 235"


def ok(*items):
    return {"response": {"status": "OK", "result": {"items": list(items)}}}


def address_item(road=ROAD, parcel="", x="127.1", y="37.4", bldnm=""):
    return {"point": {"x": x, "y": y}, "address": {"road": road, "parcel": parcel, "bldnm": bldnm}}


NOT_FOUND = {"response": {"status": "NOT_FOUND"}}


def adapter(by_type):
    calls = []

    def fetch(params):
        calls.append(params)
        return by_type((params["type"], params.get("category")))

    return VWorldPlaceSearch("key", fetch=fetch), calls


def test_missing_key_is_reported_and_nothing_is_called():
    calls = []
    search = VWorldPlaceSearch("", fetch=lambda p: calls.append(p) or NOT_FOUND)
    with pytest.raises(AppError) as e:
        search.locate(ROAD)
    assert (e.value.status_code, e.value.code) == (503, "place_search_not_configured")
    assert calls == []


def test_vworld_error_is_a_failure_not_an_empty_result():
    search, _ = adapter(lambda t: {"response": {"status": "ERROR", "error": {"code": "INCORRECT_KEY"}}})
    with pytest.raises(AppError) as e:
        search.locate(ROAD)
    assert (e.value.status_code, e.value.code) == (502, "place_search_failed")


def test_locate_reads_longitude_from_x_and_latitude_from_y():
    search, _ = adapter(lambda t: ok(address_item()))
    assert search.locate(ROAD) == Coordinates(37.4, 127.1)


def test_locate_prefers_the_exact_address_ignoring_spaces():
    other = address_item(road="경기도 성남시 분당구 판교역로 2355", x="127.9", y="37.9")
    search, _ = adapter(lambda t: ok(other, address_item()))
    assert search.locate("경기도 성남시 분당구  판교역로 235") == Coordinates(37.4, 127.1)


def test_locate_does_not_guess_between_several_other_addresses():
    a, b = address_item(road="경기도 A로 1"), address_item(road="경기도 B로 1")
    search, _ = adapter(lambda t: ok(a, b))
    assert search.locate(ROAD) is None


def test_locate_falls_back_to_parcel():
    parcel = "경기도 성남시 분당구 삼평동 681"
    search, _ = adapter(lambda t: NOT_FOUND if t[1] == "ROAD" else ok(address_item(road="", parcel=parcel)))
    assert search.locate(parcel) == Coordinates(37.4, 127.1)


def test_locate_ignores_the_dong_in_parentheses():
    item = address_item(road=f"{ROAD} (삼평동)")
    search, calls = adapter(lambda t: ok(item))
    assert search.locate(ROAD) == Coordinates(37.4, 127.1)
    assert search.locate(f"{ROAD} (삼평동)") == Coordinates(37.4, 127.1)
    assert all("(" not in c["query"] for c in calls)


def test_locate_matches_the_short_parcel_vworld_returns():
    short = address_item(road="", parcel="오류동 1556")
    search, _ = adapter(lambda t: NOT_FOUND if t[1] == "ROAD" else ok(short))
    assert search.locate("인천광역시 서구 오류동 1556") == Coordinates(37.4, 127.1)


def test_locate_drops_the_land_type_letter():
    short = address_item(road="", parcel="삼평동 681")
    search, calls = adapter(lambda t: NOT_FOUND if t[1] == "ROAD" else ok(short))
    assert search.locate("경기도 성남시 분당구 삼평동 681대") == Coordinates(37.4, 127.1)
    assert calls[-1]["query"] == "경기도 성남시 분당구 삼평동 681"


def test_locate_does_not_match_a_longer_parcel_number():
    other = address_item(road="", parcel="삼평동 1")
    search, _ = adapter(lambda t: NOT_FOUND if t[1] == "ROAD" else ok(other))
    assert search.locate("경기도 성남시 분당구 삼평동 11") is None


def test_locate_prefers_the_named_building_at_the_same_address():
    plain = address_item(x="127.9", y="37.9")
    named = address_item(bldnm="분당구청")
    search, _ = adapter(lambda t: ok(plain, named))
    assert search.locate(ROAD) == Coordinates(37.4, 127.1)


@pytest.mark.parametrize("delivery_address", [
    "경기 성남시 분당구 판교역로 235",
    "성남시 분당구 판교역로 235",
    "경기도 성남시 분당구 판교역로 235 에이치스퀘어 N동 3층",
    "경기도 성남시 분당구 판교역로 235, 301호",
])
def test_locate_accepts_delivery_address_styles(delivery_address):
    search, _ = adapter(lambda t: ok(address_item(road=f"{ROAD} (삼평동)")) if t[1] == "ROAD" else NOT_FOUND)
    assert search.locate(delivery_address) == Coordinates(37.4, 127.1)


def test_locate_handles_a_road_with_a_number_in_its_name():
    road = "서울특별시 서초구 바우뫼로12길 73"
    search, calls = adapter(lambda t: ok(address_item(road=road)) if t[1] == "ROAD" else NOT_FOUND)
    assert search.locate("서울 서초구 바우뫼로12길 73 2층") == Coordinates(37.4, 127.1)
    assert calls[0]["query"] == "서울특별시 서초구 바우뫼로12길 73"


def test_locate_rejects_a_different_building_number_or_province():
    search, _ = adapter(lambda t: ok(address_item()) if t[1] == "ROAD" else NOT_FOUND)
    assert search.locate("경기도 성남시 분당구 판교역로 23") is None
    assert search.locate("서울 성남시 분당구 판교역로 235") is None


def test_locate_does_not_pick_between_provinces_when_none_is_given():
    seoul, daegu = address_item(road="서울특별시 중구 세종대로 110"), address_item(road="대구광역시 중구 세종대로 110")
    search, _ = adapter(lambda t: ok(seoul, daegu) if t[1] == "ROAD" else NOT_FOUND)
    assert search.locate("중구 세종대로 110") is None


def test_locate_needs_a_district_or_province():
    search, calls = adapter(lambda t: ok(address_item()))
    assert search.locate("판교역로 235") is None
    assert calls == []


def test_locate_does_not_use_a_single_different_address():
    search, _ = adapter(lambda t: ok(address_item(road="경기도 다른로 1")))
    assert search.locate(ROAD) is None


def test_locate_rejects_coordinates_outside_korea():
    search, _ = adapter(lambda t: ok(address_item(x="0", y="0")))
    assert search.locate(ROAD) is None


class FakePlaces:
    def __init__(self, located=Coordinates(37.5, 127.2)):
        self.located = located
        self.asked = []

    def locate(self, address):
        self.asked.append(address)
        return self.located


@pytest.fixture
def places(client):
    fake = FakePlaces()
    client.app.dependency_overrides[get_place_search] = lambda: fake
    return fake


def add_without_coordinates(client, address=ROAD):
    return client.post(f"{T}/stops", json={
        "idempotency_key": str(uuid.uuid4()), "name": "판교 납품처", "address": address,
        "cargo": [{"item_name": "박스", "kind": "box", "qty": 1}],
    })


def test_check_api_reports_whether_the_address_was_found_without_coordinates(client, places):
    r = client.get("/places/check", params={"address": ROAD})
    assert r.status_code == 200
    assert r.json() == {"found": True}
    places.located = None
    assert client.get("/places/check", params={"address": ROAD}).json() == {"found": False}


def test_check_api_needs_an_address(client, places):
    assert client.get("/places/check", params={"address": "판"}).status_code == 422


def test_check_api_without_key_says_so(client):
    client.app.dependency_overrides[get_place_search] = lambda: VWorldPlaceSearch("")
    r = client.get("/places/check", params={"address": ROAD})
    assert r.status_code == 503
    assert r.json()["detail"]["code"] == "place_search_not_configured"


def change_place(client, stop_id, name="새 납품처", address="서울특별시 서초구 헌릉로 12", key=None):
    return client.post(f"{T}/stops/{stop_id}/place", json={"idempotency_key": key or str(uuid.uuid4()), "name": name, "address": address})


def test_change_place_keeps_cargo_and_clears_coordinates(client, places):
    stop_id = client.post(f"{T}/stops", json={
        "idempotency_key": str(uuid.uuid4()), "name": "옛 곳", "address": ROAD, "latitude": 37.4, "longitude": 127.1,
        "cargo": [{"item_name": "박스", "kind": "box", "qty": 2}],
    }).json()["trip"]["stops"][-1]["id"]
    stop = change_place(client, stop_id).json()["trip"]["stops"][-1]
    assert (stop["name"], stop["address"]) == ("새 납품처", "서울특별시 서초구 헌릉로 12")
    assert (stop["latitude"], stop["longitude"]) == (None, None)
    assert stop["cargo"][0]["planned_qty"] == 2


def test_change_place_uses_a_new_place_key_so_old_notes_do_not_follow(client, places):
    before = client.get(T).json()["stops"][0]
    after = change_place(client, before["id"]).json()["trip"]["stops"][0]
    assert after["place_key"] != before["place_key"]


def test_change_place_is_idempotent(client, places):
    key = str(uuid.uuid4())
    assert change_place(client, "stop-a", key=key).json()["replayed"] is False
    assert change_place(client, "stop-a", key=key).json()["replayed"] is True
    assert change_place(client, "stop-a", name="다른 이름", key=key).status_code == 409


def test_change_place_needs_name_and_address(client, places):
    assert change_place(client, "stop-a", name="").status_code == 422
    assert change_place(client, "stop-a", address=" ").status_code == 422


def test_change_place_rejects_finished_stops(client, places):
    client.post(f"{T}/stops/stop-a/cancel", json={"idempotency_key": str(uuid.uuid4()), "reason": "고객 요청"})
    r = change_place(client, "stop-a")
    assert r.status_code == 409
    assert r.json()["detail"]["code"] == "stop_already_completed"


def test_stop_without_coordinates_is_saved_with_address_only(client, session_factory, places):
    r = add_without_coordinates(client)
    assert r.status_code == 200
    added = r.json()["trip"]["stops"][-1]
    assert (added["address"], added["latitude"], added["longitude"]) == (ROAD, None, None)


def test_stop_needs_an_address_when_coordinates_are_missing(client, places):
    assert add_without_coordinates(client, address=" ").status_code == 422


def test_stop_rejects_only_one_coordinate(client, places):
    r = client.post(f"{T}/stops", json={
        "idempotency_key": str(uuid.uuid4()), "name": "X", "address": ROAD, "latitude": 37.4,
        "cargo": [{"item_name": "박스", "kind": "box", "qty": 1}],
    })
    assert r.status_code == 422


def test_route_points_use_stored_coordinates_without_calling_the_provider(client, places):
    stop_id = client.post(f"{T}/stops", json={
        "idempotency_key": str(uuid.uuid4()), "name": "좌표 있는 곳", "address": ROAD, "latitude": 37.4, "longitude": 127.1,
        "cargo": [{"item_name": "박스", "kind": "box", "qty": 1}],
    }).json()["trip"]["stops"][-1]["id"]
    r = client.get(f"{T}/route-points", params={"stop_ids": stop_id})
    assert r.json() == {"points": [{"stop_id": stop_id, "latitude": 37.4, "longitude": 127.1}], "missing": []}
    assert places.asked == []


def test_sample_stops_have_a_real_address_and_no_stored_coordinates(client, places):
    r = client.get(T).json()
    assert all(s["latitude"] is None and s["longitude"] is None and "인근" not in s["address"] for s in r["stops"])
    points = client.get(f"{T}/route-points", params={"stop_ids": "stop-a,stop-b,stop-c"}).json()
    assert [p["stop_id"] for p in points["points"]] == ["stop-a", "stop-b", "stop-c"]
    assert len(places.asked) == 3


def test_route_points_look_up_the_address_and_do_not_store_it(client, session_factory, places):
    stop_id = add_without_coordinates(client).json()["trip"]["stops"][-1]["id"]
    r = client.get(f"{T}/route-points", params={"stop_ids": stop_id})
    assert r.json() == {"points": [{"stop_id": stop_id, "latitude": 37.5, "longitude": 127.2}], "missing": []}
    assert places.asked == [ROAD]
    with session_factory() as s:
        stop = s.get(Stop, stop_id)
        assert (stop.latitude, stop.longitude) == (None, None)


def test_route_points_report_addresses_that_cannot_be_found(client, places):
    places.located = None
    stop_id = add_without_coordinates(client).json()["trip"]["stops"][-1]["id"]
    assert client.get(f"{T}/route-points", params={"stop_ids": stop_id}).json() == {"points": [], "missing": [stop_id]}


def test_route_points_unknown_stop_is_404(client, places):
    r = client.get(f"{T}/route-points", params={"stop_ids": "nope"})
    assert r.status_code == 404
    assert r.json()["detail"]["code"] == "stop_not_found"
