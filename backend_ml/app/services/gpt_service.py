import asyncio
import json
from typing import Any

from pydantic import ValidationError

from app.core.config import settings
from app.db.schemas import MeetingAnalysis
from app.utils.prompt_templates import (
    MEETING_ANALYSIS_SYSTEM_PROMPT,
    build_meeting_analysis_prompt,
)


class GPTAnalysisError(RuntimeError):
    pass


class YandexGPTService:
    def __init__(self) -> None:
        if not settings.yandex_auth_token:
            raise GPTAnalysisError("Yandex IAM token or API key is not configured")

    async def analyze_meeting(self, transcript: str) -> MeetingAnalysis:
        raw_text = await asyncio.to_thread(self._run_completion, transcript)
        try:
            payload = json.loads(raw_text)
            analysis = MeetingAnalysis.model_validate({**payload, "raw": payload})
        except (json.JSONDecodeError, TypeError, ValidationError) as exc:
            raise GPTAnalysisError("YandexGPT returned invalid analysis JSON") from exc
        return analysis

    def _run_completion(self, transcript: str) -> str:
        try:
            from yandex_ai_studio_sdk import AIStudio
        except ImportError as exc:
            raise GPTAnalysisError("yandex-ai-studio-sdk is not installed") from exc

        sdk = AIStudio(
            folder_id=settings.yandex_folder_id,
            auth=settings.yandex_auth_token,
        )
        model = sdk.models.completions(
            settings.yandex_gpt_model,
            model_version=settings.yandex_gpt_model_version,
        )
        model = model.configure(
            temperature=settings.yandex_gpt_temperature,
            max_tokens=settings.yandex_gpt_max_tokens,
            response_format="json",
        )
        result = model.run(
            [
                {"role": "system", "text": MEETING_ANALYSIS_SYSTEM_PROMPT},
                {"role": "user", "text": build_meeting_analysis_prompt(transcript)},
            ]
        )
        if not result:
            raise GPTAnalysisError("YandexGPT returned no alternatives")

        first = result[0]
        text = getattr(first, "text", None)
        if not isinstance(text, str) or not text.strip():
            raise GPTAnalysisError("YandexGPT returned empty text")
        return text.strip()

