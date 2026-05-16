from datetime import datetime
from typing import Any

from pydantic import BaseModel, ConfigDict, Field

from app.db.models import MeetingStatus, TaskStatus


class UserRead(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    id: str
    email: str
    name: str | None = None


class AuthUrlResponse(BaseModel):
    url: str
    state: str


class TokenResponse(BaseModel):
    access_token: str
    token_type: str = "bearer"
    user: UserRead


class MeetingCreate(BaseModel):
    title: str = Field(min_length=1, max_length=255)


class MeetingRead(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    id: str
    title: str
    status: MeetingStatus
    summary: str | None = None
    started_at: datetime | None = None
    ended_at: datetime | None = None
    created_at: datetime


class TranscriptSegmentCreate(BaseModel):
    speaker_tag: str = "unknown"
    text: str
    is_final: bool = True
    start_ms: int | None = None
    end_ms: int | None = None


class TranscriptSegmentRead(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    id: str
    meeting_id: str
    sequence: int
    speaker_tag: str
    text: str
    is_final: bool
    start_ms: int | None = None
    end_ms: int | None = None
    created_at: datetime


class TaskBase(BaseModel):
    speaker_tag: str = "unknown"
    title: str = Field(min_length=1, max_length=255)
    description: str | None = None
    due_at: datetime | None = None


class TaskCreate(TaskBase):
    meeting_id: str


class TaskUpdate(BaseModel):
    title: str | None = Field(default=None, min_length=1, max_length=255)
    description: str | None = None
    due_at: datetime | None = None
    status: TaskStatus | None = None


class TaskRead(TaskBase):
    model_config = ConfigDict(from_attributes=True)

    id: str
    meeting_id: str
    status: TaskStatus
    calendar_event_id: str | None = None
    created_at: datetime


class ExtractedTask(BaseModel):
    speaker_tag: str = "unknown"
    title: str
    description: str | None = None
    due_at: datetime | None = None
    confidence: float = Field(ge=0.0, le=1.0, default=0.7)


class MeetingAnalysis(BaseModel):
    summary: str
    tasks: list[ExtractedTask] = Field(default_factory=list)
    raw: dict[str, Any] | None = None


class CalendarSyncResponse(BaseModel):
    task_id: str
    calendar_event_id: str
    status: TaskStatus

