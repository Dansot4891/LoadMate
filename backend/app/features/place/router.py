from fastapi import APIRouter, Depends, Query
from pydantic import BaseModel

from app.integrations.places.vworld import PlaceSearch, get_place_search

router = APIRouter(prefix="/places", tags=["place"])


class AddressCheckOut(BaseModel):
    found: bool


@router.get("/check", response_model=AddressCheckOut)
def check_address(address: str = Query(min_length=2, max_length=300), places: PlaceSearch = Depends(get_place_search)) -> AddressCheckOut:
    """배송지 직접 입력의 위치 확인. DB 업무가 없어 router 만 둔다. 좌표는 내려 주지 않는다."""
    return AddressCheckOut(found=places.locate(address) is not None)
