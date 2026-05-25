from __future__ import annotations

import json
from collections.abc import AsyncIterator
from uuid import UUID, uuid4

import pytest

from app.ai.gateway import ModelChunk
from app.config import ModelConfig
from app.models import ConversationResponse, MessageResponse, MessageRole, MessageStatus, SkillResponse, utc_now
from app.services.chat_service import ChatStreamService


def parse_sse_events(lines: list[str]) -> list[dict]:
    events = []
    for line in lines:
        data_line = next(item for item in line.splitlines() if item.startswith("data: "))
        events.append(json.loads(data_line.removeprefix("data: ")))
    return events


class FakeConversationRepository:
    def __init__(self) -> None:
        self.ensure_exists_calls = 0
        self.touch_calls = 0

    async def ensure_exists(self, conversation_id: UUID) -> None:
        self.ensure_exists_calls += 1

    async def touch(self, conversation_id: UUID) -> None:
        self.touch_calls += 1


class FakeMessageRepository:
    def __init__(self) -> None:
        self.saved: list[MessageResponse] = []

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
            attachmentIds=attachment_ids,
            status=status,
            createdAt=utc_now(),
        )
        self.saved.append(message)
        return message

    async def list(self, conversation_id: UUID) -> list[MessageResponse]:
        return [message for message in self.saved if message.conversationId == conversation_id]


class FakeSkillService:
    async def get_skill(self, skill_id: str) -> SkillResponse:
        return SkillResponse(id=skill_id, name="通用助手", description="简洁回答")


class FakeModelService:
    def get_model(self, model_id: str) -> ModelConfig:
        return ModelConfig(model_id, "测试模型", "local", model_id)


class FakeRagService:
    async def retrieve(self, query: str, limit: int | None = None):
        return []


class FakeMcpService:
    async def list_tools(self):
        return []


class FakeTrace:
    def event(self, name: str, metadata: dict) -> None:
        pass

    def end(self, status: str, output: str = "", error: str = "") -> None:
        pass


class FakeObservability:
    def start_chat_trace(self, **kwargs) -> FakeTrace:
        return FakeTrace()


class FlakyGateway:
    def __init__(self, failures_before_success: int) -> None:
        self.failures_before_success = failures_before_success
        self.attempts = 0

    async def stream(self, model: ModelConfig, messages: list[dict[str, str]]) -> AsyncIterator[ModelChunk]:
        self.attempts += 1
        if self.attempts <= self.failures_before_success:
            raise RuntimeError("temporary model failure")
        yield ModelChunk("delta", "恢复成功")


def build_service(gateway: FlakyGateway, message_repository: FakeMessageRepository | None = None) -> ChatStreamService:
    return ChatStreamService(
        FakeConversationRepository(),
        message_repository or FakeMessageRepository(),
        FakeSkillService(),
        FakeModelService(),
        gateway,
        FakeRagService(),
        FakeMcpService(),
        FakeObservability(),
    )


@pytest.mark.asyncio
async def test_stream_retries_llm_before_first_chunk_and_completes():
    gateway = FlakyGateway(failures_before_success=2)
    service = build_service(gateway)

    events = parse_sse_events(
        [
            event
            async for event in service.stream(
                uuid4(),
                "你好",
                "general",
                "local-fallback",
                [],
            )
        ]
    )

    assert gateway.attempts == 3
    assert events[0]["type"] == "started"
    assert events[-2]["type"] == "delta"
    assert events[-2]["content"] == "恢复成功"
    assert events[-1]["type"] == "completed"
    assert any(event["content"] == "模型调用暂时失败，正在进行第 2 次尝试。" for event in events)


@pytest.mark.asyncio
async def test_stream_stops_after_three_llm_failures_and_saves_failed_message():
    gateway = FlakyGateway(failures_before_success=3)
    message_repository = FakeMessageRepository()
    service = build_service(gateway, message_repository)

    events = parse_sse_events(
        [
            event
            async for event in service.stream(
                uuid4(),
                "你好",
                "general",
                "local-fallback",
                [],
            )
        ]
    )

    assert gateway.attempts == 3
    assert events[-1]["type"] == "failed"
    assert "连续重试 3 次仍未成功" in events[-1]["content"]
    assert message_repository.saved[-1].role == MessageRole.ASSISTANT
    assert message_repository.saved[-1].status == MessageStatus.FAILED
