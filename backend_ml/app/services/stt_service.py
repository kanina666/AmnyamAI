from collections.abc import AsyncIterator
from dataclasses import dataclass
from typing import Any

import grpc

from app.core.config import settings


@dataclass(slots=True)
class STTResult:
    text: str
    speaker_tag: str
    is_final: bool
    event_type: str
    start_ms: int | None = None
    end_ms: int | None = None


class SpeechKitStreamingClient:
    def __init__(self) -> None:
        if not settings.yandex_auth_token:
            raise RuntimeError("Yandex IAM token or API key is not configured")

    async def stream_pcm16(
        self,
        audio_chunks: AsyncIterator[bytes],
    ) -> AsyncIterator[STTResult]:
        stt_pb2, stt_service_pb2_grpc = self._import_stt_modules()

        async def request_iterator() -> AsyncIterator[Any]:
            yield stt_pb2.StreamingRequest(
                session_options=self._build_streaming_options(stt_pb2)
            )
            async for chunk in audio_chunks:
                yield stt_pb2.StreamingRequest(
                    chunk=stt_pb2.AudioChunk(data=chunk)
                )

        credentials = grpc.ssl_channel_credentials()
        channel = grpc.aio.secure_channel(
            settings.speechkit_endpoint,
            credentials,
        )
        metadata = self._build_metadata()

        async with channel:
            stub = stt_service_pb2_grpc.RecognizerStub(channel)
            responses = stub.RecognizeStreaming(
                request_iterator(),
                metadata=metadata,
            )
            async for response in responses:
                result = self._parse_response(response)
                if result is not None:
                    yield result

    @staticmethod
    def _import_stt_modules() -> tuple[Any, Any]:
        try:
            from yandex.cloud.ai.stt.v3 import stt_pb2, stt_service_pb2_grpc
        except ImportError as exc:
            raise RuntimeError(
                "SpeechKit protobuf modules are unavailable. Install yandexcloud "
                "or generate yandex.cloud.ai.stt.v3 modules from cloudapi."
            ) from exc
        return stt_pb2, stt_service_pb2_grpc

    @staticmethod
    def _build_streaming_options(stt_pb2: Any) -> Any:
        processing_type = getattr(
            stt_pb2.RecognitionModelOptions,
            settings.speechkit_processing_type,
        )
        return stt_pb2.StreamingOptions(
            speaker_labeling=stt_pb2.SpeakerLabelingOptions(
                speaker_labeling=(
                    stt_pb2.SpeakerLabelingOptions.SPEAKER_LABELING_ENABLED
                )
            ),
            recognition_model=stt_pb2.RecognitionModelOptions(
                model=settings.speechkit_model,
                audio_format=stt_pb2.AudioFormatOptions(
                    raw_audio=stt_pb2.RawAudio(
                        audio_encoding=stt_pb2.RawAudio.LINEAR16_PCM,
                        sample_rate_hertz=settings.speechkit_sample_rate_hertz,
                        audio_channel_count=settings.speechkit_audio_channels,
                    )
                ),
                text_normalization=stt_pb2.TextNormalizationOptions(
                    text_normalization=(
                        stt_pb2.TextNormalizationOptions.TEXT_NORMALIZATION_ENABLED
                    ),
                    profanity_filter=False,
                    literature_text=True,
                ),
                language_restriction=stt_pb2.LanguageRestrictionOptions(
                    restriction_type=(
                        stt_pb2.LanguageRestrictionOptions.WHITELIST
                    ),
                    language_code=[settings.speechkit_language_code],
                ),
                audio_processing_type=processing_type,
            ),
        )

    @staticmethod
    def _build_metadata() -> tuple[tuple[str, str], ...]:
        api_key = settings.yandex_cloud_api_key or settings.yandex_api_key
        if api_key:
            return (("authorization", f"Api-Key {api_key}"),)
        return (("authorization", f"Bearer {settings.yandex_iam_token}"),)

    @staticmethod
    def _parse_response(response: Any) -> STTResult | None:
        event_type = response.WhichOneof("Event")
        alternatives: list[Any] = []
        is_final = False

        if event_type == "partial":
            alternatives = list(response.partial.alternatives)
        elif event_type == "final":
            alternatives = list(response.final.alternatives)
            is_final = True
        elif event_type == "final_refinement":
            alternatives = list(response.final_refinement.normalized_text.alternatives)
            is_final = True
        else:
            return None

        if not alternatives:
            return None

        text = alternatives[0].text.strip()
        if not text:
            return None

        channel_tag = getattr(response, "channel_tag", None)
        speaker_tag = f"speaker_{channel_tag}" if channel_tag is not None else "unknown"
        return STTResult(
            text=text,
            speaker_tag=speaker_tag,
            is_final=is_final,
            event_type=event_type,
        )
