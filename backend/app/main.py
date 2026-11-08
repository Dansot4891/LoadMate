from fastapi import FastAPI

from app.core.config import get_settings
from app.core.errors import install_error_handlers
from app.features.health.router import router as health_router
from app.features.assistant.router import router as assistant_router
from app.features.fleet.router import router as fleet_router
from app.features.handover.router import router as handover_router
from app.features.memo.router import router as memo_router
from app.features.trip.router import router as trip_router


def create_app() -> FastAPI:
    app = FastAPI(title="Loadmate API", version=get_settings().app_version)
    # Feature 라우터는 여기서만 등록한다 (features/<name>/router.py)
    install_error_handlers(app)
    app.include_router(health_router)
    app.include_router(trip_router)
    app.include_router(fleet_router)
    app.include_router(memo_router)
    app.include_router(handover_router)
    app.include_router(assistant_router)
    return app


app = create_app()
