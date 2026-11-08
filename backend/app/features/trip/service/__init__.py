from datetime import timedelta
from sqlalchemy.orm import Session
from app.core.time import utcnow
from app.features.trip.repository import TripRepository
from app.features.trip.service.common import CommonMixin, DEFAULT_STALE_AFTER, request_hash, ZONE_NAMES
from app.features.trip.service.trips import TripsMixin
from app.features.trip.service.stops import StopsMixin
from app.features.trip.service.loading import LoadingMixin
from app.features.trip.service.pickups import PickupsMixin
from app.features.trip.service.issues import IssuesMixin


class TripService(TripsMixin, StopsMixin, LoadingMixin, PickupsMixin, IssuesMixin, CommonMixin):
    def __init__(self, session: Session, now=utcnow, stale_after: timedelta = DEFAULT_STALE_AFTER):
        self.s = session
        self.repo = TripRepository(session)
        self.now = now
        self.stale_after = stale_after
