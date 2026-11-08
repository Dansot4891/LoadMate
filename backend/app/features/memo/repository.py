from sqlalchemy import or_, select
from sqlalchemy.orm import Session

from app.features.memo.models import Memo


class MemoRepository:
    def __init__(self, session: Session):
        self.s = session

    @staticmethod
    def visible(q, actor: str | None):
        return q.where(or_(Memo.visibility == "shared", Memo.author_id == (actor or "")))

    def list_memos(self, scope_type, scope_id, trip_id, q, actor):
        stmt = select(Memo)
        if scope_type:
            stmt = stmt.where(Memo.scope_type == scope_type)
        if scope_id:
            stmt = stmt.where(Memo.scope_id == scope_id)
        if trip_id:
            stmt = stmt.where(or_(Memo.persistence == "always", Memo.trip_id == trip_id))
        if q:
            stmt = stmt.where(Memo.body.contains(q))
        return list(self.s.scalars(self.visible(stmt, actor).order_by(Memo.updated_at.desc())))

    def relevant_place_memos(self, place_key: str, trip_id: str | None, actor: str | None):
        q = select(Memo).where(Memo.scope_type == "place", Memo.scope_id == place_key)
        q = q.where(or_(Memo.persistence == "always", Memo.trip_id == (trip_id or "")))
        return list(self.s.scalars(self.visible(q, actor).order_by(Memo.updated_at.desc())))

    def shared_for_handover(self, trip_id: str, place_keys: list[str]):
        q = select(Memo).where(Memo.visibility == "shared").where(
            or_(Memo.scope_type == "trip", Memo.scope_type == "vehicle", Memo.scope_id.in_(place_keys))
        ).where(or_(Memo.persistence == "always", Memo.trip_id == trip_id))
        return list(self.s.scalars(q.order_by(Memo.updated_at.desc())))

    def by_key(self, author: str, key: str):
        return self.s.scalars(select(Memo).where(Memo.author_id == author, Memo.idempotency_key == key)).first()

    def get(self, memo_id: int):
        return self.s.get(Memo, memo_id)

    def add(self, memo: Memo):
        self.s.add(memo)
