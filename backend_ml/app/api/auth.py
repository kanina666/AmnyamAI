from uuid import uuid4

from fastapi import APIRouter, Depends, HTTPException, Query, status
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.security import (
    build_google_oauth_url,
    create_access_token,
    exchange_google_code,
    fetch_google_userinfo,
)
from app.db.models import User
from app.db.schemas import AuthUrlResponse, TokenResponse
from app.db.session import get_db

router = APIRouter(prefix="/auth", tags=["auth"])


@router.get("/google/url", response_model=AuthUrlResponse)
async def google_auth_url() -> AuthUrlResponse:
    state = str(uuid4())
    return AuthUrlResponse(url=build_google_oauth_url(state), state=state)


@router.post("/google/callback", response_model=TokenResponse)
async def google_callback(
    code: str = Query(...),
    db: AsyncSession = Depends(get_db),
) -> TokenResponse:
    token_payload = await exchange_google_code(code, redirect_uri="")
    access_token = token_payload.get("access_token")
    if not access_token:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="Google did not return access_token",
        )

    userinfo = await fetch_google_userinfo(access_token)
    google_id = userinfo.get("sub")
    email = userinfo.get("email")
    if not google_id or not email:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="Google userinfo is incomplete",
        )

    result = await db.execute(select(User).where(User.google_id == google_id))
    user = result.scalar_one_or_none()
    if user is None:
        user = User(
            google_id=google_id,
            email=email,
            name=userinfo.get("name"),
        )
        db.add(user)
    else:
        user.email = email
        user.name = userinfo.get("name")

    refresh_token = token_payload.get("refresh_token")
    if refresh_token:
        user.google_refresh_token = refresh_token

    await db.commit()
    await db.refresh(user)
    return TokenResponse(
        access_token=create_access_token(user.id),
        user=user,
    )

