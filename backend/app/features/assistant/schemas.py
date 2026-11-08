from pydantic import BaseModel, Field


class InterpretIn(BaseModel):
    trip_id: str
    text: str = Field(min_length=1, max_length=500)
    # 재질문(clarify) 에 대한 답이면 이전 응답의 action 을 그대로 보낸다
    pending: dict | None = None


