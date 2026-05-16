import asyncio
from collections.abc import AsyncIterator
from datetime import UTC, datetime

from fastapi import APIRouter, WebSocket, WebSocketDisconnect, status
from sqlalchemy import func, select

from app.core.security import decode_access_token
from app.db.models import Meeting, MeetingStatus, TranscriptSegment
from app.db.session import AsyncSessionLocal
from app.services.meeting_access import (
    ensure_meeting_participant,
    find_meeting_by_identifier,
)
from app.services.stt_service import STTResult, SpeechKitStreamingClient
from app.utils.audio_utils import validate_pcm16_mono_chunk

router = APIRouter(tags=["websocket"])


@router.websocket("/ws/meetings/{meeting_id}/audio")
async def stream_meeting_audio(websocket: WebSocket, meeting_id: str) -> None:
    token = websocket.query_params.get("token")
    if not token:
        await websocket.close(code=status.WS_1008_POLICY_VIOLATION)
        return

    try:
        user_id = decode_access_token(token)
    except Exception:
        await websocket.close(code=status.WS_1008_POLICY_VIOLATION)
        return

    canonical_meeting_id = meeting_id
    async with AsyncSessionLocal() as db:
        meeting, is_ambiguous = await find_meeting_by_identifier(db, meeting_id)
        if meeting is None or is_ambiguous:
            await websocket.close(code=status.WS_1008_POLICY_VIOLATION)
            return

        canonical_meeting_id = meeting.id
        await ensure_meeting_participant(db, meeting, user_id)
        meeting.status = MeetingStatus.RECORDING
        meeting.started_at = meeting.started_at or datetime.now(UTC)
        await db.commit()

    await websocket.accept()

    audio_queue: asyncio.Queue[bytes | None] = asyncio.Queue(maxsize=64)
    recognizer_task = asyncio.create_task(
        _recognize_and_emit(websocket, canonical_meeting_id, audio_queue)
    )

    try:
        while True:
            chunk = await websocket.receive_bytes()
            validate_pcm16_mono_chunk(chunk)
            await audio_queue.put(chunk)
    except WebSocketDisconnect:
        pass
    finally:
        await audio_queue.put(None)
        await recognizer_task
        async with AsyncSessionLocal() as db:
            meeting = await db.get(Meeting, canonical_meeting_id)
            if meeting and meeting.status == MeetingStatus.RECORDING:
                meeting.status = MeetingStatus.PROCESSING
                meeting.ended_at = datetime.now(UTC)
                await db.commit()


async def _audio_iterator(
    audio_queue: asyncio.Queue[bytes | None],
) -> AsyncIterator[bytes]:
    while True:
        chunk = await audio_queue.get()
        if chunk is None:
            break
        yield chunk


async def _recognize_and_emit(
    websocket: WebSocket,
    meeting_id: str,
    audio_queue: asyncio.Queue[bytes | None],
) -> None:
    client = SpeechKitStreamingClient()
    async for result in client.stream_pcm16(_audio_iterator(audio_queue)):
        if result.is_final:
            await _store_transcript_segment(meeting_id, result)
        await websocket.send_json(
            {
                "type": "transcript",
                "meeting_id": meeting_id,
                "speaker_tag": result.speaker_tag,
                "text": result.text,
                "is_final": result.is_final,
                "event_type": result.event_type,
            }
        )


async def _store_transcript_segment(meeting_id: str, result: STTResult) -> None:
    async with AsyncSessionLocal() as db:
        sequence_result = await db.execute(
            select(func.count(TranscriptSegment.id)).where(
                TranscriptSegment.meeting_id == meeting_id
            )
        )
        sequence = int(sequence_result.scalar_one())
        db.add(
            TranscriptSegment(
                meeting_id=meeting_id,
                sequence=sequence,
                speaker_tag=result.speaker_tag,
                text=result.text,
                is_final=result.is_final,
                start_ms=result.start_ms,
                end_ms=result.end_ms,
            )
        )
        await db.commit()

