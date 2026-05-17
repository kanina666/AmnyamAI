import asyncio
from datetime import UTC, datetime, timedelta

from google.auth.exceptions import RefreshError
from google.oauth2.credentials import Credentials
from googleapiclient.errors import HttpError
from googleapiclient.discovery import build

from app.core.config import settings
from app.db.models import Task, User


class CalendarSyncError(RuntimeError):
    pass


class GoogleCalendarService:
    async def create_event_for_task(self, user: User, task: Task) -> str:
        if not user.google_refresh_token:
            raise CalendarSyncError("User has no Google refresh token")
        return await asyncio.to_thread(self._create_event_sync, user, task)

    def _create_event_sync(self, user: User, task: Task) -> str:
        if not settings.google_client_id or not settings.google_client_secret:
            raise CalendarSyncError("Google OAuth client is not configured")

        credentials = Credentials(
            token=None,
            refresh_token=user.google_refresh_token,
            token_uri="https://oauth2.googleapis.com/token",
            client_id=settings.google_client_id,
            client_secret=settings.google_client_secret,
        )
        service = build("calendar", "v3", credentials=credentials, cache_discovery=False)

        start = task.due_at or datetime.now(UTC) + timedelta(days=1)
        if start.tzinfo is None:
            start = start.replace(tzinfo=UTC)
        end = start + timedelta(minutes=30)

        event = {
            "summary": task.title,
            "description": task.description or "",
            "start": {"dateTime": start.isoformat()},
            "end": {"dateTime": end.isoformat()},
        }
        try:
            created = service.events().insert(calendarId="primary", body=event).execute()
        except RefreshError as exc:
            raise CalendarSyncError(
                "Google Calendar authorization is invalid. Re-login and grant Calendar access."
            ) from exc
        except HttpError as exc:
            raise CalendarSyncError(
                "Google Calendar permission is missing. Re-login and grant Calendar access."
            ) from exc
        event_id = created.get("id")
        if not event_id:
            raise CalendarSyncError("Google Calendar returned event without id")
        return event_id
