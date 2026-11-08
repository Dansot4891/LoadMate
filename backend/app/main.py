from fastapi import FastAPI

from app.core.config import get_settings
from app.features.health.router import router as health_router


def create_app() -> FastAPI:
    app = FastAPI(title="Loadmate API", version=get_settings().app_version)
    # Feature 라우터는 여기서만 등록한다 (features/<name>/router.py)
    app.include_router(health_router)
    return app


app = create_app()
