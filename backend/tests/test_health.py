from fastapi.testclient import TestClient

from app.features.health import router as health_module
from app.main import create_app


def test_health_ok(monkeypatch):
    monkeypatch.setattr(health_module, "ping_database", lambda: True)
    res = TestClient(create_app()).get("/health")
    assert res.status_code == 200
    assert res.json() == {"status": "ok", "version": "0.1.0", "database": "ok"}


def test_health_db_down_is_degraded_not_error(monkeypatch):
    monkeypatch.setattr(health_module, "ping_database", lambda: False)
    res = TestClient(create_app()).get("/health")
    assert res.status_code == 200
    assert res.json()["database"] == "unavailable"
    assert res.json()["status"] == "degraded"
