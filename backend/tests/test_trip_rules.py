from datetime import datetime, timedelta, timezone

from app.features.trip.rules import ObservationView, add_counts, compare, expected_remaining

NOW = datetime(2026, 11, 8, 6, 0, tzinfo=timezone.utc)
STALE = timedelta(seconds=300)


def obs(counts, status="ok", age=0):
    return ObservationView(1, "sample", status, counts, NOW - timedelta(seconds=age))


def test_expected_remaining_baseline_minus_deliveries():
    # R05: 확정 직전 잔량 − 이번 하차량. A 3개 하차 → 7
    assert expected_remaining([{"box": 10}, {"box": -3}]) == {"box": 7}
    assert expected_remaining([{"box": 10}, {"box": -3}, {"box": -3}, {"box": -4}]) == {"box": 0}


def test_expected_remaining_keeps_kinds_separate():
    assert expected_remaining([{"box": 6, "container": 2}, {"box": -3}]) == {"box": 3, "container": 2}


def test_add_counts_subtract():
    assert add_counts({"box": 10}, {"box": 3}, sign=-1) == {"box": 7}


def test_compare_match():
    assert compare({"box": 7}, obs({"box": 7}), NOW, STALE).state == "match"


def test_compare_mismatch_lists_difference():
    # 14장: 예상 7 / 관측 8
    c = compare({"box": 7}, obs({"box": 8}), NOW, STALE)
    assert c.state == "mismatch"
    assert [(d.kind, d.expected, d.observed) for d in c.differences] == [("box", 7, 8)]


def test_compare_does_not_sum_different_kinds():
    # 16장: 박스 1 + 용기 1 = 2 로 합산해 일치 처리하지 않는다
    c = compare({"box": 2}, obs({"box": 1, "container": 1}), NOW, STALE)
    assert c.state == "mismatch"
    assert {d.kind for d in c.differences} == {"box", "container"}


def test_compare_no_observation_is_not_match():
    assert compare({"box": 7}, None, NOW, STALE).state == "no_observation"


def test_compare_unavailable_is_not_match():
    # R10: 카메라 가림 → 확인 불가, 정상 판정 안 함
    assert compare({"box": 7}, obs(None, status="unavailable"), NOW, STALE).state == "unavailable"


def test_compare_stale_even_if_counts_equal():
    # R10: 오래된 관측은 값이 같아도 정상으로 판정하지 않는다
    assert compare({"box": 7}, obs({"box": 7}, age=301), NOW, STALE).state == "stale"
    assert compare({"box": 7}, obs({"box": 7}, age=300), NOW, STALE).state == "match"
