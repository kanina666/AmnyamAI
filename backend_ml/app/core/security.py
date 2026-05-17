from datetime import UTC, datetime, timedelta
from typing import Any
from urllib.parse import urlencode

import httpx
from fastapi import Depends, HTTPException, status
from fastapi.security import OAuth2PasswordBearer
from jose import JWTError, jwt

from app.core.config import settings

oauth2_scheme = OAuth2PasswordBearer(tokenUrl=f"{settings.api_prefix}/auth/token")

GOOGLE_AUTH_URL = "https://accounts.google.com/o/oauth2/v2/auth"
GOOGLE_TOKEN_URL = "https://oauth2.googleapis.com/token"
GOOGLE_USERINFO_URL = "https://openidconnect.googleapis.com/v1/userinfo"
GOOGLE_SCOPES = (
    "openid",
    "email",
    "profile",
    "https://www.googleapis.com/auth/calendar.events",
)


def create_access_token(subject: str, expires_delta: timedelta | None = None) -> str:
    expire = datetime.now(UTC) + (
        expires_delta or timedelta(minutes=settings.access_token_expire_minutes)
    )
    payload: dict[str, Any] = {"sub": subject, "exp": expire}
    return jwt.encode(payload, settings.jwt_secret_key, algorithm=settings.jwt_algorithm)


def decode_access_token(token: str) -> str:
    try:
        payload = jwt.decode(
            token,
            settings.jwt_secret_key,
            algorithms=[settings.jwt_algorithm],
        )
        subject = payload.get("sub")
    except JWTError as exc:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Invalid access token",
        ) from exc
    if not isinstance(subject, str) or not subject:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Invalid access token subject",
        )
    return subject


async def get_current_user_id(token: str = Depends(oauth2_scheme)) -> str:
    return decode_access_token(token)


def build_google_oauth_url(state: str) -> str:
    if not settings.google_client_id or not settings.google_redirect_uri:
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail="Google OAuth is not configured",
        )
    query = urlencode(
        {
            "client_id": settings.google_client_id,
            "redirect_uri": str(settings.google_redirect_uri),
            "response_type": "code",
            "scope": " ".join(GOOGLE_SCOPES),
            "access_type": "offline",
            "prompt": "consent",
            "state": state,
        }
    )
    return f"{GOOGLE_AUTH_URL}?{query}"


async def exchange_google_code(
    code: str,
    redirect_uri: str | None = None,
) -> dict[str, Any]:
    if redirect_uri is not None and not redirect_uri.strip():
        redirect_uri = None
    if (
        not settings.google_client_id
        or not settings.google_client_secret
        or (redirect_uri is None and not settings.google_redirect_uri)
    ):
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail="Google OAuth is not configured",
        )
    token_redirect_uri = (
        str(settings.google_redirect_uri) if redirect_uri is None else redirect_uri
    )
    async with httpx.AsyncClient(timeout=20) as client:
        data = {
            "code": code,
            "client_id": settings.google_client_id,
            "client_secret": settings.google_client_secret,
            "grant_type": "authorization_code",
        }
        if redirect_uri is not None:
            data["redirect_uri"] = token_redirect_uri

        response = await client.post(GOOGLE_TOKEN_URL, data=data)
        try:
            response.raise_for_status()
        except httpx.HTTPStatusError as exc:
            detail: Any
            try:
                detail = exc.response.json()
            except Exception:
                detail = exc.response.text
            raise HTTPException(
                status_code=status.HTTP_502_BAD_GATEWAY,
                detail={"message": "Google token exchange failed", "google": detail},
            ) from exc
        return response.json()


async def fetch_google_userinfo(access_token: str) -> dict[str, Any]:
    async with httpx.AsyncClient(timeout=20) as client:
        response = await client.get(
            GOOGLE_USERINFO_URL,
            headers={"Authorization": f"Bearer {access_token}"},
        )
        response.raise_for_status()
        return response.json()

