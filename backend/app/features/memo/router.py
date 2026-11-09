from fastapi import APIRouter, Depends, Query
from sqlalchemy.orm import Session

from app.core.actor import actor_id
from app.core.db import get_session
from app.features.memo.schemas import MemoIn, MemoOut, MemoUpdateIn, ScopeType
from app.features.memo.service import MemoService
from app.integrations.assistant.note_ai import NoteAi, get_note_ai

router = APIRouter(tags=["memo"])


def get_service(s: Session = Depends(get_session), note_ai: NoteAi | None = Depends(get_note_ai)) -> MemoService:
    return MemoService(s, note_ai=note_ai)


@router.get("/memos", response_model=dict[str, list[MemoOut]])
def list_memos(scope_type: ScopeType | None = None, scope_id: str | None = None, trip_id: str | None = None,
               q: str | None = Query(default=None, max_length=100),
               svc: MemoService = Depends(get_service), actor: str | None = Depends(actor_id)):
    return svc.list_memos(scope_type, scope_id, trip_id, q, actor)


@router.get("/trips/{trip_id}/stops/{stop_id}/memos", response_model=dict[str, list[MemoOut]])
def stop_memos(trip_id: str, stop_id: str, svc: MemoService = Depends(get_service), actor: str | None = Depends(actor_id)):
    return svc.stop_memos(trip_id, stop_id, actor)


@router.post("/memos", response_model=dict, status_code=201)
def create_memo(body: MemoIn, svc: MemoService = Depends(get_service), actor: str | None = Depends(actor_id)):
    return svc.create(body, actor)


@router.put("/memos/{memo_id}", response_model=MemoOut)
def update_memo(memo_id: int, body: MemoUpdateIn, svc: MemoService = Depends(get_service), actor: str | None = Depends(actor_id)):
    return svc.update(memo_id, body, actor)


@router.delete("/memos/{memo_id}")
def delete_memo(memo_id: int, svc: MemoService = Depends(get_service), actor: str | None = Depends(actor_id)) -> dict:
    return svc.delete(memo_id, actor)
