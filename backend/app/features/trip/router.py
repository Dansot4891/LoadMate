from __future__ import annotations

from fastapi import APIRouter, Depends
from sqlalchemy.orm import Session

from app.core.db import get_session
from app.features.trip.schemas import (
    BaselineIn,
    BaselineResultOut,
    CompleteIn,
    CompleteResultOut,
    ObservationCreatedOut,
    ObservationIn,
    TripListOut,
    TripOut,
)
from app.features.trip.service import TripService

router = APIRouter(prefix="/trips", tags=["trip"])


def get_service(session: Session = Depends(get_session)) -> TripService:
    return TripService(session)


@router.get("", response_model=TripListOut)
def list_trips(svc: TripService = Depends(get_service)) -> TripListOut:
    return svc.list_trips()


@router.get("/{trip_id}", response_model=TripOut)
def get_trip(trip_id: str, svc: TripService = Depends(get_service)) -> TripOut:
    return svc.get_trip(trip_id)


@router.post("/{trip_id}/observations", response_model=ObservationCreatedOut, status_code=201)
def record_observation(trip_id: str, body: ObservationIn, svc: TripService = Depends(get_service)) -> ObservationCreatedOut:
    return svc.record_observation(trip_id, body)


@router.post("/{trip_id}/baseline", response_model=BaselineResultOut)
def confirm_baseline(trip_id: str, body: BaselineIn, svc: TripService = Depends(get_service)) -> BaselineResultOut:
    return svc.confirm_baseline(trip_id, body)


@router.post("/{trip_id}/stops/{stop_id}/complete", response_model=CompleteResultOut)
def complete_stop(trip_id: str, stop_id: str, body: CompleteIn, svc: TripService = Depends(get_service)) -> CompleteResultOut:
    return svc.complete_stop(trip_id, stop_id, body)
