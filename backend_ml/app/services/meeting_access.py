import re

from sqlalchemy import String, cast, func, select
from sqlalchemy.ext.asyncio import AsyncSession

from app.db.models import Meeting, MeetingParticipant

_UUID_HEX_RE = re.compile(r"^[0-9A-F]+$")


def normalize_meeting_identifier(identifier: str) -> str:
    return identifier.strip().upper().replace("-", "")


async def find_meeting_by_identifier(
    db: AsyncSession,
    identifier: str,
) -> tuple[Meeting | None, bool]:
    normalized = normalize_meeting_identifier(identifier)
    if len(normalized) < 4 or _UUID_HEX_RE.fullmatch(normalized) is None:
        return None, False

    normalized_id = func.replace(func.upper(cast(Meeting.id, String)), "-", "")
    result = await db.execute(
        select(Meeting)
        .where(normalized_id.like(f"{normalized}%"))
        .order_by(Meeting.created_at.desc())
        .limit(2)
    )
    meetings = list(result.scalars().all())
    if not meetings:
        return None, False
    return meetings[0], len(meetings) > 1


async def ensure_meeting_participant(
    db: AsyncSession,
    meeting: Meeting,
    user_id: str,
) -> None:
    if meeting.owner_id == user_id:
        return

    result = await db.execute(
        select(MeetingParticipant).where(
            MeetingParticipant.meeting_id == meeting.id,
            MeetingParticipant.user_id == user_id,
        )
    )
    if result.scalar_one_or_none() is not None:
        return

    db.add(MeetingParticipant(meeting_id=meeting.id, user_id=user_id))


async def get_accessible_meeting(
    db: AsyncSession,
    meeting_id: str,
    user_id: str,
) -> Meeting | None:
    meeting = await db.get(Meeting, meeting_id)
    if meeting is None:
        return None
    if meeting.owner_id == user_id:
        return meeting

    result = await db.execute(
        select(MeetingParticipant.id).where(
            MeetingParticipant.meeting_id == meeting.id,
            MeetingParticipant.user_id == user_id,
        )
    )
    if result.scalar_one_or_none() is None:
        return None
    return meeting
