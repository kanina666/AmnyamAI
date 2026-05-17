import logging

from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.db.models import Meeting, Task, TranscriptSegment
from app.db.schemas import MeetingAnalysis
from app.services.gpt_service import GPTAnalysisError, YandexGPTService

logger = logging.getLogger(__name__)


class EmptyTranscriptError(RuntimeError):
    pass


class MeetingAnalysisService:
    def __init__(self, gpt_service: YandexGPTService | None = None) -> None:
        self.gpt_service = gpt_service or YandexGPTService()

    async def analyze_and_store(
        self,
        db: AsyncSession,
        meeting: Meeting,
    ) -> MeetingAnalysis:
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

