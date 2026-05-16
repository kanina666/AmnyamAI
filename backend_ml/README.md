# MeetingAgent Backend

FastAPI backend scaffold for the MeetingAgent hackathon project.

## What is included

- Google OAuth callback and JWT issuing.
- Async SQLAlchemy models for users, meetings, transcript segments, and tasks.
- WebSocket endpoint for PCM16 16 kHz audio streaming.
- Yandex SpeechKit gRPC v3 client wrapper with speaker label normalization.
- YandexGPT analysis service with Pydantic JSON validation.
- Google Calendar event creation for confirmed tasks.
- Docker and docker-compose setup with PostgreSQL.

## Local run

```bash
cd backend_ml
cp .env.example .env
docker compose up --build
```

Healthcheck:

```bash
curl http://localhost:8000/health
```

API docs:

```text
http://localhost:8000/docs
```

## Android audio stream

Join an existing meeting by full UUID or the short 8-character code:

```text
POST /api/v1/meetings/join/{identifier}
Authorization: Bearer {jwt}
```

Open:

```text
ws://localhost:8000/ws/meetings/{meeting_id_or_code}/audio?token={jwt}
```

Send raw PCM bytes:

- Linear PCM, signed 16-bit little-endian.
- Mono.
- 16 kHz.

The backend sends live transcript events:

```json
{
  "type": "transcript",
  "meeting_id": "...",
  "speaker_tag": "speaker_0",
  "text": "...",
  "is_final": true,
  "event_type": "final"
}
```

## Notes

Yandex SpeechKit API v3 examples use generated protobuf modules under
`yandex.cloud.ai.stt.v3`. If the installed package does not provide these
modules in your environment, generate them from the official `cloudapi`
repository and make them importable in the container.

Speaker labeling in current Yandex SpeechKit API v3 responses is exposed as
`channel_tag`. The backend maps it to the provider-neutral `speaker_tag` field
used by Android, DB records, and GPT prompts.

