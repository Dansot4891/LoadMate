from fastapi import APIRouter, Depends
from sqlalchemy.orm import Session

from app.core.actor import actor_id
from app.core.db import get_session
from app.features.fleet.schemas import DriverOut, EquipmentOut, EquipmentUpdateIn
from app.features.fleet.service import FleetService

router = APIRouter(tags=["fleet"])


def get_service(s: Session = Depends(get_session)) -> FleetService:
    return FleetService(s)


@router.get("/drivers", response_model=dict[str, list[DriverOut]])
def list_drivers(svc: FleetService = Depends(get_service)):
    return svc.list_drivers()


@router.get("/vehicles/{vehicle_id}/equipment", response_model=dict[str, list[EquipmentOut]])
def list_equipment(vehicle_id: str, svc: FleetService = Depends(get_service)):
    return svc.list_equipment(vehicle_id)


@router.post("/equipment/{equipment_id}/status", response_model=EquipmentOut)
def update_equipment(equipment_id: str, body: EquipmentUpdateIn, svc: FleetService = Depends(get_service),
                     actor: str | None = Depends(actor_id)):
    return svc.update_equipment(equipment_id, body, actor)
