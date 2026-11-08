from fastapi import FastAPI, Request
from fastapi.responses import JSONResponse


class AppError(Exception):
    """업무 오류. 응답 형식: {"detail": {"code": ..., "message": ...}} (docs/api-contract.md 공통 규칙)."""

    def __init__(self, status_code: int, code: str, message: str):
        super().__init__(message)
        self.status_code = status_code
        self.code = code
        self.message = message


def install_error_handlers(app: FastAPI) -> None:
    @app.exception_handler(AppError)
    async def _handle(_: Request, exc: AppError) -> JSONResponse:
        return JSONResponse(status_code=exc.status_code, content={"detail": {"code": exc.code, "message": exc.message}})
