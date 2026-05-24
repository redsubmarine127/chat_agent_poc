from __future__ import annotations

import logging
from pathlib import Path

import asyncpg

from app.config import Settings

logger = logging.getLogger(__name__)


async def reset_connection(connection: asyncpg.Connection) -> None:
    await connection.execute("RESET ALL")


class Database:
    def __init__(self, settings: Settings) -> None:
        self._settings = settings
        self._pool: asyncpg.Pool | None = None

    async def connect(self) -> None:
        if self._pool is not None:
            return
        self._pool = await asyncpg.create_pool(
            dsn=self._settings.db_dsn,
            min_size=1,
            max_size=10,
            command_timeout=10,
            reset=reset_connection,
        )
        logger.info("database pool initialized")

    async def close(self) -> None:
        if self._pool is None:
            return
        await self._pool.close()
        self._pool = None
        logger.info("database pool closed")

    async def migrate(self) -> None:
        schema_path = Path(__file__).with_name("schema.sql")
        statements = schema_path.read_text(encoding="utf-8")
        async with self.acquire() as connection:
            await connection.execute(statements)
        logger.info("database schema ensured")

    def acquire(self) -> asyncpg.pool.PoolAcquireContext:
        if self._pool is None:
            raise RuntimeError("database pool is not initialized")
        return self._pool.acquire()
