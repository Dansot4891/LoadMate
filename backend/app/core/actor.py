from fastapi import Header


def actor_id(x_driver_id: str | None = Header(default=None)) -> str | None:
    """현재 기사. 인증이 아닌 사용자 선택 값(F56). 개인 메모 열람 범위와 수정 이력 작성자에 쓴다."""
    return x_driver_id or None
