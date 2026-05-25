from __future__ import annotations

import logging
import asyncio
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

logger = logging.getLogger(__name__)


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
        logger.info("db_conversation_list_query_started")
        query = """
            SELECT id, title, created_at, updated_at
              FROM conversations
             WHERE deleted = FALSE
             ORDER BY updated_at DESC
             LIMIT 100
        """
        async with self._database.acquire() as connection:
            rows = await connection.fetch(query)
        conversations = [
            ConversationResponse(
                id=row["id"],
                title=row["title"],
                createdAt=row["created_at"],
                updatedAt=row["updated_at"],
            )
            for row in rows
        ]
        logger.info("db_conversation_list_query_completed count=%s", len(conversations))
        return conversations

    async def create(self, title: str) -> ConversationResponse:
        conversation_id = uuid4()
        now = utc_now()
        logger.info("db_conversation_create_started conversation_id=%s title_length=%s", conversation_id, len(title.strip()))
        query = """
            INSERT INTO conversations (id, title, deleted, created_at, updated_at)
            VALUES ($1, $2, FALSE, $3, $3)
            RETURNING id, title, created_at, updated_at
        """
        async with self._database.acquire() as connection:
            row = await connection.fetchrow(query, conversation_id, title.strip(), now)
        logger.info("db_conversation_create_completed conversation_id=%s", row["id"])
        return ConversationResponse(id=row["id"], title=row["title"], createdAt=row["created_at"], updatedAt=row["updated_at"])

    async def ensure_exists(self, conversation_id: UUID) -> None:
        logger.info("db_conversation_ensure_started conversation_id=%s", conversation_id)
        query = "SELECT 1 FROM conversations WHERE id = $1 AND deleted = FALSE"
        async with self._database.acquire() as connection:
            exists = await connection.fetchval(query, conversation_id)
        if exists is None:
            logger.warning("db_conversation_ensure_not_found conversation_id=%s", conversation_id)
            raise NotFoundError("对话不存在")
        logger.info("db_conversation_ensure_completed conversation_id=%s", conversation_id)

    async def delete(self, conversation_id: UUID) -> None:
        logger.info("db_conversation_delete_started conversation_id=%s", conversation_id)
        query = "UPDATE conversations SET deleted = TRUE, updated_at = $2 WHERE id = $1 AND deleted = FALSE"
        async with self._database.acquire() as connection:
            result = await connection.execute(query, conversation_id, utc_now())
        if result == "UPDATE 0":
            logger.warning("db_conversation_delete_not_found conversation_id=%s", conversation_id)
            raise NotFoundError("对话不存在")
        logger.info("db_conversation_delete_completed conversation_id=%s result=%s", conversation_id, result)

    async def touch(self, conversation_id: UUID) -> None:
        logger.info("db_conversation_touch_started conversation_id=%s", conversation_id)
        query = "UPDATE conversations SET updated_at = $2 WHERE id = $1 AND deleted = FALSE"
        async with self._database.acquire() as connection:
            await connection.execute(query, conversation_id, utc_now())
        logger.info("db_conversation_touch_completed conversation_id=%s", conversation_id)


class MessageRepository:
    def __init__(self, database: Database) -> None:
        self._database = database

    async def list(self, conversation_id: UUID) -> list[MessageResponse]:
        logger.info("db_message_list_started conversation_id=%s", conversation_id)
        query = """
            SELECT id, conversation_id, role, content, skill_id, attachment_ids, status, created_at
              FROM chat_messages
             WHERE conversation_id = $1
             ORDER BY created_at ASC
        """
        async with self._database.acquire() as connection:
            rows = await connection.fetch(query, conversation_id)
        messages = [self._to_response(row) for row in rows]
        logger.info("db_message_list_completed conversation_id=%s count=%s", conversation_id, len(messages))
        return messages

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
        effective_message_id = message_id or uuid4()
        logger.info(
            "db_message_save_started conversation_id=%s message_id=%s role=%s status=%s content_length=%s attachment_count=%s",
            conversation_id,
            effective_message_id,
            role.value,
            status.value,
            len(content),
            len(attachment_ids),
        )
        query = """
            INSERT INTO chat_messages
                (id, conversation_id, role, content, skill_id, attachment_ids, status, created_at)
            VALUES ($1, $2, $3, $4, $5, $6, $7, $8)
            RETURNING id, conversation_id, role, content, skill_id, attachment_ids, status, created_at
        """
        async with self._database.acquire() as connection:
            row = await connection.fetchrow(
                query,
                effective_message_id,
                conversation_id,
                role.value,
                content,
                skill_id,
                encode_attachment_ids(attachment_ids),
                status.value,
                created_at,
            )
        message = self._to_response(row)
        logger.info("db_message_save_completed conversation_id=%s message_id=%s status=%s", conversation_id, message.id, message.status)
        return message

    async def clear(self, conversation_id: UUID) -> None:
        logger.info("db_message_clear_started conversation_id=%s", conversation_id)
        query = "DELETE FROM chat_messages WHERE conversation_id = $1"
        async with self._database.acquire() as connection:
            result = await connection.execute(query, conversation_id)
        logger.info("db_message_clear_completed conversation_id=%s result=%s", conversation_id, result)

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


class InMemoryConversationRepository:
    def __init__(self) -> None:
        self._lock = asyncio.Lock()
        self._conversations: dict[UUID, ConversationResponse] = {}
        self._deleted_ids: set[UUID] = set()

    async def list(self) -> list[ConversationResponse]:
        async with self._lock:
            conversations = [
                conversation
                for conversation in self._conversations.values()
                if conversation.id not in self._deleted_ids
            ]
        return sorted(conversations, key=lambda conversation: conversation.updatedAt, reverse=True)[:100]

    async def create(self, title: str) -> ConversationResponse:
        now = utc_now()
        conversation = ConversationResponse(id=uuid4(), title=title.strip() or "新的对话", createdAt=now, updatedAt=now)
        async with self._lock:
            self._conversations[conversation.id] = conversation
        logger.info("memory_conversation_created conversation_id=%s", conversation.id)
        return conversation

    async def ensure_exists(self, conversation_id: UUID) -> None:
        async with self._lock:
            exists = conversation_id in self._conversations and conversation_id not in self._deleted_ids
        if not exists:
            raise NotFoundError("对话不存在")

    async def delete(self, conversation_id: UUID) -> None:
        await self.ensure_exists(conversation_id)
        async with self._lock:
            self._deleted_ids.add(conversation_id)
            conversation = self._conversations[conversation_id]
            self._conversations[conversation_id] = conversation.model_copy(update={"updatedAt": utc_now()})
        logger.info("memory_conversation_deleted conversation_id=%s", conversation_id)

    async def touch(self, conversation_id: UUID) -> None:
        await self.ensure_exists(conversation_id)
        async with self._lock:
            conversation = self._conversations[conversation_id]
            self._conversations[conversation_id] = conversation.model_copy(update={"updatedAt": utc_now()})


class InMemoryMessageRepository:
    def __init__(self) -> None:
        self._lock = asyncio.Lock()
        self._messages: dict[UUID, MessageResponse] = {}

    async def list(self, conversation_id: UUID) -> list[MessageResponse]:
        async with self._lock:
            messages = [
                message
                for message in self._messages.values()
                if message.conversationId == conversation_id
            ]
        return sorted(messages, key=lambda message: message.createdAt)

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
        message = MessageResponse(
            id=message_id or uuid4(),
            conversationId=conversation_id,
            role=role,
            content=content,
            skillId=skill_id,
            attachmentIds=list(attachment_ids),
            status=status,
            createdAt=utc_now(),
        )
        async with self._lock:
            self._messages[message.id] = message
        logger.info("memory_message_saved conversation_id=%s message_id=%s role=%s status=%s", conversation_id, message.id, role.value, status.value)
        return message

    async def clear(self, conversation_id: UUID) -> None:
        async with self._lock:
            message_ids = [
                message_id
                for message_id, message in self._messages.items()
                if message.conversationId == conversation_id
            ]
            for message_id in message_ids:
                self._messages.pop(message_id, None)
        logger.info("memory_messages_cleared conversation_id=%s count=%s", conversation_id, len(message_ids))


class InMemoryAttachmentRepository:
    def __init__(self) -> None:
        self._lock = asyncio.Lock()
        self._attachments: dict[UUID, AttachmentResponse] = {}

    async def save(self, original_filename: str, content_type: str, storage_key: str, size_in_bytes: int) -> AttachmentResponse:
        attachment = AttachmentResponse(
            id=uuid4(),
            originalFilename=original_filename,
            contentType=content_type,
            storageKey=storage_key,
            sizeInBytes=size_in_bytes,
            createdAt=utc_now(),
        )
        async with self._lock:
            self._attachments[attachment.id] = attachment
        logger.info("memory_attachment_saved attachment_id=%s storage_key=%s", attachment.id, storage_key)
        return attachment


class InMemoryDynamicSkillRepository:
    def __init__(self) -> None:
        self._lock = asyncio.Lock()
        self._skills: dict[str, tuple[SkillResponse, datetime]] = {}

    async def list_enabled(self) -> list[SkillResponse]:
        async with self._lock:
            skills = [
                (skill, created_at)
                for skill, created_at in self._skills.values()
                if skill.enabled
            ]
        return [skill for skill, _ in sorted(skills, key=lambda item: item[1], reverse=True)]

    async def save(self, skill_id: str, name: str, description: str) -> SkillResponse:
        skill = SkillResponse(id=skill_id, name=name, description=description, enabled=True)
        async with self._lock:
            self._skills[skill_id] = (skill, utc_now())
        logger.info("memory_dynamic_skill_saved skill_id=%s", skill_id)
        return skill


class InMemoryRepositoryBundle:
    def __init__(self) -> None:
        self.conversations = InMemoryConversationRepository()
        self.messages = InMemoryMessageRepository()
        self.attachments = InMemoryAttachmentRepository()
        self.dynamic_skills = InMemoryDynamicSkillRepository()
