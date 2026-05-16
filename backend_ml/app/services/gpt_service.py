import asyncio
import json
import logging
import re

from pydantic import ValidationError

from app.core.config import settings
from app.db.schemas import MeetingAnalysis
from app.utils.prompt_templates import (
    MEETING_ANALYSIS_SYSTEM_PROMPT,
    build_meeting_analysis_prompt,
)

logger = logging.getLogger(__name__)


class GPTAnalysisError(RuntimeError):
    pass


class YandexGPTService:
    MODEL_ALIASES = {
        "yandexgpt-5-1-pro": "yandexgpt-5.1",
        "yandexgpt-5.1-pro": "yandexgpt-5.1",
    }

    def __init__(self) -> None:
        api_key = settings.yandex_cloud_api_key or settings.yandex_api_key
        if not api_key:
            raise GPTAnalysisError("Yandex Cloud API key is not configured")
        if not settings.yandex_effective_folder_id:
            raise GPTAnalysisError("Yandex Cloud folder ID is not configured")

        try:
            from openai import OpenAI
        except ImportError as exc:
            raise GPTAnalysisError("openai package is not installed") from exc

        self.client = OpenAI(
            api_key=api_key,
            base_url="https://llm.api.cloud.yandex.net/v1",
        )
        self.model_uri = self._build_model_uri(settings.yandex_gpt_model)

    async def analyze_meeting(self, transcript: str) -> MeetingAnalysis:
        try:
            raw_text = await asyncio.to_thread(self._run_completion, transcript)
            raw_text = self._extract_json(raw_text)
            payload = json.loads(raw_text)
            analysis = MeetingAnalysis.model_validate(payload)
        except (json.JSONDecodeError, TypeError, ValidationError) as exc:
            logger.exception(
                "YandexGPT returned invalid analysis JSON: %s",
                raw_text[:1000] if "raw_text" in locals() else None,
            )
            raise GPTAnalysisError("YandexGPT returned invalid analysis JSON") from exc
        return analysis

    def _run_completion(self, transcript: str) -> str:
        try:
            response = self.client.chat.completions.create(
                model=self.model_uri,
                messages=[
                    {"role": "system", "content": MEETING_ANALYSIS_SYSTEM_PROMPT},
                    {
                        "role": "user",
                        "content": build_meeting_analysis_prompt(transcript),
                    },
                ],
                temperature=settings.yandex_gpt_temperature,
                max_tokens=settings.yandex_gpt_max_tokens,
                response_format={"type": "json_object"},
            )
        except Exception as exc:
            logger.exception("YandexGPT request failed")
            raise GPTAnalysisError(f"YandexGPT request failed: {exc}") from exc

        text = response.choices[0].message.content
        if not isinstance(text, str) or not text.strip():
            raise GPTAnalysisError("YandexGPT returned empty text")
        return text.strip()

    def _build_model_uri(self, model: str) -> str:
        if model.startswith("gpt://"):
            return model

        model = self.MODEL_ALIASES.get(model, model)
        return f"gpt://{settings.yandex_effective_folder_id}/{model}"

    @staticmethod
    def _extract_json(text: str) -> str:
        return re.sub(r"```(?:json)?\s?|```", "", text).strip()
