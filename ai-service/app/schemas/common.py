from typing import Any, Literal

from pydantic import BaseModel, ConfigDict, Field


class InternalTestRequest(BaseModel):
    model_config = ConfigDict(populate_by_name=True)

    request_id: str = Field(
        alias="requestId",
        min_length=1,
        max_length=100,
    )

    message: str = Field(
        min_length=1,
        max_length=500,
    )


class InternalResponse(BaseModel):
    model_config = ConfigDict(populate_by_name=True)

    request_id: str = Field(alias="requestId")
    status: Literal["SUCCESS", "FAILED"]
    data: Any | None = None