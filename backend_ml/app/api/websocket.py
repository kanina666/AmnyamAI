import asyncio
from collections.abc import AsyncIterator
from datetime import UTC, datetime
import logging

from fastapi import APIRouter, WebSocket, WebSocketDisconnect, status
from sqlalchemy import func, select
from starlette.websockets import WebSocketState

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
logger = logging.getLogger(__name__)


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
    websocket_closed = asyncio.Event()
    recognizer_task = asyncio.create_task(
        _recognize_and_emit(
            websocket,
            canonical_meeting_id,
            audio_queue,
            websocket_closed,
        )
    )

    try:
        while True:
            chunk = await websocket.receive_bytes()
            validate_pcm16_mono_chunk(chunk)
            await audio_queue.put(chunk)
    except WebSocketDisconnect:
        websocket_closed.set()
    except ValueError as exc:
        websocket_closed.set()
        logger.warning(
            "Closing audio websocket because an invalid audio chunk was received: "
            "meeting_id=%s error=%s",
            canonical_meeting_id,
            exc,
        )
        await _close_websocket(websocket, code=1003, reason="invalid audio chunk")
    finally:
        websocket_closed.set()
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
    websocket_closed: asyncio.Event,
) -> None:
    seen_results = 0
    seen_final_results = 0
    try:
        client = SpeechKitStreamingClient()
        async for result in client.stream_pcm16(_audio_iterator(audio_queue)):
            seen_results += 1
            logger.debug(
                "SpeechKit transcript event: meeting_id=%s event_type=%s "
                "is_final=%s text_len=%s",
                meeting_id,
                result.event_type,
                result.is_final,
                len(result.text),
            )
            if result.is_final:
                seen_final_results += 1
                await _store_transcript_segment(meeting_id, result)

            if websocket_closed.is_set():
                continue

            sent = await _send_transcript_event(websocket, meeting_id, result)
            if not sent:
                websocket_closed.set()
    except Exception:
        logger.exception("SpeechKit streaming failed: meeting_id=%s", meeting_id)
        if not websocket_closed.is_set():
            sent = await _send_websocket_json(
                websocket,
                {
                    "type": "error",
                    "meeting_id": meeting_id,
                    "message": "Speech recognition failed",
                },
            )
            if not sent:
                websocket_closed.set()
        websocket_closed.set()
        await _close_websocket(
            websocket,
            code=1011,
            reason="speech recognition failed",
        )
    finally:
        if seen_results == 0:
            logger.warning(
                "SpeechKit produced no transcript events: meeting_id=%s",
                meeting_id,
            )
        elif seen_final_results == 0:
            logger.warning(
                "SpeechKit produced no final transcript events: meeting_id=%s",
                meeting_id,
            )


async def _send_transcript_event(
    websocket: WebSocket,
    meeting_id: str,
    result: STTResult,
) -> bool:
    return await _send_websocket_json(
        websocket,
        {
            "type": "transcript",
            "meeting_id": meeting_id,
            "speaker_tag": result.speaker_tag,
            "text": result.text,
            "is_final": result.is_final,
            "event_type": result.event_type,
        },
    )


async def _send_websocket_json(websocket: WebSocket, payload: dict) -> bool:
    if websocket.application_state != WebSocketState.CONNECTED:
        return False
    try:
        await websocket.send_json(payload)
    except RuntimeError as exc:
        logger.info("Skipping websocket send because connection is closed: %s", exc)
        return False
    return True


async def _close_websocket(websocket: WebSocket, *, code: int, reason: str) -> None:
    if websocket.application_state == WebSocketState.DISCONNECTED:
        return
    try:
        await websocket.close(code=code, reason=reason)
    except RuntimeError as exc:
        logger.info("Skipping websocket close because connection is closed: %s", exc)


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

