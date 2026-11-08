from sqlalchemy.orm import Session
from app.core.errors import AppError
from app.core.time import as_utc, utcnow
from app.features.fleet.repository import FleetRepository
from app.features.fleet.schemas import DriverOut, EquipmentOut, EquipmentUpdateIn


class FleetService:
    def __init__(self, session: Session, now=utcnow):
        self.s = session
        self.repo = FleetRepository(session)
        self.now = now

    @staticmethod
    def equipment_out(e) -> EquipmentOut:
        return EquipmentOut(id=e.id, vehicle_id=e.vehicle_id, name=e.name, location=e.location,
                            status=e.status, note=e.note, updated_by=e.updated_by,
                            updated_at=as_utc(e.updated_at) if e.updated_at else None)

    def list_drivers(self):
        return {"drivers": [DriverOut(id=d.id, name=d.name) for d in self.repo.drivers()]}

    def list_equipment(self, vehicle_id: str):
        if self.repo.vehicle(vehicle_id) is None:
            raise AppError(404, "vehicle_not_found", "차량을 찾을 수 없습니다")
        return {"equipment": [self.equipment_out(e) for e in self.repo.equipment(vehicle_id)]}

    def update_equipment(self, equipment_id: str, body: EquipmentUpdateIn, actor: str | None):
        """A state assignment is idempotent, so it needs no idempotency key."""
        e = self.repo.equipment_by_id(equipment_id)
        if e is None:
            raise AppError(404, "equipment_not_found", "장비를 찾을 수 없습니다")
        e.status = body.status
        if body.location is not None:
            e.location = body.location
        if body.note is not None:
            e.note = body.note
        e.updated_by = actor
        e.updated_at = self.now()
        self.s.commit()
        return self.equipment_out(e)
