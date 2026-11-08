"""Assistant reads feature data through their public repositories."""

from app.features.fleet.repository import FleetRepository
from app.features.memo.repository import MemoRepository


class AssistantRepository:
    def __init__(self, session):
        self.fleet = FleetRepository(session)
        self.memo = MemoRepository(session)
