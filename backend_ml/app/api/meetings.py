from datetime import UTC, datetime

from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy import or_, select
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.security import get_current_user_id
from app.db.models import (
    Meeting,
    MeetingParticipant,
    MeetingStatus,
    Task,
    TranscriptSegment,
)
from app.db.schemas import (
    MeetingCreate,
    MeetingRead,
    TaskRead,
    TranscriptSegmentRead,
)
from app.db.session import get_db
from app.services.meeting_access import (
    ensure_meeting_participant,
    find_meeting_by_identifier,
    get_accessible_meeting,
)
from app.services.gpt_service import GPTAnalysisError
from app.services.meeting_analysis import EmptyTranscriptError, MeetingAnalysisService

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
        .outerjoin(MeetingParticipant, MeetingParticipant.meeting_id == Meeting.id)
        .where(
            or_(
                Meeting.owner_id == user_id,
                MeetingParticipant.user_id == user_id,
            )
        )
        .distinct()
        .order_by(Meeting.created_at.desc())
    )
    return list(result.scalars().all())


@router.post("/join/{identifier}", response_model=MeetingRead)
async def join_meeting(
    identifier: str,
    user_id: str = Depends(get_current_user_id),
    db: AsyncSession = Depends(get_db),
) -> Meeting:
    meeting, is_ambiguous = await find_meeting_by_identifier(db, identifier)
    if is_ambiguous:
        raise HTTPException(
            status_code=status.HTTP_409_CONFLICT,
            detail="Meeting identifier is ambiguous",
        )
    if meeting is None:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="Meeting not found",
        )

    await ensure_meeting_participant(db, meeting, user_id)
    await db.commit()
    await db.refresh(meeting)
    return meeting


@router.get("/{meeting_id}", response_model=MeetingRead)
async def get_meeting(
    meeting_id: str,
    user_id: str = Depends(get_current_user_id),
    db: AsyncSession = Depends(get_db),
) -> Meeting:
    return await _get_accessible_meeting(db, meeting_id, user_id)


@router.get("/{meeting_id}/transcript", response_model=list[TranscriptSegmentRead])
async def get_transcript(
    meeting_id: str,
    user_id: str = Depends(get_current_user_id),
    db: AsyncSession = Depends(get_db),
) -> list[TranscriptSegment]:
    await _get_accessible_meeting(db, meeting_id, user_id)
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
    try:
        await service.analyze_and_store(db, meeting)
    except EmptyTranscriptError as exc:
        meeting.status = MeetingStatus.FAILED
        await db.commit()
        raise HTTPException(
            status_code=status.HTTP_422_UNPROCESSABLE_ENTITY,
            detail=str(exc),
        ) from exc
    except GPTAnalysisError as exc:
        meeting.status = MeetingStatus.FAILED
        await db.commit()
        raise HTTPException(
            status_code=status.HTTP_502_BAD_GATEWAY,
            detail=str(exc),
        ) from exc

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


async def _get_accessible_meeting(
    db: AsyncSession,
    meeting_id: str,
    user_id: str,
) -> Meeting:
    meeting = await get_accessible_meeting(db, meeting_id, user_id)
    if meeting is None:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="Meeting not found",
        )
    return meeting

