"""규칙 기반 발화 해석기 (대화 AI 어댑터의 기본 구현).

대화 AI 모델·실행 위치가 미정이라(docs/next-steps.md 결정 필요) 키워드 규칙으로 의도·대상·수량만 뽑는다.
같은 Intent 를 돌려주는 다른 어댑터(LLM 등)로 교체할 수 있다. 업무 실행·확정은 하지 않는다 (기획서 17장).
"""

from __future__ import annotations

import re
from dataclasses import dataclass, field
from typing import Literal

IntentType = Literal[
    "query_next_stop", "query_here", "query_remaining", "query_memo", "query_equipment",
    "add_pickup", "add_memo", "navigate_next", "unknown",
]

_NUM = {"한": 1, "하나": 1, "두": 2, "둘": 2, "세": 3, "셋": 3, "석": 3, "네": 4, "넷": 4, "다섯": 5, "여섯": 6,
        "일곱": 7, "여덟": 8, "아홉": 9, "열": 10}
# 물품 이름 → 종류(kind). 서버·앱이 쓰는 kind 와 맞춘다
ITEM_KINDS = {"빈 용기": "container", "빈용기": "container", "용기": "container", "박스": "box", "상자": "box",
              "팔레트": "pallet", "파렛트": "pallet", "카트": "cart"}
_EQUIP_WORDS = ["핸드카트", "카트", "고정 끈", "끈", "공구함", "공구"]


@dataclass
class Intent:
    type: IntentType
    slots: dict = field(default_factory=dict)
    text: str = ""


def parse_qty(text: str) -> int | None:
    m = re.search(r"(\d+)\s*(개|박스|상자|통)?", text)
    if m:
        return int(m.group(1))
    m = re.search(r"(한|하나|두|둘|세|셋|석|네|넷|다섯|여섯|일곱|여덟|아홉|열)\s*(개|박스|상자|통)", text)
    return _NUM[m.group(1)] if m else None


def parse_item(text: str) -> tuple[str, str] | None:
    for name in sorted(ITEM_KINDS, key=len, reverse=True):
        if name in text:
            return ("빈 용기" if ITEM_KINDS[name] == "container" else name), ITEM_KINDS[name]
    return None


def parse_return_place(text: str, loose: bool = True) -> str | None:
    """"창고로 반납" 같은 표현에서 장소를 뽑는다. loose 이면 "창고" 단어만 있어도 창고로 본다."""
    m = re.search(r"(\S+?)(?:으로|로|에)\s*(?:반납|돌려|가져다)", text)
    if m:
        return m.group(1).strip()
    if loose and "창고" in text:
        return "창고"
    return None


def interpret(text: str) -> Intent:
    t = re.sub(r"\s+", " ", text.strip())
    if not t:
        return Intent("unknown", text=t)
    # 변경 요청 (확인 후 저장)
    if re.search(r"회수|수거|가져왔|실었|받아왔", t):
        item = parse_item(t)
        return Intent("add_pickup", {"item_name": item[0] if item else None, "kind": item[1] if item else None,
                                     "qty": parse_qty(t), "return_place": parse_return_place(t)}, t)
    if re.search(r"메모|기록해|기억해|다음부터|다음에는", t):
        body = re.sub(r"^(메모|기록)(해 ?줘|해|하기)?[:,]?\s*", "", t)
        body = re.sub(r"\s*(라고|이라고)?\s*(메모|기록)(해 ?줘|해|남겨 ?줘|남겨)\.?$", "", body).strip() or t
        if re.search(r"(메모|주의|특이)\S*\s*(뭐|있어|알려|읽어)", t) and not re.search(r"다음부터|다음에는", t):
            return Intent("query_memo", {"target": "next" if "다음" in t else "here"}, t)
        return Intent("add_memo", {"body": body}, t)
    if re.search(r"(안내|내비|길)\S*\s*(해|시작|보내|켜|찾)", t) or re.search(r"다음 ?(장소|목적지|곳)으?로\s*(가|출발)", t):
        return Intent("navigate_next", {}, t)
    # 조회 (즉시 답변)
    if re.search(r"(몇 ?개|얼마나|잔량).*(남|실려|있)", t) or "잔량" in t:
        return Intent("query_remaining", {}, t)
    for w in _EQUIP_WORDS:
        if w in t and re.search(r"어디|위치|있어", t):
            return Intent("query_equipment", {"word": w}, t)
    if re.search(r"(주의|특이|메모|조심)", t):
        return Intent("query_memo", {"target": "next" if "다음" in t else "here"}, t)
    if re.search(r"다음 ?(장소|목적지|곳|배송지)|다음에", t) and re.search(r"뭐|무엇|몇|내려|물품|어디", t):
        return Intent("query_next_stop", {}, t)
    if re.search(r"(여기|이번|지금).*(뭐|몇|내려|물품)", t) or re.search(r"뭐 ?내려", t):
        return Intent("query_here", {}, t)
    return Intent("unknown", text=t)
