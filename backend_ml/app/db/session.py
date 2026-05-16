from collections.abc import AsyncGenerator
import asyncio
import logging

from sqlalchemy.exc import SQLAlchemyError
from sqlalchemy.ext.asyncio import AsyncSession, async_sessionmaker, create_async_engine
from sqlalchemy.engine import make_url

from app.core.config import settings
from app.db.models import Base

logger = logging.getLogger(__name__)

engine = create_async_engine(settings.database_url, echo=settings.debug)
AsyncSessionLocal = async_sessionmaker(
    bind=engine,
    class_=AsyncSession,
    expire_on_commit=False,
)


async def get_db() -> AsyncGenerator[AsyncSession, None]:
    async with AsyncSessionLocal() as session:
        yield session


async def init_db() -> None:
    for attempt in range(1, settings.database_connect_retries + 1):
        try:
            async with engine.begin() as connection:
                await connection.run_sync(Base.metadata.create_all)
            return
        except (OSError, SQLAlchemyError) as exc:
            if attempt == settings.database_connect_retries:
                raise

            url = make_url(settings.database_url).render_as_string(
                hide_password=True
            )
            logger.warning(
                "Database is unavailable at %s, retrying in %.1fs "
                "(attempt %s/%s): %s",
                url,
                settings.database_connect_retry_delay_seconds,
                attempt,
                settings.database_connect_retries,
                exc,
            )
            await asyncio.sleep(settings.database_connect_retry_delay_seconds)
