from datetime import UTC, datetime

from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.security import get_current_user_id
from app.db.models import Meeting, MeetingStatus, Task, TranscriptSegment
from app.db.schemas import (
    MeetingCreate,
    MeetingRead,
    TaskRead,
    TranscriptSegmentRead,
)
from app.db.session import get_db
from app.services.meeting_analysis import MeetingAnalysisService

router = APIRouter(prefix="/meetings", tags=["meetings"])


@router.post("", response_model=MeetingRead, status_code=status.HTTP_201_CREATED)
async def create_meeting(
    payload: MeetingCreate,
    user_id: str = Depends(get_current_user_id),
    db: AsyncSession = Depends(get_db),
) -> Meeting:
    meeting = Meeting(owner_id=user_id, title=payload.title)
    db.add(meeting)
    await db.commit()
    await db.refresh(meeting)
    return meeting


@router.get("", response_model=list[MeetingRead])
async def list_meetings(
    user_id: str = Depends(get_current_user_id),
    db: AsyncSession = Depends(get_db),
) -> list[Meeting]:
    result = await db.execute(
        select(Meeting)
        .where(Meeting.owner_id == user_id)
        .order_by(Meeting.created_at.desc())
    )
    return list(result.scalars().all())


@router.get("/{meeting_id}", response_model=MeetingRead)
async def get_meeting(
    meeting_id: str,
    user_id: str = Depends(get_current_user_id),
    db: AsyncSession = Depends(get_db),
) -> Meeting:
    return await _get_owned_meeting(db, meeting_id, user_id)


@router.get("/{meeting_id}/transcript", response_model=list[TranscriptSegmentRead])
async def get_transcript(
    meeting_id: str,
    user_id: str = Depends(get_current_user_id),
    db: AsyncSession = Depends(get_db),
) -> list[TranscriptSegment]:
    await _get_owned_meeting(db, meeting_id, user_id)
    result = await db.execute(
        select(TranscriptSegment)
        .where(TranscriptSegment.meeting_id == meeting_id)
        .order_by(TranscriptSegment.sequence)
    )
    return list(result.scalars().all())


@router.post("/{meeting_id}/finish", response_model=list[TaskRead])
async def finish_meeting(
    meeting_id: str,
    user_id: str = Depends(get_current_user_id),
    db: AsyncSession = Depends(get_db),
) -> list[Task]:
    meeting = await _get_owned_meeting(db, meeting_id, user_id)
    meeting.status = MeetingStatus.PROCESSING
    meeting.ended_at = datetime.now(UTC)
    await db.commit()

    service = MeetingAnalysisService()
    await service.analyze_and_store(db, meeting)

    meeting.status = MeetingStatus.COMPLETED
    await db.commit()

    result = await db.execute(
        select(Task).where(Task.meeting_id == meeting_id).order_by(Task.created_at)
    )
    return list(result.scalars().all())


async def _get_owned_meeting(
    db: AsyncSession,
    meeting_id: str,
    user_id: str,
) -> Meeting:
    result = await db.execute(
        select(Meeting).where(Meeting.id == meeting_id, Meeting.owner_id == user_id)
    )
    meeting = result.scalar_one_or_none()
    if meeting is None:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="Meeting not found",
        )
    return meeting

