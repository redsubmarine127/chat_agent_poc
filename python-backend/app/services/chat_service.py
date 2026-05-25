from __future__ import annotations

import json
import logging
import asyncio
from collections.abc import AsyncIterator
from collections.abc import Awaitable, Callable
from typing import TypeVar
from uuid import UUID, uuid4

from app.ai.gateway import ModelChunk, RoutingChatModelGateway
from app.ai.graph import build_chat_graph
from app.errors import AgentStepFailedError, AssistantError
from app.models import ChatStreamResponse, McpToolResponse, MessageRole, MessageStatus, RagContextResponse
from app.observability import AssistantObservability, NoopObservability
from app.repositories import ConversationRepository, MessageRepository
from app.services.mcp_service import McpService
from app.services.model_service import ModelService
from app.services.rag_service import RagService
from app.services.skill_service import SkillService

logger = logging.getLogger(__name__)
T = TypeVar("T")

AGENT_MAX_ATTEMPTS = 3
AGENT_RETRY_DELAY_SECONDS = 0.25


class ConversationService:
    def __init__(self, conversation_repository: ConversationRepository, message_repository: MessageRepository) -> None:
        self._conversation_repository = conversation_repository
        self._message_repository = message_repository

    async def list_conversations(self):
        logger.info("db_conversation_list_started")
        return await self._conversation_repository.list()

    async def create_conversation(self, title: str):
        conversation = await self._conversation_repository.create(title)
        logger.info("db_conversation_created conversation_id=%s", conversation.id)
        return conversation

    async def delete_conversation(self, conversation_id: UUID) -> None:
        await self._conversation_repository.delete(conversation_id)
        logger.info("db_conversation_deleted conversation_id=%s", conversation_id)

    async def list_messages(self, conversation_id: UUID):
        await self._conversation_repository.ensure_exists(conversation_id)
        return await self._message_repository.list(conversation_id)

    async def clear_messages(self, conversation_id: UUID) -> None:
        await self._conversation_repository.ensure_exists(conversation_id)
        await self._message_repository.clear(conversation_id)
        await self._conversation_repository.touch(conversation_id)
        logger.info("db_conversation_context_cleared conversation_id=%s", conversation_id)


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
        request_context = {
            "conversationId": str(conversation_id),
            "modelId": model_id,
            "skillId": skill_id,
            "attachmentCount": len(attachment_ids),
            "contentLength": len(content.strip()),
        }
        logger.info(
            "agent_stream_started conversation_id=%s model_id=%s skill_id=%s attachment_count=%s content_length=%s",
            conversation_id,
            model_id,
            skill_id,
            len(attachment_ids),
            len(content.strip()),
        )
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
            await self._run_step("database.ensure_conversation", "数据库确认对话", lambda: self._conversation_repository.ensure_exists(conversation_id), request_context)
            user_message = await self._run_step(
                "database.save_user_message",
                "数据库保存用户消息",
                lambda: self._message_repository.save(
                    conversation_id=conversation_id,
                    role=MessageRole.USER,
                    content=content.strip(),
                    skill_id=skill_id,
                    attachment_ids=attachment_ids,
                ),
                request_context,
            )
            await self._run_step("database.touch_conversation", "数据库更新对话时间", lambda: self._conversation_repository.touch(conversation_id), request_context)
            skill = await self._run_step("skill.load", "Skill 加载", lambda: self._skill_service.get_skill(skill_id), request_context)
            model = await self._run_step("model.resolve", "模型配置解析", lambda: self._async_value(self._model_service.get_model(model_id)), request_context)
            history = await self._run_step("database.load_history", "数据库加载历史消息", lambda: self._message_repository.list(conversation_id), request_context)
            rag_contexts = await self._run_step("rag.retrieve", "RAG 上下文检索", lambda: self._rag_service.retrieve(content.strip()), request_context)
            mcp_tools = await self._run_step("mcp.list_tools", "MCP 工具加载", self._mcp_service.list_tools, request_context)
            trace.event(
                "context.loaded",
                {
                    "historyCount": len(history),
                    "ragContextIds": [context.source_id for context in rag_contexts],
                    "mcpToolNames": [tool.name for tool in mcp_tools],
                },
            )
            graph_state = await self._run_step(
                "langgraph.invoke",
                "LangGraph 编排",
                lambda: self._graph.ainvoke(
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
                ),
                request_context,
            )
            reasoning = graph_state.get("reasoning", "")
            if reasoning:
                trace.event("reasoning.emitted", {"chars": len(reasoning)})
                yield self._sse(ChatStreamResponse(type="reasoning", messageId=assistant_message_id, content=reasoning))

            async for chunk in self._stream_model_with_retry(model, graph_state["prompt_messages"], request_context):
                if chunk.type == "reasoning":
                    trace.event("reasoning.emitted", {"chars": len(chunk.content)})
                    yield self._sse(ChatStreamResponse(type="reasoning", messageId=assistant_message_id, content=chunk.content))
                    continue
                answer_parts.append(chunk.content)
                yield self._sse(ChatStreamResponse(type="delta", messageId=assistant_message_id, content=chunk.content))

            answer = "".join(answer_parts)
            await self._run_step(
                "database.save_assistant_message",
                "数据库保存助手消息",
                lambda: self._message_repository.save(
                    conversation_id=conversation_id,
                    role=MessageRole.ASSISTANT,
                    content=answer,
                    skill_id=skill_id,
                    attachment_ids=[],
                    status=MessageStatus.COMPLETED,
                    message_id=assistant_message_id,
                ),
                request_context,
            )
            await self._run_step("database.touch_conversation_completed", "数据库更新对话完成时间", lambda: self._conversation_repository.touch(conversation_id), request_context)
            trace.end("completed", output=answer)
            logger.info(
                "agent_stream_completed conversation_id=%s assistant_message_id=%s answer_chars=%s",
                conversation_id,
                assistant_message_id,
                len(answer),
            )
            yield self._sse(ChatStreamResponse(type="completed", messageId=assistant_message_id, content=""))
        except Exception as error:
            logger.exception(
                "chat_stream_failed conversation_id=%s model_id=%s skill_id=%s",
                conversation_id,
                model_id,
                skill_id,
            )
            failure_message = error.message if isinstance(error, AssistantError) else "模型调用失败，请检查模型配置或稍后重试。"
            await self._save_failed_assistant_message(
                conversation_id=conversation_id,
                assistant_message_id=assistant_message_id,
                skill_id=skill_id,
                failure_message=failure_message,
                request_context=request_context,
            )
            trace.end("failed", output="".join(answer_parts), error=failure_message)
            yield self._sse(ChatStreamResponse(type="failed", messageId=assistant_message_id, content=failure_message))

    def _sse(self, response: ChatStreamResponse) -> str:
        payload = response.model_dump(mode="json")
        return f"event: {response.type}\nid: {response.messageId}\ndata: {json.dumps(payload, ensure_ascii=False)}\n\n"

    async def _run_step(
        self,
        step_name: str,
        display_name: str,
        operation: Callable[[], Awaitable[T]],
        context: dict[str, object],
    ) -> T:
        last_error: Exception | None = None
        for attempt in range(1, AGENT_MAX_ATTEMPTS + 1):
            logger.info(
                "agent_step_started step=%s attempt=%s max_attempts=%s context=%s",
                step_name,
                attempt,
                AGENT_MAX_ATTEMPTS,
                context,
            )
            try:
                result = await operation()
                logger.info("agent_step_succeeded step=%s attempt=%s context=%s", step_name, attempt, context)
                return result
            except Exception as error:  # noqa: BLE001 - retry boundary must capture framework/provider errors
                last_error = error
                logger.warning(
                    "agent_step_failed step=%s attempt=%s max_attempts=%s error=%s context=%s",
                    step_name,
                    attempt,
                    AGENT_MAX_ATTEMPTS,
                    repr(error),
                    context,
                    exc_info=True,
                )
                if attempt >= AGENT_MAX_ATTEMPTS:
                    break
                await asyncio.sleep(AGENT_RETRY_DELAY_SECONDS * attempt)
        raise AgentStepFailedError(display_name, AGENT_MAX_ATTEMPTS, last_error or RuntimeError("unknown error"))

    async def _stream_model_with_retry(
        self,
        model,
        prompt_messages: list[dict[str, str]],
        context: dict[str, object],
    ) -> AsyncIterator[ModelChunk]:
        last_error: Exception | None = None
        for attempt in range(1, AGENT_MAX_ATTEMPTS + 1):
            emitted_chunk = False
            logger.info(
                "agent_step_started step=llm.stream attempt=%s max_attempts=%s model_id=%s prompt_message_count=%s context=%s",
                attempt,
                AGENT_MAX_ATTEMPTS,
                model.id,
                len(prompt_messages),
                context,
            )
            try:
                async for chunk in self._gateway.stream(model, prompt_messages):
                    emitted_chunk = True
                    yield chunk
                logger.info("agent_step_succeeded step=llm.stream attempt=%s model_id=%s context=%s", attempt, model.id, context)
                return
            except Exception as error:  # noqa: BLE001 - model SDK errors vary by provider
                last_error = error
                logger.warning(
                    "agent_step_failed step=llm.stream attempt=%s max_attempts=%s emitted_chunk=%s model_id=%s error=%s context=%s",
                    attempt,
                    AGENT_MAX_ATTEMPTS,
                    emitted_chunk,
                    model.id,
                    repr(error),
                    context,
                    exc_info=True,
                )
                if emitted_chunk or attempt >= AGENT_MAX_ATTEMPTS:
                    break
                next_attempt = attempt + 1
                yield ModelChunk("reasoning", f"模型调用暂时失败，正在进行第 {next_attempt} 次尝试。")
                await asyncio.sleep(AGENT_RETRY_DELAY_SECONDS * attempt)
        raise AgentStepFailedError("模型调用", AGENT_MAX_ATTEMPTS, last_error or RuntimeError("unknown model error"))

    async def _save_failed_assistant_message(
        self,
        *,
        conversation_id: UUID,
        assistant_message_id: UUID,
        skill_id: str,
        failure_message: str,
        request_context: dict[str, object],
    ) -> None:
        try:
            await self._run_step(
                "database.save_failed_assistant_message",
                "数据库保存失败消息",
                lambda: self._message_repository.save(
                    conversation_id=conversation_id,
                    role=MessageRole.ASSISTANT,
                    content=failure_message,
                    skill_id=skill_id,
                    attachment_ids=[],
                    status=MessageStatus.FAILED,
                    message_id=assistant_message_id,
                ),
                request_context,
            )
        except Exception as save_error:  # noqa: BLE001 - failed event must still reach the client
            logger.error(
                "agent_failed_message_save_failed conversation_id=%s assistant_message_id=%s error=%s context=%s",
                conversation_id,
                assistant_message_id,
                repr(save_error),
                request_context,
                exc_info=True,
            )

    async def _async_value(self, value: T) -> T:
        return value
