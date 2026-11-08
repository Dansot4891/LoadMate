from sqlalchemy import select
from sqlalchemy.orm import Session

from app.features.handover.models import Handover


class HandoverRepository:
    def __init__(self, session: Session):
        self.s = session

    def by_key(self, trip_id: str, key: str):
        return self.s.scalars(select(Handover).where(Handover.trip_id == trip_id, Handover.idempotency_key == key)).first()

    def pending(self, trip_id: str):
        return self.s.scalars(select(Handover).where(Handover.trip_id == trip_id, Handover.status == "pending")).first()

    def latest(self, trip_id: str):
        return self.s.scalars(select(Handover).where(Handover.trip_id == trip_id).order_by(Handover.id.desc())).first()

    def get(self, handover_id: int):
        return self.s.get(Handover, handover_id)

    def add(self, handover: Handover):
        self.s.add(handover)
