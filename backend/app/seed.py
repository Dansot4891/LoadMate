"""샘플 운행 데이터 (기획서 14장: 목적지 A·B·C, 박스 10개 = A3·B3·C4).

실제 배송 데이터가 아니다. data_source="sample" 로 저장되고 앱은 "샘플"로 표시한다 (원칙 5).
좌표는 에뮬레이터 내비에서 경로 생성이 확인된 공공장소(역) 근처이며 실제 납품처가 아니다.

    python -m app.seed           # 없으면 생성
    python -m app.seed --reset   # 샘플 운행을 지우고 다시 생성 (시연 반복용)
"""

from __future__ import annotations

import sys

from sqlalchemy import delete
from sqlalchemy import delete, select
from sqlalchemy.orm import Session
from app.core.time import utcnow

from app.features.fleet.models import Driver, Equipment, Vehicle
from app.features.handover.models import Handover
from app.features.memo.models import Memo
from app.features.trip.models import CargoLine, CargoUnit, Issue, Observation, Pickup, PlacementRequest, Stop, Trip, TripEvent

SAMPLE_TRIP_ID = "sample-trip-1"

SAMPLE_VEHICLE_ID = "vehicle-1"

_STOPS = [
    # id, label, name, address, lat, lng, boxes, place_key, zone(먼저 내릴 물품이 뒷문 쪽)
    ("stop-a", "A", "판교역 납품처 (샘플)", "경기 성남시 분당구 판교역 인근", 37.3947, 127.1112, 3, "place-pangyo", "rear"),
    ("stop-b", "B", "정자역 납품처 (샘플)", "경기 성남시 분당구 정자역 인근", 37.3670, 127.1084, 3, "place-jeongja", "middle"),
    ("stop-c", "C", "서현역 납품처 (샘플)", "경기 성남시 분당구 서현역 인근", 37.3850, 127.1233, 4, "place-seohyeon", "front"),
]
_DRIVERS = [("driver-1", "기사 1 (샘플)"), ("driver-2", "기사 2 (샘플)")]
# 박스별 계획 자리 (lane, layer). 실제 적재 기록이 아닌 샘플. 나중에 내릴 C 부터 앞쪽에 싣는다
_SLOTS = {
    "stop-c": [(0, 1), (1, 1), (2, 1), (1, 2)],
    "stop-b": [(0, 1), (2, 1), (0, 2)],
    "stop-a": [(0, 1), (1, 1), (1, 2)],
}
_LOAD_ORDER = ["stop-c", "stop-b", "stop-a"]
_EQUIPMENT = [
    ("equip-cart", "접이식 핸드카트", "적재함 뒷문 오른쪽"),
    ("equip-strap", "화물 고정 끈 4개", "조수석 뒤 수납함"),
    ("equip-toolbox", "공구함", "적재함 앞쪽 왼편"),
]
# 차량 노트 예시 (샘플). 기사가 화면·음성으로 자유롭게 추가한다. 고정 목록이 아니다
_VEHICLE_NOTES = [
    ("seed-note-1", "tool", "접이식 핸드카트", "적재함 뒷문 오른쪽"),
    ("seed-note-2", "issue", "고정 끈 1개 끊어짐", "조수석 뒤 수납함"),
    ("seed-note-3", "other", "뒷문은 세게 닫아야 잠김", None),
]


def ensure_sample(session: Session, reset: bool = False, demo_issue: bool = False) -> bool:
    """샘플 운행이 없으면 만든다. 만들었으면 True. 기사·차량·장비는 항상 맞춰 둔다."""
    now = utcnow()
    for did, name in _DRIVERS:
        session.merge(Driver(id=did, name=name))
    session.merge(Vehicle(id=SAMPLE_VEHICLE_ID, name="샘플 차량"))
    if reset:
        place_keys = [x[7] for x in _STOPS]
        session.execute(delete(Memo).where((Memo.trip_id == SAMPLE_TRIP_ID) | Memo.scope_id.in_(place_keys)
                                           | (Memo.scope_type == "vehicle") | (Memo.scope_type == "equipment")))
        for model in (Handover, Issue, Pickup, TripEvent, Observation, PlacementRequest, CargoUnit, CargoLine, Stop):
            session.execute(delete(model).where(model.trip_id == SAMPLE_TRIP_ID))
        session.execute(delete(Trip).where(Trip.id == SAMPLE_TRIP_ID))
        session.execute(delete(Equipment).where(Equipment.vehicle_id == SAMPLE_VEHICLE_ID))
        session.flush()
    for eid, name, loc in _EQUIPMENT:
        if session.get(Equipment, eid) is None:
            session.add(Equipment(id=eid, vehicle_id=SAMPLE_VEHICLE_ID, name=name, location=loc))
    session.flush()
    has_notes = session.scalars(select(Memo.id).where(Memo.scope_type == "vehicle", Memo.scope_id == SAMPLE_VEHICLE_ID)).first()
    if has_notes is None:
        for key, category, body, location in _VEHICLE_NOTES:
            session.add(Memo(idempotency_key=key, scope_type="vehicle", scope_id=SAMPLE_VEHICLE_ID, body=body, tags=[], visibility="shared",
                             persistence="always", source="screen", category=category, location=location, author_id="shared",
                             created_at=now, updated_at=now, updated_by="shared"))
    if session.get(Trip, SAMPLE_TRIP_ID) is not None:
        session.commit()
        return False
    trip = Trip(id=SAMPLE_TRIP_ID, name="샘플 운행 · A·B·C", data_source="sample", status="planned",
                created_at=now, vehicle_id=SAMPLE_VEHICLE_ID)
    for seq, (sid, label, name, addr, lat, lng, boxes, place_key, zone) in enumerate(_STOPS, start=1):
        st = Stop(id=sid, seq=seq, label=label, name=name, address=addr, latitude=lat, longitude=lng, place_key=place_key)
        st.cargo.append(CargoLine(trip_id=SAMPLE_TRIP_ID, item_name="박스", kind="box", planned_qty=boxes, zone=zone))
        trip.stops.append(st)
    session.add(trip)
    session.flush()
    zone_by_stop = {x[0]: x[8] for x in _STOPS}
    lines = {st.id: st.cargo[0] for st in trip.stops}
    load_seq = 0
    for sid in _LOAD_ORDER:
        for no, (lane, layer) in enumerate(_SLOTS[sid], start=1):
            load_seq += 1
            unit = CargoUnit(trip_id=SAMPLE_TRIP_ID, stop_id=sid, cargo_line_id=lines[sid].id, kind="box", item_name="박스", no=no,
                             load_seq=load_seq, state="planned", planned_zone=zone_by_stop[sid], planned_lane=lane, planned_layer=layer, updated_at=now)
            session.add(unit)
            session.flush()
            # 비전 AI 전까지 이상 표시를 보여 주기 위한 시작 데이터
            if demo_issue and sid == "stop-a" and no == 3:
                session.add(Issue(trip_id=SAMPLE_TRIP_ID, kind="shift", zone=unit.planned_zone, unit_id=unit.id, note="", source="sample", observed_at=now))
    # 공유 현장 메모 예시 (샘플). 실제 기사 경험 기록이 아니다
    session.add(Memo(idempotency_key="seed-memo-1", scope_type="place", scope_id="place-jeongja",
                     body="(샘플) 정문은 오전 혼잡. 하역은 건물 뒤편 출입구가 편함", tags=["진입", "하역"], visibility="shared",
                     persistence="always", source="screen", category="place", author_id="driver-2", created_at=now, updated_at=now, updated_by="driver-2"))
    session.commit()
    return True


def main() -> None:
    from app.core.db import get_engine
    from sqlalchemy.orm import sessionmaker

    with sessionmaker(bind=get_engine())() as s:
        created = ensure_sample(s, reset="--reset" in sys.argv, demo_issue=True)
        print("sample trip created" if created else "sample trip already exists")


if __name__ == "__main__":
    main()
