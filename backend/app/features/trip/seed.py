"""샘플 운행 데이터 (기획서 14장: 목적지 A·B·C, 박스 10개 = A3·B3·C4).

실제 배송 데이터가 아니다. data_source="sample" 로 저장되고 앱은 "샘플"로 표시한다 (원칙 5).
좌표는 에뮬레이터 내비에서 경로 생성이 확인된 공공장소(역) 근처이며 실제 납품처가 아니다.

    python -m app.features.trip.seed           # 없으면 생성
    python -m app.features.trip.seed --reset   # 샘플 운행을 지우고 다시 생성 (시연 반복용)
"""

from __future__ import annotations

import sys
from datetime import datetime, timezone

from sqlalchemy import delete
from sqlalchemy.orm import Session

from app.features.trip.models import CargoLine, Observation, Stop, Trip, TripEvent

SAMPLE_TRIP_ID = "sample-trip-1"

_STOPS = [
    # id, label, name, address, lat, lng, boxes
    ("stop-a", "A", "판교역 납품처 (샘플)", "경기 성남시 분당구 판교역 인근", 37.3947, 127.1112, 3),
    ("stop-b", "B", "정자역 납품처 (샘플)", "경기 성남시 분당구 정자역 인근", 37.3670, 127.1084, 3),
    ("stop-c", "C", "서현역 납품처 (샘플)", "경기 성남시 분당구 서현역 인근", 37.3850, 127.1233, 4),
]


def ensure_sample(session: Session, reset: bool = False) -> bool:
    """샘플 운행이 없으면 만든다. 만들었으면 True."""
    if reset:
        for model in (TripEvent, Observation, CargoLine, Stop):
            session.execute(delete(model).where(model.trip_id == SAMPLE_TRIP_ID))
        session.execute(delete(Trip).where(Trip.id == SAMPLE_TRIP_ID))
        session.flush()
    elif session.get(Trip, SAMPLE_TRIP_ID) is not None:
        return False
    trip = Trip(id=SAMPLE_TRIP_ID, name="샘플 운행 · A·B·C", data_source="sample", status="planned",
                created_at=datetime.now(timezone.utc))
    for seq, (sid, label, name, addr, lat, lng, boxes) in enumerate(_STOPS, start=1):
        st = Stop(id=sid, seq=seq, label=label, name=name, address=addr, latitude=lat, longitude=lng)
        st.cargo.append(CargoLine(trip_id=SAMPLE_TRIP_ID, item_name="박스", kind="box", planned_qty=boxes))
        trip.stops.append(st)
    session.add(trip)
    session.commit()
    return True


def main() -> None:
    from app.core.db import get_engine
    from sqlalchemy.orm import sessionmaker

    with sessionmaker(bind=get_engine())() as s:
        created = ensure_sample(s, reset="--reset" in sys.argv)
        print("sample trip created" if created else "sample trip already exists")


if __name__ == "__main__":
    main()
