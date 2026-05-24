from __future__ import annotations

import json
import logging
from collections.abc import AsyncIterator
from uuid import UUID, uuid4

from app.ai.gateway import RoutingChatModelGateway
from app.ai.graph import build_chat_graph
from app.errors import AssistantError
from app.models import ChatStreamResponse, McpToolResponse, MessageRole, MessageStatus, RagContextResponse
from app.observability import AssistantObservability, NoopObservability
from app.repositories import ConversationRepository, MessageRepository
from app.services.mcp_service import McpService
from app.services.model_service import ModelService
from app.services.rag_service import RagService
from app.services.skill_service import SkillService

logger = logging.getLogger(__name__)


class ConversationService:
    def __init__(self, conversation_repository: ConversationRepository, message_repository: MessageRepository) -> None:
        self._conversation_repository = conversation_repository
        self._message_repository = message_repository

    async def list_conversations(self):
        return await self._conversation_repository.list()

    async def create_conversation(self, title: str):
        return await self._conversation_repository.create(title)

    async def delete_conversation(self, conversation_id: UUID) -> None:
        await self._conversation_repository.delete(conversation_id)

    async def list_messages(self, conversation_id: UUID):
        await self._conversation_repository.ensure_exists(conversation_id)
        return await self._message_repository.list(conversation_id)

    async def clear_messages(self, conversation_id: UUID) -> None:
        await self._conversation_repository.ensure_exists(conversation_id)
        await self._message_repository.clear(conversation_id)
        await self._conversation_repository.touch(conversation_id)


class ChatStreamService:
    def __init__(
        self,
        conversation_repository: ConversationRepository,
        message_repository: MessageRepository,
        skill_service: SkillService,
        model_service: ModelService,
        gateway: RoutingChatModelGateway,
        rag_service: RagService,
        mcp_service: McpService,
        observability: AssistantObservability | None = None,
    ) -> None:
        self._conversation_repository = conversation_repository
        self._message_repository = message_repository
        self._skill_service = skill_service
        self._model_service = model_service
        self._gateway = gateway
        self._rag_service = rag_service
        self._mcp_service = mcp_service
        self._observability = observability or NoopObservability()
        self._graph = build_chat_graph()

    async def stream(
        self,
        conversation_id: UUID,
        content: str,
        skill_id: str,
        model_id: str,
        attachment_ids: list[UUID],
    ) -> AsyncIterator[str]:
        await self._conversation_repository.ensure_exists(conversation_id)
        user_message = await self._message_repository.save(
            conversation_id=conversation_id,
            role=MessageRole.USER,
            content=content.strip(),
            skill_id=skill_id,
            attachment_ids=attachment_ids,
        )
        await self._conversation_repository.touch(conversation_id)

        assistant_message_id = uuid4()
        trace = self._observability.start_chat_trace(
            conversation_id=conversation_id,
            message_id=assistant_message_id,
            model_id=model_id,
            skill_id=skill_id,
            content=content.strip(),
        )
        yield self._sse(ChatStreamResponse(type="started", messageId=assistant_message_id, content=""))

        answer_parts: list[str] = []
        try:
            skill = await self._skill_service.get_skill(skill_id)
            model = self._model_service.get_model(model_id)
            history = await self._message_repository.list(conversation_id)
            rag_contexts = await self._rag_service.retrieve(content.strip())
            mcp_tools = await self._mcp_service.list_tools()
            trace.event(
                "context.loaded",
                {
                    "historyCount": len(history),
                    "ragContextIds": [context.source_id for context in rag_contexts],
                    "mcpToolNames": [tool.name for tool in mcp_tools],
                },
            )
            graph_state = await self._graph.ainvoke(
                {
                    "content": content.strip(),
                    "skill": skill,
                    "model": model,
                    "history": [item for item in history if item.id != user_message.id],
                    "rag_contexts": [
                        RagContextResponse(
                            sourceId=context.source_id,
                            title=context.title,
                            content=context.content,
                            score=context.score,
                        )
                        for context in rag_contexts
                    ],
                    "mcp_tools": [
                        McpToolResponse(name=tool.name, description=tool.description, enabled=tool.enabled)
                        for tool in mcp_tools
                    ],
                }
            )
            reasoning = graph_state.get("reasoning", "")
            if reasoning:
                trace.event("reasoning.emitted", {"chars": len(reasoning)})
                yield self._sse(ChatStreamResponse(type="reasoning", messageId=assistant_message_id, content=reasoning))

            async for chunk in self._gateway.stream(model, graph_state["prompt_messages"]):
                if chunk.type == "reasoning":
                    trace.event("reasoning.emitted", {"chars": len(chunk.content)})
                    yield self._sse(ChatStreamResponse(type="reasoning", messageId=assistant_message_id, content=chunk.content))
                    continue
                answer_parts.append(chunk.content)
                yield self._sse(ChatStreamResponse(type="delta", messageId=assistant_message_id, content=chunk.content))

            answer = "".join(answer_parts)
            await self._message_repository.save(
                conversation_id=conversation_id,
                role=MessageRole.ASSISTANT,
                content=answer,
                skill_id=skill_id,
                attachment_ids=[],
                status=MessageStatus.COMPLETED,
                message_id=assistant_message_id,
            )
            await self._conversation_repository.touch(conversation_id)
            trace.end("completed", output=answer)
            yield self._sse(ChatStreamResponse(type="completed", messageId=assistant_message_id, content=""))
        except Exception as error:
            logger.exception(
                "chat_stream_failed conversation_id=%s model_id=%s skill_id=%s",
                conversation_id,
                model_id,
                skill_id,
            )
            failure_message = error.message if isinstance(error, AssistantError) else "模型调用失败，请检查模型配置或稍后重试。"
            await self._message_repository.save(
                conversation_id=conversation_id,
                role=MessageRole.ASSISTANT,
                content=failure_message,
                skill_id=skill_id,
                attachment_ids=[],
                status=MessageStatus.FAILED,
                message_id=assistant_message_id,
            )
            trace.end("failed", output="".join(answer_parts), error=failure_message)
            yield self._sse(ChatStreamResponse(type="failed", messageId=assistant_message_id, content=failure_message))

    def _sse(self, response: ChatStreamResponse) -> str:
        payload = response.model_dump(mode="json")
        return f"event: {response.type}\nid: {response.messageId}\ndata: {json.dumps(payload, ensure_ascii=False)}\n\n"
