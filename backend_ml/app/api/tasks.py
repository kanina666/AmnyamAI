from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.security import get_current_user_id
from app.db.models import Meeting, Task, TaskStatus, User
from app.db.schemas import CalendarSyncResponse, TaskCreate, TaskRead, TaskUpdate
from app.db.session import get_db
from app.services.calendar_service import CalendarSyncError, GoogleCalendarService

router = APIRouter(prefix="/tasks", tags=["tasks"])


@router.get("", response_model=list[TaskRead])
async def list_tasks(
    user_id: str = Depends(get_current_user_id),
    db: AsyncSession = Depends(get_db),
) -> list[Task]:
    result = await db.execute(
        select(Task).where(Task.owner_id == user_id).order_by(Task.created_at.desc())
    )
    return list(result.scalars().all())


@router.post("", response_model=TaskRead, status_code=status.HTTP_201_CREATED)
async def create_task(
    payload: TaskCreate,
    user_id: str = Depends(get_current_user_id),
    db: AsyncSession = Depends(get_db),
) -> Task:
    meeting = await _get_owned_meeting(db, payload.meeting_id, user_id)
    task = Task(
        meeting_id=meeting.id,
        owner_id=user_id,
        speaker_tag=payload.speaker_tag,
        title=payload.title,
        description=payload.description,
        due_at=payload.due_at,
    )
    db.add(task)
    await db.commit()
    await db.refresh(task)
    return task


@router.patch("/{task_id}", response_model=TaskRead)
async def update_task(
    task_id: str,
    payload: TaskUpdate,
    user_id: str = Depends(get_current_user_id),
    db: AsyncSession = Depends(get_db),
) -> Task:
    task = await _get_owned_task(db, task_id, user_id)
    updates = payload.model_dump(exclude_unset=True)
    for key, value in updates.items():
        setattr(task, key, value)
    await db.commit()
    await db.refresh(task)
    return task


@router.post("/{task_id}/confirm", response_model=TaskRead)
async def confirm_task(
    task_id: str,
    user_id: str = Depends(get_current_user_id),
    db: AsyncSession = Depends(get_db),
) -> Task:
    task = await _get_owned_task(db, task_id, user_id)
    task.status = TaskStatus.CONFIRMED
    await db.commit()
    await db.refresh(task)
    return task


@router.post("/{task_id}/sync-calendar", response_model=CalendarSyncResponse)
async def sync_task_to_calendar(
    task_id: str,
    user_id: str = Depends(get_current_user_id),
    db: AsyncSession = Depends(get_db),
) -> CalendarSyncResponse:
    task = await _get_owned_task(db, task_id, user_id)
    user = await db.get(User, user_id)
    if user is None:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="User not found",
        )

    try:
        event_id = await GoogleCalendarService().create_event_for_task(user, task)
    except CalendarSyncError as exc:
        # Make this a client-visible error instead of a silent 500.
        raise HTTPException(
            status_code=status.HTTP_409_CONFLICT,
            detail=str(exc),
        ) from exc
    task.calendar_event_id = event_id
    task.status = TaskStatus.SYNCED
    await db.commit()
    return CalendarSyncResponse(
        task_id=task.id,
        calendar_event_id=event_id,
        status=task.status,
    )


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


async def _get_owned_task(db: AsyncSession, task_id: str, user_id: str) -> Task:
    result = await db.execute(
        select(Task).where(Task.id == task_id, Task.owner_id == user_id)
    )
    task = result.scalar_one_or_none()
    if task is None:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="Task not found",
        )
    return task
