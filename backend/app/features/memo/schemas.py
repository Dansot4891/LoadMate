from __future__ import annotations
from datetime import datetime
from typing import Literal
from pydantic import BaseModel, Field, field_serializer
from app.core.time import iso_utc

ScopeType = Literal["place", "vehicle", "equipment", "trip"]
Category = Literal["tool", "issue", "place", "other"]


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
    category: Category | None = None
    location: str | None = Field(default=None, max_length=200)


class MemoUpdateIn(BaseModel):
    body: str | None = Field(default=None, min_length=1, max_length=2000)
    tags: list[str] | None = None
    visibility: Literal["private", "shared"] | None = None
    category: Category | None = None
    location: str | None = Field(default=None, max_length=200)


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
    category: str
    location: str | None
    author_id: str
    created_at: datetime
    updated_at: datetime
    updated_by: str

    @field_serializer("created_at", "updated_at", when_used="json")
    def _ser(self, v):
        return iso_utc(v)


