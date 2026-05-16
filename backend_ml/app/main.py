from contextlib import asynccontextmanager

from fastapi import Body, FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware

from app.api import auth, meetings, tasks, websocket
from app.core.config import settings
from app.db.schemas import MeetingAnalysis
from app.db.session import init_db
from app.services.gpt_service import GPTAnalysisError, YandexGPTService


@asynccontextmanager
async def lifespan(app: FastAPI):
    if settings.environment == "local":
        await init_db()
    yield


app = FastAPI(
    title=settings.app_name,
    debug=settings.debug,
    lifespan=lifespan,
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=settings.cors_origins,
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

app.include_router(auth.router, prefix=settings.api_prefix)
app.include_router(meetings.router, prefix=settings.api_prefix)
app.include_router(tasks.router, prefix=settings.api_prefix)
app.include_router(websocket.router)


@app.get("/health")
async def health() -> dict[str, str]:
    return {"status": "ok"}

#TODO: убрать, когда подсосется котлин
@app.post("/test_alice", response_model=MeetingAnalysis)
async def test_alice(
    text: str = Body(..., media_type="text/plain"),
) -> MeetingAnalysis:
    try:
        return await YandexGPTService().analyze_meeting(text)
    except GPTAnalysisError as exc:
        raise HTTPException(status_code=502, detail=str(exc)) from exc

