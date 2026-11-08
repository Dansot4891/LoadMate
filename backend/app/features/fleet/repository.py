from sqlalchemy import select
from sqlalchemy.orm import Session
from app.features.fleet.models import Driver, Equipment, Vehicle


class FleetRepository:
    def __init__(self, session: Session):
        self.s = session

    def drivers(self):
        return list(self.s.scalars(select(Driver).order_by(Driver.id)))

    def vehicle(self, vehicle_id: str):
        return self.s.get(Vehicle, vehicle_id)

    def equipment(self, vehicle_id: str):
        return list(self.s.scalars(select(Equipment).where(Equipment.vehicle_id == vehicle_id).order_by(Equipment.id)))

    def equipment_by_id(self, equipment_id: str):
        return self.s.get(Equipment, equipment_id)

    def alert_equipment(self, vehicle_id: str):
        return list(self.s.scalars(select(Equipment).where(Equipment.vehicle_id == vehicle_id, Equipment.status != "ok")))
