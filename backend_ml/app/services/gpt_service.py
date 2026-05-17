import asyncio
import json
import logging
import re
from typing import Any

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
            # Avoid leaving meetings stuck in PROCESSING forever if provider hangs.
            raw_text = await asyncio.wait_for(
                asyncio.to_thread(self._run_completion, transcript),
                timeout=90,
            )
            payload = self._parse_json_payload(raw_text)
            analysis = MeetingAnalysis.model_validate(payload)
        except asyncio.TimeoutError as exc:
            logger.exception("YandexGPT request timed out")
            raise GPTAnalysisError("YandexGPT request timed out") from exc
        except (json.JSONDecodeError, TypeError) as exc:
            logger.exception(
                "YandexGPT returned invalid analysis JSON: %s",
                raw_text[:1000] if "raw_text" in locals() else None,
            )
            raise GPTAnalysisError("YandexGPT returned invalid analysis JSON") from exc
        except ValidationError as exc:
            logger.exception(
                "YandexGPT returned invalid analysis schema: errors=%s raw=%s",
                exc.errors(),
                raw_text[:1000] if "raw_text" in locals() else None,
            )
            raise GPTAnalysisError(
                "YandexGPT returned invalid analysis format"
            ) from exc
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

    @classmethod
    def _parse_json_payload(cls, text: str) -> dict[str, Any]:
        cleaned = cls._strip_markdown_fences(text)
        decoder = json.JSONDecoder()
        for match in re.finditer(r"\{", cleaned):
            try:
                payload, _ = decoder.raw_decode(cleaned[match.start() :])
            except json.JSONDecodeError:
                continue
            if isinstance(payload, dict):
                return payload
        raise json.JSONDecodeError("No JSON object found", cleaned, 0)

    @staticmethod
    def _strip_markdown_fences(text: str) -> str:
        return re.sub(r"```(?:json)?\s?|```", "", text).strip()
