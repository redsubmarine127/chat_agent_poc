from __future__ import annotations

import asyncio
import json
import logging
from collections.abc import AsyncIterator
from dataclasses import dataclass

import httpx

from app.config import ModelConfig
from app.errors import AssistantError

logger = logging.getLogger(__name__)


@dataclass(frozen=True, slots=True)
class ModelChunk:
    type: str
    content: str


class ChatModelGateway:
    async def stream(self, model: ModelConfig, messages: list[dict[str, str]]) -> AsyncIterator[ModelChunk]:
        raise NotImplementedError


class LocalFallbackChatModelGateway(ChatModelGateway):
    async def stream(self, model: ModelConfig, messages: list[dict[str, str]]) -> AsyncIterator[ModelChunk]:
        logger.info("llm_local_stream_started model_id=%s message_count=%s", model.id, len(messages))
        user_message = next((item["content"] for item in reversed(messages) if item["role"] == "user"), "")
        yield ModelChunk("reasoning", "1. 已接收问题，正在整理上下文。\n")
        await asyncio.sleep(0.08)
        yield ModelChunk("reasoning", f"2. 已选择 {model.name}，使用本地回退模型生成可区分的示例响应。\n")
        answer = (
            f"这是 Python + LangGraph 后端的本地回退响应，当前选择模型：{model.name}（{model.id}）。\n\n"
            "| 项目 | 状态 | 说明 |\n"
            "| --- | --- |\n"
            f"| 模型路由 | OK | 已按 {model.id} 进入本地回退链路 |\n"
            "| LangGraph 编排 | OK | 已完成 prompt 编排 |\n"
            "| SSE 流式输出 | OK | 正在逐字输出 |\n"
            "| 用户问题 | " + user_message[:80].replace("|", "/") + " | 已收到 |\n"
        )
        for character in answer:
            yield ModelChunk("delta", character)
            await asyncio.sleep(0.01)
        logger.info("llm_local_stream_completed model_id=%s answer_chars=%s", model.id, len(answer))


class OpenAiCompatibleChatModelGateway(ChatModelGateway):
    def __init__(self, timeout_seconds: float = 60.0) -> None:
        self._timeout = httpx.Timeout(timeout_seconds, connect=10.0)

    async def stream(self, model: ModelConfig, messages: list[dict[str, str]]) -> AsyncIterator[ModelChunk]:
        if not model.api_key:
            raise AssistantError(f"模型 {model.id} 未配置 API Key")
        endpoint = model.base_url.rstrip("/") + "/chat/completions"
        logger.info(
            "llm_remote_stream_started model_id=%s provider=%s endpoint=%s message_count=%s",
            model.id,
            model.provider,
            endpoint,
            len(messages),
        )
        payload = {
            "model": model.model_name,
            "messages": messages,
            "stream": True,
            "temperature": 0.7,
        }
        headers = {
            "Authorization": f"Bearer {model.api_key}",
            "Content-Type": "application/json",
        }
        async with httpx.AsyncClient(timeout=self._timeout) as client:
            async with client.stream("POST", endpoint, headers=headers, json=payload) as response:
                logger.info("llm_remote_response_received model_id=%s status_code=%s", model.id, response.status_code)
                if response.status_code >= 400:
                    body = await response.aread()
                    logger.warning(
                        "openai_compatible_call_failed model_id={} status_code={} body={}",
                        model.id,
                        response.status_code,
                        body[:500],
                    )
                    raise AssistantError("模型服务调用失败")
                chunk_count = 0
                async for line in response.aiter_lines():
                    chunk = self._parse_stream_line(line)
                    if chunk is not None:
                        chunk_count += 1
                        yield chunk
                logger.info("llm_remote_stream_completed model_id=%s chunk_count=%s", model.id, chunk_count)

    def _parse_stream_line(self, line: str) -> ModelChunk | None:
        if not line.startswith("data:"):
            return None
        payload = line[5:].strip()
        if not payload or payload == "[DONE]":
            return None
        try:
            body = json.loads(payload)
        except json.JSONDecodeError:
            logger.debug("ignore malformed model stream line payload={}", payload)
            return None
        choice = (body.get("choices") or [{}])[0]
        delta = choice.get("delta") or {}
        reasoning = delta.get("reasoning_content") or delta.get("reasoning")
        if reasoning:
            return ModelChunk("reasoning", str(reasoning))
        content = delta.get("content")
        if content:
            return ModelChunk("delta", str(content))
        return None


class RoutingChatModelGateway(ChatModelGateway):
    def __init__(self) -> None:
        self._local = LocalFallbackChatModelGateway()
        self._remote = OpenAiCompatibleChatModelGateway()

    async def stream(self, model: ModelConfig, messages: list[dict[str, str]]) -> AsyncIterator[ModelChunk]:
        logger.info(
            "llm_route_selected model_id=%s provider=%s has_api_key=%s message_count=%s",
            model.id,
            model.provider,
            bool(model.api_key),
            len(messages),
        )
        if model.provider == "local":
            async for chunk in self._local.stream(model, messages):
                yield chunk
            return
        if not model.api_key:
            raise AssistantError(f"模型 {model.name} 未配置 API Key，无法调用远程服务")
        async for chunk in self._remote.stream(model, messages):
            yield chunk
