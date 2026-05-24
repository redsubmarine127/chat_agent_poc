from __future__ import annotations

from datetime import datetime
from uuid import UUID, uuid4

from app.database import Database
from app.errors import NotFoundError
from app.models import (
    AttachmentResponse,
    ConversationResponse,
    MessageResponse,
    MessageRole,
    MessageStatus,
    SkillResponse,
    utc_now,
)


def encode_attachment_ids(values: list[UUID]) -> str:
    return ",".join(str(value) for value in values)


def decode_attachment_ids(value: str | None) -> list[UUID]:
    if not value:
        return []
    return [UUID(item) for item in value.split(",") if item]


class ConversationRepository:
    def __init__(self, database: Database) -> None:
        self._database = database

    async def list(self) -> list[ConversationResponse]:
        query = """
            SELECT id, title, created_at, updated_at
              FROM conversations
             WHERE deleted = FALSE
             ORDER BY updated_at DESC
             LIMIT 100
        """
        async with self._database.acquire() as connection:
            rows = await connection.fetch(query)
        return [
            ConversationResponse(
                id=row["id"],
                title=row["title"],
                createdAt=row["created_at"],
                updatedAt=row["updated_at"],
            )
            for row in rows
        ]

    async def create(self, title: str) -> ConversationResponse:
        conversation_id = uuid4()
        now = utc_now()
        query = """
            INSERT INTO conversations (id, title, deleted, created_at, updated_at)
            VALUES ($1, $2, FALSE, $3, $3)
            RETURNING id, title, created_at, updated_at
        """
        async with self._database.acquire() as connection:
            row = await connection.fetchrow(query, conversation_id, title.strip(), now)
        return ConversationResponse(id=row["id"], title=row["title"], createdAt=row["created_at"], updatedAt=row["updated_at"])

    async def ensure_exists(self, conversation_id: UUID) -> None:
        query = "SELECT 1 FROM conversations WHERE id = $1 AND deleted = FALSE"
        async with self._database.acquire() as connection:
            exists = await connection.fetchval(query, conversation_id)
        if exists is None:
            raise NotFoundError("对话不存在")

    async def delete(self, conversation_id: UUID) -> None:
        query = "UPDATE conversations SET deleted = TRUE, updated_at = $2 WHERE id = $1 AND deleted = FALSE"
        async with self._database.acquire() as connection:
            result = await connection.execute(query, conversation_id, utc_now())
        if result == "UPDATE 0":
            raise NotFoundError("对话不存在")

    async def touch(self, conversation_id: UUID) -> None:
        query = "UPDATE conversations SET updated_at = $2 WHERE id = $1 AND deleted = FALSE"
        async with self._database.acquire() as connection:
            await connection.execute(query, conversation_id, utc_now())


class MessageRepository:
    def __init__(self, database: Database) -> None:
        self._database = database

    async def list(self, conversation_id: UUID) -> list[MessageResponse]:
        query = """
            SELECT id, conversation_id, role, content, skill_id, attachment_ids, status, created_at
              FROM chat_messages
             WHERE conversation_id = $1
             ORDER BY created_at ASC
        """
        async with self._database.acquire() as connection:
            rows = await connection.fetch(query, conversation_id)
        return [self._to_response(row) for row in rows]

    async def save(
        self,
        conversation_id: UUID,
        role: MessageRole,
        content: str,
        skill_id: str | None,
        attachment_ids: list[UUID],
        status: MessageStatus = MessageStatus.COMPLETED,
        message_id: UUID | None = None,
    ) -> MessageResponse:
        created_at = utc_now()
        query = """
            INSERT INTO chat_messages
                (id, conversation_id, role, content, skill_id, attachment_ids, status, created_at)
            VALUES ($1, $2, $3, $4, $5, $6, $7, $8)
            RETURNING id, conversation_id, role, content, skill_id, attachment_ids, status, created_at
        """
        async with self._database.acquire() as connection:
            row = await connection.fetchrow(
                query,
                message_id or uuid4(),
                conversation_id,
                role.value,
                content,
                skill_id,
                encode_attachment_ids(attachment_ids),
                status.value,
                created_at,
            )
        return self._to_response(row)

    async def clear(self, conversation_id: UUID) -> None:
        query = "DELETE FROM chat_messages WHERE conversation_id = $1"
        async with self._database.acquire() as connection:
            await connection.execute(query, conversation_id)

    def _to_response(self, row) -> MessageResponse:
        return MessageResponse(
            id=row["id"],
            conversationId=row["conversation_id"],
            role=MessageRole(row["role"]),
            content=row["content"],
            skillId=row["skill_id"],
            attachmentIds=decode_attachment_ids(row["attachment_ids"]),
            status=MessageStatus(row["status"]),
            createdAt=row["created_at"],
        )


class AttachmentRepository:
    def __init__(self, database: Database) -> None:
        self._database = database

    async def save(self, original_filename: str, content_type: str, storage_key: str, size_in_bytes: int) -> AttachmentResponse:
        query = """
            INSERT INTO attachments (id, original_filename, content_type, storage_key, size_in_bytes, created_at)
            VALUES ($1, $2, $3, $4, $5, $6)
            RETURNING id, original_filename, content_type, storage_key, size_in_bytes, created_at
        """
        async with self._database.acquire() as connection:
            row = await connection.fetchrow(query, uuid4(), original_filename, content_type, storage_key, size_in_bytes, utc_now())
        return AttachmentResponse(
            id=row["id"],
            originalFilename=row["original_filename"],
            contentType=row["content_type"],
            storageKey=row["storage_key"],
            sizeInBytes=row["size_in_bytes"],
            createdAt=row["created_at"],
        )


class DynamicSkillRepository:
    def __init__(self, database: Database) -> None:
        self._database = database

    async def list_enabled(self) -> list[SkillResponse]:
        query = """
            SELECT id, name, description, enabled
              FROM dynamic_skills
             WHERE enabled = TRUE
             ORDER BY created_at DESC
        """
        async with self._database.acquire() as connection:
            rows = await connection.fetch(query)
        return [SkillResponse(id=row["id"], name=row["name"], description=row["description"], enabled=row["enabled"]) for row in rows]

    async def save(self, skill_id: str, name: str, description: str) -> SkillResponse:
        query = """
            INSERT INTO dynamic_skills (id, name, description, enabled, created_at)
            VALUES ($1, $2, $3, TRUE, $4)
            ON CONFLICT (id) DO UPDATE
                SET name = EXCLUDED.name,
                    description = EXCLUDED.description,
                    enabled = TRUE
            RETURNING id, name, description, enabled
        """
        async with self._database.acquire() as connection:
            row = await connection.fetchrow(query, skill_id, name, description, utc_now())
        return SkillResponse(id=row["id"], name=row["name"], description=row["description"], enabled=row["enabled"])

