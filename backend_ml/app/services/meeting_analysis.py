from datetime import datetime, timedelta, timezone
import logging

from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.config import settings
from app.db.models import Meeting, Task, TranscriptSegment
from app.db.schemas import MeetingAnalysis
from app.services.gpt_service import GPTAnalysisError, YandexGPTService

logger = logging.getLogger(__name__)


class EmptyTranscriptError(RuntimeError):
    pass


class MeetingAnalysisService:
    def __init__(self, gpt_service: YandexGPTService | None = None) -> None:
        # Lazy-init because demo mode should work without external credentials.
        self.gpt_service = gpt_service

    async def analyze_and_store(
        self,
        db: AsyncSession,
        meeting: Meeting,
    ) -> MeetingAnalysis:
        if settings.demo_mode:
            return await self._store_demo_analysis(db, meeting)

        if self.gpt_service is None:
            self.gpt_service = YandexGPTService()

        transcript = await self._load_transcript(db, meeting.id)
        if not transcript.strip():
            logger.warning(
                "Skipping meeting analysis because no final transcript was "
                "recognized: meeting_id=%s",
                meeting.id,
            )
            raise EmptyTranscriptError(
                "SpeechKit did not recognize any speech for this meeting"
            )

        analysis = await self.gpt_service.analyze_meeting(transcript)
        summary = analysis.summary.strip()
        if not summary:
            logger.warning("YandexGPT returned blank summary: meeting_id=%s", meeting.id)
            raise GPTAnalysisError("YandexGPT returned empty summary")

        analysis.summary = summary
        meeting.summary = summary
        for extracted in analysis.tasks:
            db.add(
                Task(
                    meeting_id=meeting.id,
                    owner_id=meeting.owner_id,
                    speaker_tag=extracted.speaker_tag,
                    title=extracted.title,
                    description=extracted.description,
                    due_at=extracted.due_at,
                    raw_ai_payload=extracted.model_dump(mode="json"),
                )
            )
        await db.commit()
        await db.refresh(meeting)
        return analysis

    @staticmethod
    async def _store_demo_analysis(db: AsyncSession, meeting: Meeting) -> MeetingAnalysis:
        # Deterministic stub tasks for demos.
        summary = (
            "Согласовали развертывание демо-инфраструктуры и распределили ответственность. "
            "Катя развернет сервер до 17 мая 19:00 (МСК), Ксюша настроит базу данных до 22 мая 21:00 (МСК). "
            "После выполнения задач можно переходить к интеграционному тестированию."
        )
        meeting.summary = summary
        tz = timezone(timedelta(hours=3))

        tasks = [
            Task(
                meeting_id=meeting.id,
                owner_id=meeting.owner_id,
                speaker_tag="Катя",
                title="развернуть сервер",
                description=None,
                due_at=datetime(2026, 5, 17, 19, 0, 0, tzinfo=tz),
                raw_ai_payload={"demo": True},
            ),
            Task(
                meeting_id=meeting.id,
                owner_id=meeting.owner_id,
                speaker_tag="Ксюша",
                title="настроить базу данных",
                description=None,
                due_at=datetime(2026, 5, 22, 21, 0, 0, tzinfo=tz),
                raw_ai_payload={"demo": True},
            ),
        ]
        for task in tasks:
            db.add(task)
        await db.commit()
        await db.refresh(meeting)
        return MeetingAnalysis(summary=summary, tasks=[], raw={"demo": True})

    @staticmethod
    async def _load_transcript(db: AsyncSession, meeting_id: str) -> str:
        result = await db.execute(
            select(TranscriptSegment)
            .where(
                TranscriptSegment.meeting_id == meeting_id,
                TranscriptSegment.is_final.is_(True),
            )
            .order_by(TranscriptSegment.sequence)
        )
        segments = result.scalars().all()
        lines = []
        for segment in segments:
            text = segment.text.strip()
            if text:
                lines.append(f"[{segment.speaker_tag}] {text}")
        return "\n".join(lines)

