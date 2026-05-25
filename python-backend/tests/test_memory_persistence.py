from __future__ import annotations

from uuid import uuid4

import pytest

from app.config import get_settings
from app.models import MessageRole
from app.repositories import InMemoryRepositoryBundle


def test_persistence_mode_defaults_to_memory(monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.delenv("ASSISTANT_PERSISTENCE_MODE", raising=False)
    get_settings.cache_clear()

    settings = get_settings()

    assert settings.persistence_mode == "memory"


@pytest.mark.asyncio
async def test_in_memory_repositories_store_conversation_messages_and_attachments() -> None:
    repositories = InMemoryRepositoryBundle()

    conversation = await repositories.conversations.create("内存测试")
    await repositories.messages.save(conversation.id, MessageRole.USER, "你好", "general", [])
    attachment = await repositories.attachments.save("a.txt", "text/plain", f"{uuid4()}-a.txt", 2)

    conversations = await repositories.conversations.list()
    messages = await repositories.messages.list(conversation.id)

    assert conversations == [conversation]
    assert len(messages) == 1
    assert messages[0].content == "你好"
    assert attachment.originalFilename == "a.txt"

    await repositories.messages.clear(conversation.id)

    assert await repositories.messages.list(conversation.id) == []
