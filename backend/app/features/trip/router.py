from __future__ import annotations

from fastapi import APIRouter, Depends, Query
from sqlalchemy.orm import Session

from app.core.actor import actor_id
from app.core.db import get_session
from app.features.trip.schemas import (
    CargoQtyIn,
    ChangeResultOut,
    HistoryOut,
    IssueIn,
    IssueResolveIn,
    PickupIn,
    PlacementsIn,
    ReorderIn,
    RoutePointsOut,
    SimpleChangeIn,
    StopAddIn,
    StopCancelIn,
    StopPlaceIn,
    TripEndIn,
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
from app.integrations.places.vworld import PlaceSearch, get_place_search

router =APIRouter(prefix="/trips", tags=["trip"])


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
def confirm_baseline(trip_id: str, body: BaselineIn, svc: TripService = Depends(get_service), actor: str | None = Depends(actor_id)) -> BaselineResultOut:
    return svc.confirm_baseline(trip_id, body, actor)


@router.post("/{trip_id}/stops/{stop_id}/complete", response_model=CompleteResultOut)
def complete_stop(trip_id: str, stop_id: str, body: CompleteIn, svc: TripService = Depends(get_service), actor: str | None = Depends(actor_id)) -> CompleteResultOut:
    return svc.complete_stop(trip_id, stop_id, body, actor)


Svc = Depends(get_service)
Actor = Depends(actor_id)


@router.get("/{trip_id}/route-points", response_model=RoutePointsOut)
def route_points(trip_id: str, stop_ids: str = Query(min_length=1), svc: TripService = Svc, places: PlaceSearch = Depends(get_place_search)) -> RoutePointsOut:
    return svc.route_points(trip_id, [s for s in stop_ids.split(",") if s], places)


@router.post("/{trip_id}/stops", response_model=ChangeResultOut)
def add_stop(trip_id: str, body: StopAddIn, svc: TripService = Svc, actor: str | None = Actor) -> ChangeResultOut:
    return svc.add_stop(trip_id, body, actor)


@router.post("/{trip_id}/stops/{stop_id}/place", response_model=ChangeResultOut)
def change_stop_place(trip_id: str, stop_id: str, body: StopPlaceIn, svc: TripService = Svc, actor: str | None = Actor) -> ChangeResultOut:
    return svc.change_stop_place(trip_id, stop_id, body, actor)


@router.post("/{trip_id}/stops/reorder", response_model=ChangeResultOut)
def reorder_stops(trip_id: str, body: ReorderIn, svc: TripService = Svc, actor: str | None = Actor) -> ChangeResultOut:
    return svc.reorder_stops(trip_id, body, actor)


@router.post("/{trip_id}/stops/{stop_id}/cancel", response_model=ChangeResultOut)
def cancel_stop(trip_id: str, stop_id: str, body: StopCancelIn, svc: TripService = Svc, actor: str | None = Actor) -> ChangeResultOut:
    return svc.cancel_stop(trip_id, stop_id, body, actor)


@router.post("/{trip_id}/stops/{stop_id}/cargo", response_model=ChangeResultOut)
def change_cargo(trip_id: str, stop_id: str, body: CargoQtyIn, svc: TripService = Svc, actor: str | None = Actor) -> ChangeResultOut:
    return svc.change_cargo_qty(trip_id, stop_id, body, actor)


@router.post("/{trip_id}/end", response_model=ChangeResultOut)
def end_trip(trip_id: str, body: TripEndIn, svc: TripService = Svc, actor: str | None = Actor) -> ChangeResultOut:
    return svc.end_trip(trip_id, body, actor)


@router.post("/{trip_id}/pickups", response_model=ChangeResultOut)
def add_pickup(trip_id: str, body: PickupIn, svc: TripService = Svc, actor: str | None = Actor) -> ChangeResultOut:
    return svc.add_pickup(trip_id, body, actor)


@router.post("/{trip_id}/pickups/{pickup_id}/return", response_model=ChangeResultOut)
def return_pickup(trip_id: str, pickup_id: int, body: SimpleChangeIn, svc: TripService = Svc, actor: str | None = Actor) -> ChangeResultOut:
    return svc.return_pickup(trip_id, pickup_id, body, actor)


@router.post("/{trip_id}/issues", response_model=ChangeResultOut)
def record_issue(trip_id: str, body: IssueIn, svc: TripService = Svc, actor: str | None = Actor) -> ChangeResultOut:
    return svc.record_issue(trip_id, body, actor)


@router.post("/{trip_id}/issues/{issue_id}/resolve", response_model=ChangeResultOut)
def resolve_issue(trip_id: str, issue_id: int, body: IssueResolveIn, svc: TripService = Svc, actor: str | None = Actor) -> ChangeResultOut:
    return svc.resolve_issue(trip_id, issue_id, body, actor)


@router.post("/{trip_id}/placements", response_model=ChangeResultOut)
def record_placements(trip_id: str, body: PlacementsIn, svc: TripService = Svc) -> ChangeResultOut:
    return svc.record_placements(trip_id, body)


@router.get("/{trip_id}/history", response_model=HistoryOut)
def history(trip_id: str, svc: TripService = Svc) -> HistoryOut:
    return svc.history(trip_id)
