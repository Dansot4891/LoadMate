from typing import Literal

from pydantic import BaseModel, Field


class InterpretIn(BaseModel):
    trip_id: str
    text: str = Field(min_length=1, max_length=500)
    # 재질문(clarify) 에 대한 답이면 이전 응답의 action 을 그대로 보낸다
    pending: dict | None = None
    # clarify: 되물은 질문의 대답, confirm: 저장 확인 창에 대한 대답. pending 이 있을 때만 쓴다
    pending_kind: Literal["clarify", "confirm"] = "clarify"


