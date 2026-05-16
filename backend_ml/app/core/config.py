from functools import lru_cache
from typing import Literal

from pydantic import AnyHttpUrl, Field, computed_field
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(
        env_file=".env",
        env_file_encoding="utf-8",
        extra="ignore",
        case_sensitive=False,
    )

    app_name: str = "MeetingAgent Backend"
    environment: Literal["local", "dev", "prod"] = "local"
    debug: bool = True
    api_prefix: str = "/api/v1"
    cors_origins: list[str] = Field(default_factory=lambda: ["*"])

    database_url: str = (
        "postgresql+asyncpg://meetingagent:meetingagent@db:5432/meetingagent"
    )

    jwt_secret_key: str = "change-me"
    jwt_algorithm: str = "HS256"
    access_token_expire_minutes: int = 60 * 24

    yandex_iam_token: str | None = None
    yandex_api_key: str | None = None
    yandex_folder_id: str = "replace-me"
    yandex_gpt_model: str = "yandexgpt"
    yandex_gpt_model_version: str = "pro"
    yandex_gpt_temperature: float = 0.2
    yandex_gpt_max_tokens: int = 4000
    speechkit_endpoint: str = "stt.api.cloud.yandex.net:443"
    speechkit_language_code: str = "ru-RU"
    speechkit_sample_rate_hertz: int = 16000
    speechkit_audio_channels: int = 1
    speechkit_model: str = "general:rc"
    speechkit_processing_type: Literal["REAL_TIME", "FULL_DATA"] = "FULL_DATA"
    speechkit_chunk_size_bytes: int = 4096

    google_client_id: str | None = None
    google_client_secret: str | None = None
    google_redirect_uri: AnyHttpUrl | None = None

    @computed_field  # type: ignore[prop-decorator]
    @property
    def yandex_auth_token(self) -> str | None:
        return self.yandex_api_key or self.yandex_iam_token


@lru_cache
def get_settings() -> Settings:
    return Settings()


settings = get_settings()
