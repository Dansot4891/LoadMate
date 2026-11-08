from __future__ import annotations

from sqlalchemy import select
from sqlalchemy.orm import Session, selectinload

from app.features.trip.models import Issue, Observation, Pickup, Stop, Trip, TripEvent


class TripRepository:
    """SQLAlchemy 조회·저장만 담당. 업무 규칙은 service 에 둔다."""

    def __init__(self, session: Session):
        self.s = session

    def list_trips(self) -> list[Trip]:
        return list(self.s.scalars(select(Trip).options(selectinload(Trip.stops)).order_by(Trip.created_at)))

    def get_trip(self, trip_id: str, for_update: bool = False) -> Trip | None:
        q = select(Trip).where(Trip.id == trip_id).options(selectinload(Trip.stops).selectinload(Stop.cargo))
        if for_update:
            q = q.with_for_update(of=Trip)  # 같은 운행 동시 요청 직렬화 (PostgreSQL)
        return self.s.scalars(q).first()

    def get_stop(self, trip_id: str, stop_id: str) -> Stop | None:
        return self.s.scalars(select(Stop).where(Stop.id == stop_id, Stop.trip_id == trip_id)).first()

    def events(self, trip_id: str) -> list[TripEvent]:
        return list(self.s.scalars(select(TripEvent).where(TripEvent.trip_id == trip_id).order_by(TripEvent.id)))

    def event_by_key(self, trip_id: str, key: str) -> TripEvent | None:
        return self.s.scalars(
            select(TripEvent).where(TripEvent.trip_id == trip_id, TripEvent.idempotency_key == key)
        ).first()

    def baseline_event(self, trip_id: str) -> TripEvent | None:
        return self.s.scalars(
            select(TripEvent).where(TripEvent.trip_id == trip_id, TripEvent.type == "baseline_confirmed")
        ).first()

    def latest_observation(self, trip_id: str) -> Observation | None:
        return self.s.scalars(
            select(Observation)
            .where(Observation.trip_id == trip_id)
            .order_by(Observation.observed_at.desc(), Observation.id.desc())
            .limit(1)
        ).first()

    def observation(self, trip_id: str, obs_id: int) -> Observation | None:
        return self.s.scalars(select(Observation).where(Observation.trip_id == trip_id, Observation.id == obs_id)).first()

    def observation_by_key(self, trip_id: str, key: str) -> Observation | None:
        return self.s.scalars(
            select(Observation).where(Observation.trip_id == trip_id, Observation.idempotency_key == key)
        ).first()

    def pickups(self, trip_id: str) -> list[Pickup]:
        return list(self.s.scalars(select(Pickup).where(Pickup.trip_id == trip_id).order_by(Pickup.id)))

    def issues(self, trip_id: str) -> list[Issue]:
        return list(self.s.scalars(select(Issue).where(Issue.trip_id == trip_id).order_by(Issue.id)))

    def add(self, obj) -> None:
        self.s.add(obj)
