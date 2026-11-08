import pytest
from fastapi.testclient import TestClient
from sqlalchemy import create_engine
from sqlalchemy.orm import sessionmaker
from sqlalchemy.pool import StaticPool

import app.features.fleet.models  # noqa: F401  테이블 등록
import app.features.handover.models  # noqa: F401
import app.features.memo.models  # noqa: F401
import app.features.trip.models  # noqa: F401
from app.core.db import Base, get_session
from app.features.trip.seed import ensure_sample
from app.main import create_app


@pytest.fixture
def session_factory():
    engine = create_engine("sqlite://", connect_args={"check_same_thread": False}, poolclass=StaticPool)
    Base.metadata.create_all(engine)
    factory = sessionmaker(bind=engine, autoflush=False, expire_on_commit=False)
    with factory() as s:
        ensure_sample(s)
    return factory


@pytest.fixture
def client(session_factory):
    app = create_app()

    def _session():
        with session_factory() as s:
            yield s

    app.dependency_overrides[get_session] = _session
    return TestClient(app)
