"""운행 계산 규칙 (기획서 13장 R05·R06·R10). DB·HTTP 에 의존하지 않는 순수 함수."""

from __future__ import annotations

from dataclasses import dataclass, field
from datetime import datetime, timedelta
from typing import Literal, Mapping

Counts = dict[str, int]
ComparisonState = Literal["match", "mismatch", "no_observation", "unavailable", "stale"]


def add_counts(a: Mapping[str, int], b: Mapping[str, int], sign: int = 1) -> Counts:
    """종류별로 더한다(sign=-1 이면 뺀다). 0 이 된 종류도 남겨 비교에서 빠지지 않게 한다."""
    out: Counts = dict(a)
    for kind, n in b.items():
        out[kind] = out.get(kind, 0) + sign * n
    return out


def expected_remaining(deltas: list[Mapping[str, int]]) -> Counts:
    """R05: 최초 기준(+) − 확정된 하차(−) (+ 회수·추가 상차). 이벤트 순서대로 합산."""
    total: Counts = {}
    for d in deltas:
        total = {k: max(v, 0) for k, v in add_counts(total, d).items()}
    return total


@dataclass(frozen=True)
class ObservationView:
    id: int
    source: str
    status: Literal["ok", "unavailable"]
    counts: Counts | None
    observed_at: datetime


@dataclass(frozen=True)
class Difference:
    kind: str
    expected: int
    observed: int


@dataclass(frozen=True)
class Comparison:
    state: ComparisonState
    expected: Counts | None
    observed: Counts | None
    observation: ObservationView | None
    differences: list[Difference] = field(default_factory=list)


def compare(
    expected: Counts | None,
    latest: ObservationView | None,
    now: datetime,
    stale_after: timedelta,
) -> Comparison:
    """R06·R10: 일치할 때만 match. 관측 없음·확인 불가·오래됨은 정상으로 판정하지 않는다.
    서로 다른 종류는 합산하지 않고 종류별로 비교한다 (16장)."""
    if latest is None:
        return Comparison("no_observation", expected, None, None)
    if latest.status == "unavailable" or latest.counts is None:
        return Comparison("unavailable", expected, None, latest)
    if now - latest.observed_at > stale_after:
        return Comparison("stale", expected, latest.counts, latest)
    exp = expected or {}
    kinds = sorted(set(exp) | set(latest.counts))
    diffs = [
        Difference(k, exp.get(k, 0), latest.counts.get(k, 0))
        for k in kinds
        if exp.get(k, 0) != latest.counts.get(k, 0)
    ]
    return Comparison("mismatch" if diffs else "match", expected, latest.counts, latest, diffs)


# ---------- 적재 자리 (F12·F13)

ZONES = ("front", "middle", "rear")
LANES = 3
LAYERS = 3
Slot = tuple[str, int, int]  # (zone, lane, layer)


def free_slots(occupied: set[Slot], n: int, preferred: str | None = None) -> list[Slot | None]:
    """빈 칸 n 개를 고른다. 선호 구역부터, 아래층부터 채운다. 칸이 모자라면 나머지는 None (자리 미정)."""
    rest = [z for z in ("rear", "middle", "front") if z != preferred]
    order = ([preferred] if preferred in ZONES else []) + rest
    empty = [(z, lane, layer) for z in order for layer in range(1, LAYERS + 1) for lane in range(LANES) if (z, lane, layer) not in occupied]
    return [empty[i] if i < len(empty) else None for i in range(n)]


def settle_layers(units: list[tuple[int, str, int, int]]) -> dict[int, int]:
    """(id, zone, lane, layer) 목록에서 같은 줄의 아래 칸이 비면 내려앉힌 층을 돌려준다. 바뀐 단위만 담는다."""
    columns: dict[tuple[str, int], list[tuple[int, int]]] = {}
    for uid, zone, lane, layer in units:
        columns.setdefault((zone, lane), []).append((layer, uid))
    changed: dict[int, int] = {}
    for items in columns.values():
        for new_layer, (layer, uid) in enumerate(sorted(items), start=1):
            if layer != new_layer:
                changed[uid] = new_layer
    return changed
