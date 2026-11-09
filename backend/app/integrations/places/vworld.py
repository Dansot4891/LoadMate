"""주소로 위치를 찾는 어댑터 (VWorld 검색 API 2.0).

좌표는 저장하지 않고 내비로 보낼 때 주소로 다시 조회한다. VWorld 이용약관이 제공 데이터의 무단 저장을 금지하기 때문이다.
인증키가 로그에 남지 않도록 요청 주소는 기록하지 않는다.
"""

from __future__ import annotations

import json
import logging
import re
import urllib.error
import urllib.parse
import urllib.request
from dataclasses import dataclass
from typing import Any, Callable, Protocol

from app.core.config import get_settings
from app.core.errors import AppError

log = logging.getLogger(__name__)

_URL = "https://api.vworld.kr/req/search"
_PAGE_SIZE = "10"
# 내비로 보낼 수 있는 범위인지 확인한다 (대한민국 경위도)
_LAT_RANGE = (33.0, 39.0)
_LNG_RANGE = (124.0, 132.0)

Fetch = Callable[[dict[str, str]], dict[str, Any]]


@dataclass(frozen=True)
class Coordinates:
    latitude: float
    longitude: float


class PlaceSearch(Protocol):
    def locate(self, address: str) -> Coordinates | None: ...


def _failed() -> AppError:
    return AppError(502, "place_search_failed", "장소 검색에 실패했습니다. 잠시 후 다시 시도하세요")


def _compact(text: str) -> str:
    return "".join(text.split())


def _road_text(address: str) -> str:
    """VWorld 도로명 주소 끝의 법정동 괄호를 뗀다. 예) 판교역로 235 (삼평동)"""
    return re.sub(r"\s*\([^)]*\)\s*$", "", address)


def _parcel_text(address: str) -> str:
    """장소 검색의 지번 주소 끝에 붙는 지목 글자를 뗀다. 예) 삼평동 681대, 저산리 305-34 철"""
    return re.sub(r"(\d)\s*[가-힣]\s*$", r"\1", address)


# 택배사 주소는 시·도 표기가 제각각이다. 첫 칸의 값은 조회에 쓰는 정식 이름
_SIDO_NAMES = {
    "서울": ("서울특별시", "서울시"), "부산": ("부산광역시", "부산시"), "대구": ("대구광역시", "대구시"), "인천": ("인천광역시", "인천시"),
    "광주": ("광주광역시", "광주시"), "대전": ("대전광역시", "대전시"), "울산": ("울산광역시", "울산시"), "세종": ("세종특별자치시", "세종시"),
    "경기": ("경기도",), "강원": ("강원특별자치도", "강원도"), "충북": ("충청북도",), "충남": ("충청남도",),
    "전북": ("전북특별자치도", "전라북도"), "전남": ("전라남도",), "경북": ("경상북도",), "경남": ("경상남도",),
    "제주": ("제주특별자치도", "제주도"),
}
_SIDO_KEY = {name: short for short, names in _SIDO_NAMES.items() for name in (short, *names)}
# 도로명 + 건물번호까지. "바우뫼로12길 73", "판교로 235번길 10" 처럼 뒤에 길이 이어지면 그 길까지 간다. 뒤의 동·층·호는 버린다
_ROAD_CORE = re.compile(r"^.*?(?:로|길)\s*(?:지하\s*)?\d+(?:-\d+)?(?![\d가-힣]*(?:길|로))")
_PARCEL_CORE = re.compile(r"^.*?[동리가]\s*(?:산\s*)?\d+(?:-\d+)?")
_HAS_DISTRICT = re.compile(r"[시군구]\s")


def _split_sido(address: str) -> tuple[str | None, str]:
    head, _, tail = address.strip().partition(" ")
    sido = _SIDO_KEY.get(head)
    return (sido, tail) if sido else (None, address.strip())


def _lookup_query(sido: str | None, core: str) -> str:
    return f"{_SIDO_NAMES[sido][0]} {core}" if sido else core


def _item_road(item: dict[str, Any]) -> tuple[str | None, str]:
    return _split_sido(_road_text((item.get("address") or {}).get("road") or ""))


def _same_road(sido: str | None, core: str, item: dict[str, Any]) -> bool:
    item_sido, rest = _item_road(item)
    return bool(rest) and (sido is None or item_sido == sido) and _compact(rest).endswith(_compact(core))


def _same_parcel(sido: str | None, core: str, item: dict[str, Any]) -> bool:
    # 주소 검색의 지번은 시·구가 빠진 "삼평동 681" 형태라 저장 주소의 끝부분과 같은지 본다
    item_sido, parcel = _split_sido((item.get("address") or {}).get("parcel") or "")
    return bool(parcel) and (sido is None or item_sido in (None, sido)) and _compact(core).endswith(_compact(parcel))


def _coordinates(item: dict[str, Any]) -> Coordinates | None:
    point = item.get("point") or {}
    try:
        lng, lat = float(point["x"]), float(point["y"])
    except (KeyError, TypeError, ValueError):
        return None
    if not (_LAT_RANGE[0] <= lat <= _LAT_RANGE[1] and _LNG_RANGE[0] <= lng <= _LNG_RANGE[1]):
        return None
    return Coordinates(lat, lng)


class VWorldPlaceSearch:
    def __init__(self, api_key: str, timeout: float = 4.0, fetch: Fetch | None = None):
        self._key = api_key
        self._timeout = timeout
        self._fetch = fetch or self._http

    def _http(self, params: dict[str, str]) -> dict[str, Any]:
        url = f"{_URL}?{urllib.parse.urlencode(params)}"
        try:
            with urllib.request.urlopen(url, timeout=self._timeout) as res:  # noqa: S310  고정된 https 주소
                return json.loads(res.read().decode("utf-8"))
        except (urllib.error.URLError, TimeoutError, ValueError) as e:
            log.warning("VWorld 호출 실패: %s", type(e).__name__)
            raise _failed() from None

    def _items(self, type_: str, query: str, category: str | None = None) -> list[dict[str, Any]]:
        if not self._key:
            raise AppError(503, "place_search_not_configured", "장소 검색 키가 설정되지 않았습니다")
        params = {
            "service": "search", "request": "search", "version": "2.0", "format": "json", "crs": "EPSG:4326",
            "size": _PAGE_SIZE, "page": "1", "type": type_, "query": query, "key": self._key,
        }
        if category:
            params["category"] = category
        response = self._fetch(params).get("response") or {}
        status = response.get("status")
        if status == "NOT_FOUND":
            return []
        if status != "OK":
            log.warning("VWorld 오류 응답: %s", (response.get("error") or {}).get("code"))
            raise _failed()
        return (response.get("result") or {}).get("items") or []

    def locate(self, address: str) -> Coordinates | None:
        """저장한 주소의 좌표. 시·도 표기 차이와 건물번호 뒤의 동·층·호는 무시하되, 도로명과 건물번호(또는 지번)가
        다르면 엉뚱한 곳으로 안내하지 않도록 None. 한 주소에 건물이 여럿이면 건물명이 있는 곳을 먼저 쓴다."""
        sido, rest = _split_sido(_road_text(address))
        road = _ROAD_CORE.match(rest)
        parcel = _PARCEL_CORE.match(_parcel_text(rest))
        searches = []
        if road and (sido or _HAS_DISTRICT.search(road.group(0))):
            searches.append(("ROAD", road.group(0), _same_road))
        if parcel and (sido or _HAS_DISTRICT.search(parcel.group(0))):
            searches.append(("PARCEL", parcel.group(0), _same_parcel))
        for category, core, same in searches:
            matched = [i for i in self._items("ADDRESS", _lookup_query(sido, core), category) if same(sido, core, i)]
            if sido is None and len({_item_road(i)[0] for i in matched}) > 1:
                continue  # 시·도가 없는 "중구 세종대로 110" 이 여러 지역과 맞으면 고르지 않는다
            matched.sort(key=lambda i: not (i.get("address") or {}).get("bldnm"))
            for item in matched:
                coordinates = _coordinates(item)
                if coordinates is not None:
                    return coordinates
        return None


def get_place_search() -> PlaceSearch:
    settings = get_settings()
    return VWorldPlaceSearch(settings.vworld_api_key, settings.place_search_timeout_seconds)
