"""현장·차량·장비·운행 메모 (F49~F55). 개인 기록은 작성자만 본다 (R12)."""

from __future__ import annotations

from datetime import datetime
from typing import Literal

from fastapi import APIRouter, Depends, Query
from pydantic import BaseModel, Field, field_serializer
from sqlalchemy import or_, select
from sqlalchemy.exc import IntegrityError
from sqlalchemy.orm import Session

from app.core.actor import actor_id
from app.core.db import get_session
from app.core.errors import AppError
from app.core.time import as_utc, iso_utc, utcnow
from app.features.memo.models import Memo
from app.features.trip.models import Stop

router = APIRouter(tags=["memo"])

ScopeType = Literal["place", "vehicle", "equipment", "trip"]


class MemoIn(BaseModel):
    idempotency_key: str = Field(min_length=8, max_length=64)
    scope_type: ScopeType
    scope_id: str = Field(min_length=1, max_length=64)
    body: str = Field(min_length=1, max_length=2000)
    tags: list[str] = []
    visibility: Literal["private", "shared"] = "private"
    persistence: Literal["always", "this_trip"] = "always"
    trip_id: str | None = None
    source: Literal["screen", "voice"] = "screen"


class MemoUpdateIn(BaseModel):
    body: str | None = Field(default=None, min_length=1, max_length=2000)
    tags: list[str] | None = None
    visibility: Literal["private", "shared"] | None = None


class MemoOut(BaseModel):
    id: int
    scope_type: str
    scope_id: str
    body: str
    tags: list[str]
    visibility: str
    persistence: str
    trip_id: str | None
    source: str
    author_id: str
    created_at: datetime
    updated_at: datetime
    updated_by: str

    @field_serializer("created_at", "updated_at", when_used="json")
    def _ser(self, v):
        return iso_utc(v)


def memo_out(m: Memo) -> MemoOut:
    return MemoOut(id=m.id, scope_type=m.scope_type, scope_id=m.scope_id, body=m.body, tags=m.tags or [], visibility=m.visibility,
                   persistence=m.persistence, trip_id=m.trip_id, source=m.source, author_id=m.author_id,
                   created_at=as_utc(m.created_at), updated_at=as_utc(m.updated_at), updated_by=m.updated_by)


def visible(q, actor: str | None):
    """공유 기록 + 내 개인 기록만."""
    return q.where(or_(Memo.visibility == "shared", Memo.author_id == (actor or "")))


def relevant_place_memos(s: Session, place_key: str, trip_id: str | None, actor: str | None) -> list[Memo]:
    """F55: 이 장소의 상시 메모 + 이번 운행 메모."""
    q = select(Memo).where(Memo.scope_type == "place", Memo.scope_id == place_key)
    q = q.where(or_(Memo.persistence == "always", Memo.trip_id == (trip_id or "")))
    return list(s.scalars(visible(q, actor).order_by(Memo.updated_at.desc())))


def _require_actor(actor: str | None) -> str:
    if not actor:
        raise AppError(422, "driver_required", "기사를 먼저 선택하세요 (X-Driver-Id)")
    return actor


@router.get("/memos", response_model=dict[str, list[MemoOut]])
def list_memos(scope_type: ScopeType | None = None, scope_id: str | None = None, trip_id: str | None = None,
               q: str | None = Query(default=None, max_length=100),
               s: Session = Depends(get_session), actor: str | None = Depends(actor_id)):
    stmt = select(Memo)
    if scope_type:
        stmt = stmt.where(Memo.scope_type == scope_type)
    if scope_id:
        stmt = stmt.where(Memo.scope_id == scope_id)
    if trip_id:
        stmt = stmt.where(or_(Memo.persistence == "always", Memo.trip_id == trip_id))
    if q:  # F42 기록 검색
        stmt = stmt.where(Memo.body.contains(q))
    return {"memos": [memo_out(m) for m in s.scalars(visible(stmt, actor).order_by(Memo.updated_at.desc()))]}


@router.get("/trips/{trip_id}/stops/{stop_id}/memos", response_model=dict[str, list[MemoOut]])
def stop_memos(trip_id: str, stop_id: str, s: Session = Depends(get_session), actor: str | None = Depends(actor_id)):
    stop = s.get(Stop, stop_id)
    if stop is None or stop.trip_id != trip_id:
        raise AppError(404, "stop_not_found", "방문지를 찾을 수 없습니다")
    return {"memos": [memo_out(m) for m in relevant_place_memos(s, stop.place_key, trip_id, actor)]}


@router.post("/memos", response_model=dict, status_code=201)
def create_memo(body: MemoIn, s: Session = Depends(get_session), actor: str | None = Depends(actor_id)):
    author = _require_actor(actor)
    existing = s.scalars(select(Memo).where(Memo.author_id == author, Memo.idempotency_key == body.idempotency_key)).first()
    if existing is not None:
        return {"memo": memo_out(existing).model_dump(mode="json"), "replayed": True}
    if body.persistence == "this_trip" and not body.trip_id:
        raise AppError(422, "trip_required", "이번 운행 메모에는 운행 ID 가 필요합니다")
    now = utcnow()
    m = Memo(idempotency_key=body.idempotency_key, scope_type=body.scope_type, scope_id=body.scope_id, body=body.body,
             tags=body.tags, visibility=body.visibility, persistence=body.persistence, trip_id=body.trip_id, source=body.source,
             author_id=author, created_at=now, updated_at=now, updated_by=author)
    s.add(m)
    try:
        s.commit()
    except IntegrityError as e:
        s.rollback()
        raise AppError(409, "idempotency_key_conflict", "같은 요청이 이미 처리되었습니다. 다시 조회하세요") from e
    return {"memo": memo_out(m).model_dump(mode="json"), "replayed": False}


@router.put("/memos/{memo_id}", response_model=MemoOut)
def update_memo(memo_id: int, body: MemoUpdateIn, s: Session = Depends(get_session), actor: str | None = Depends(actor_id)):
    editor = _require_actor(actor)
    m = s.get(Memo, memo_id)
    if m is None or (m.visibility == "private" and m.author_id != editor):
        raise AppError(404, "memo_not_found", "메모를 찾을 수 없습니다")
    if body.visibility is not None and m.author_id != editor:
        raise AppError(403, "not_author", "공유 범위는 작성자만 바꿀 수 있습니다")
    if body.body is not None:
        m.body = body.body
    if body.tags is not None:
        m.tags = body.tags
    if body.visibility is not None:
        m.visibility = body.visibility
    m.updated_at = utcnow()
    m.updated_by = editor  # F54 최신화: 공유 메모는 다른 기사도 고칠 수 있고 수정자가 남는다
    s.commit()
    return memo_out(m)
