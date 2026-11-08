from __future__ import annotations

from fastapi import APIRouter, Depends
from pydantic import BaseModel, Field
from sqlalchemy.orm import Session

from app.core.actor import actor_id
from app.core.db import get_session
from app.features.assistant.service import AssistantService

router = APIRouter(tags=["assistant"])


class InterpretIn(BaseModel):
    trip_id: str
    text: str = Field(min_length=1, max_length=500)
    # 재질문(clarify) 에 대한 답이면 이전 응답의 action 을 그대로 보낸다
    pending: dict | None = None


@router.post("/assistant/interpret")
def interpret(body: InterpretIn, s: Session = Depends(get_session), actor: str | None = Depends(actor_id)) -> dict:
    return AssistantService(s).handle(body.trip_id, body.text, actor, body.pending)
