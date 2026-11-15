from __future__ import annotations

from fastapi import APIRouter, Depends
from app.features.assistant.schemas import InterpretIn
from sqlalchemy.orm import Session

from app.core.actor import actor_id
from app.core.db import get_session
from app.features.assistant.service import AssistantService
from app.integrations.assistant.note_ai import NoteAi, get_note_ai

router = APIRouter(tags=["assistant"])


@router.post("/assistant/interpret")
def interpret(body: InterpretIn, s: Session = Depends(get_session), actor: str | None = Depends(actor_id),
              note_ai: NoteAi | None = Depends(get_note_ai)) -> dict:
    return AssistantService(s, note_ai).handle(body.trip_id, body.text, actor, body.pending, body.pending_kind)
