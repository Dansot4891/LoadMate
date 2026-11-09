from sqlalchemy.exc import IntegrityError
from sqlalchemy.orm import Session

from app.core.errors import AppError
from app.core.time import as_utc, utcnow
from app.features.memo.models import Memo
from app.features.memo.repository import MemoRepository
from app.features.memo.schemas import MemoIn, MemoOut, MemoUpdateIn
from app.features.trip.repository import TripRepository
from app.integrations.assistant.note_ai import NoteAi, NoteAiUnavailable

SHARED_AUTHOR = "shared"  # 누가 타든 함께 쓰는 기록의 작성자 값


def memo_out(m: Memo) -> MemoOut:
    return MemoOut(id=m.id, scope_type=m.scope_type, scope_id=m.scope_id, body=m.body, tags=m.tags or [], visibility=m.visibility,
                   persistence=m.persistence, trip_id=m.trip_id, source=m.source, category=m.category, location=m.location,
                   author_id=m.author_id,
                   created_at=as_utc(m.created_at), updated_at=as_utc(m.updated_at), updated_by=m.updated_by)


class MemoService:
    def __init__(self, session: Session, now=utcnow, note_ai: NoteAi | None = None):
        self.s = session
        self.repo = MemoRepository(session)
        self.trips = TripRepository(session)
        self.now = now
        self.note_ai = note_ai

    @staticmethod
    def _require_actor(actor: str | None) -> str:
        if not actor:
            raise AppError(422, "driver_required", "기사를 먼저 선택하세요 (X-Driver-Id)")
        return actor

    def _visible_memo(self, memo_id: int, actor: str | None) -> Memo:
        m = self.repo.get(memo_id)
        if m is None or (m.visibility == "private" and m.author_id != actor):
            raise AppError(404, "memo_not_found", "메모를 찾을 수 없습니다")
        return m

    def _category(self, body: MemoIn) -> tuple[str, str | None]:
        """종류를 정하지 않은 차량 노트는 AI 가 분류한다. AI 를 쓸 수 없어도 저장은 막지 않는다."""
        if body.category is not None:
            return body.category, body.location
        if body.scope_type == "place":
            return "place", body.location
        if body.scope_type != "vehicle" or self.note_ai is None:
            return "other", body.location
        try:
            c = self.note_ai.classify(body.body)
        except NoteAiUnavailable:
            return "other", body.location
        return c.category, body.location or c.location

    def list_memos(self, scope_type, scope_id, trip_id, q, actor):
        return {"memos": [memo_out(m) for m in self.repo.list_memos(scope_type, scope_id, trip_id, q, actor)]}

    def stop_memos(self, trip_id: str, stop_id: str, actor: str | None):
        stop = self.trips.get_stop(trip_id, stop_id)
        if stop is None:
            raise AppError(404, "stop_not_found", "방문지를 찾을 수 없습니다")
        return {"memos": [memo_out(m) for m in self.repo.relevant_place_memos(stop.place_key, trip_id, actor)]}

    def create(self, body: MemoIn, actor: str | None):
        author = self._require_actor(actor) if body.visibility == "private" else (actor or SHARED_AUTHOR)
        existing = self.repo.by_key(author, body.idempotency_key)
        if existing is not None:
            return {"memo": memo_out(existing).model_dump(mode="json"), "replayed": True}
        if body.persistence == "this_trip" and not body.trip_id:
            raise AppError(422, "trip_required", "이번 운행 메모에는 운행 ID 가 필요합니다")
        category, location = self._category(body)
        now = self.now()
        m = Memo(idempotency_key=body.idempotency_key, scope_type=body.scope_type, scope_id=body.scope_id, body=body.body,
                 tags=body.tags, visibility=body.visibility, persistence=body.persistence, trip_id=body.trip_id, source=body.source,
                 category=category, location=location, author_id=author, created_at=now, updated_at=now, updated_by=author)
        self.repo.add(m)
        try:
            self.s.commit()
        except IntegrityError as e:
            self.s.rollback()
            raise AppError(409, "idempotency_key_conflict", "같은 요청이 이미 처리되었습니다. 다시 조회하세요") from e
        return {"memo": memo_out(m).model_dump(mode="json"), "replayed": False}

    def update(self, memo_id: int, body: MemoUpdateIn, actor: str | None):
        """값 지정이라 다시 보내도 결과가 같으므로 멱등 키를 받지 않는다."""
        m = self._visible_memo(memo_id, actor)
        editor = self._require_actor(actor) if m.visibility == "private" else (actor or SHARED_AUTHOR)
        if body.visibility is not None and m.author_id != editor:
            raise AppError(403, "not_author", "공유 범위는 작성자만 바꿀 수 있습니다")
        now = self.now()
        if body.body is not None:
            m.body = body.body
        if body.tags is not None:
            m.tags = body.tags
        if body.visibility is not None:
            m.visibility = body.visibility
        if body.category is not None:
            m.category = body.category
        if body.location is not None:
            m.location = body.location or None
        m.updated_at = now
        m.updated_by = editor
        self.s.commit()
        return memo_out(m)

    def delete(self, memo_id: int, actor: str | None):
        self.repo.delete(self._visible_memo(memo_id, actor))
        self.s.commit()
        return {"deleted": True}
