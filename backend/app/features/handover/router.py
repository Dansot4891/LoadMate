from __future__ import annotations

from fastapi import APIRouter, Depends
from app.features.handover.schemas import HandoverIn
from sqlalchemy.orm import Session

from app.core.actor import actor_id
from app.core.db import get_session
from app.features.handover.service import HandoverService

router = APIRouter(tags=["handover"])


def svc(s: Session = Depends(get_session)) -> HandoverService:
    return HandoverService(s)


@router.get("/trips/{trip_id}/handover/preview")
def preview(trip_id: str, h: HandoverService = Depends(svc)) -> dict:
    return h.preview(trip_id)


@router.get("/trips/{trip_id}/handover")
def latest(trip_id: str, h: HandoverService = Depends(svc)) -> dict:
    return h.latest(trip_id)


@router.post("/trips/{trip_id}/handover", status_code=201)
def create(trip_id: str, body: HandoverIn, h: HandoverService = Depends(svc), actor: str | None = Depends(actor_id)) -> dict:
    return h.create(trip_id, body.idempotency_key, body.to_driver_id, body.note, actor)


@router.post("/handovers/{handover_id}/accept")
def accept(handover_id: int, h: HandoverService = Depends(svc), actor: str | None = Depends(actor_id)) -> dict:
    return h.accept(handover_id, actor)
