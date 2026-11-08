from pydantic import BaseModel, Field


class HandoverIn(BaseModel):
    idempotency_key: str = Field(min_length=8, max_length=64)
    to_driver_id: str
    note: str = Field(default="", max_length=1000)


