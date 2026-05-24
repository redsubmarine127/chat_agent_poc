from __future__ import annotations

import importlib
import logging
from dataclasses import dataclass
from typing import Any, Protocol
from uuid import UUID

from app.config import Settings

logger = logging.getLogger(__name__)


class ChatTrace(Protocol):
    def event(self, name: str, metadata: dict[str, Any] | None = None) -> None:
        ...

    def end(self, status: str, output: str = "", error: str = "") -> None:
        ...


class AssistantObservability(Protocol):
    def start_chat_trace(
        self,
        *,
        conversation_id: UUID,
        message_id: UUID,
        model_id: str,
        skill_id: str,
        content: str,
    ) -> ChatTrace:
        ...


class NoopChatTrace:
    def event(self, name: str, metadata: dict[str, Any] | None = None) -> None:
        return None

    def end(self, status: str, output: str = "", error: str = "") -> None:
        return None


class NoopObservability:
    def start_chat_trace(
        self,
        *,
        conversation_id: UUID,
        message_id: UUID,
        model_id: str,
        skill_id: str,
        content: str,
    ) -> ChatTrace:
        return NoopChatTrace()


@dataclass(slots=True)
class LangfuseChatTrace:
    client: Any
    trace: Any

    def event(self, name: str, metadata: dict[str, Any] | None = None) -> None:
        try:
            if hasattr(self.trace, "event"):
                self.trace.event(name=name, metadata=metadata or {})
            elif hasattr(self.client, "event"):
                self.client.event(trace_id=getattr(self.trace, "id", None), name=name, metadata=metadata or {})
        except Exception as exc:  # noqa: BLE001 - observability must not break chat
            logger.debug("langfuse_event_failed name=%s error=%s", name, exc)

    def end(self, status: str, output: str = "", error: str = "") -> None:
        payload = {"status": status, "error": error}
        try:
            if hasattr(self.trace, "update"):
                self.trace.update(output=output, metadata=payload)
            elif hasattr(self.trace, "end"):
                self.trace.end(output=output, metadata=payload)
            if hasattr(self.client, "flush"):
                self.client.flush()
        except Exception as exc:  # noqa: BLE001 - observability must not break chat
            logger.debug("langfuse_end_failed status=%s error=%s", status, exc)


class LangfuseObservability:
    def __init__(self, settings: Settings, client: Any | None = None) -> None:
        self._settings = settings
        self._client = client or self._create_client(settings)

    def start_chat_trace(
        self,
        *,
        conversation_id: UUID,
        message_id: UUID,
        model_id: str,
        skill_id: str,
        content: str,
    ) -> ChatTrace:
        metadata = {
            "conversationId": str(conversation_id),
            "messageId": str(message_id),
            "runtime": "python-langgraph",
            "modelId": model_id,
            "skillId": skill_id,
            "environment": self._settings.langfuse.environment,
        }
        trace = self._start_trace(name="chat.stream", trace_id=str(message_id), input_value=content, metadata=metadata)
        return LangfuseChatTrace(client=self._client, trace=trace)

    def _start_trace(self, *, name: str, trace_id: str, input_value: str, metadata: dict[str, Any]) -> Any:
        if hasattr(self._client, "trace"):
            return self._client.trace(id=trace_id, name=name, input=input_value, metadata=metadata)
        if hasattr(self._client, "start_span"):
            return self._client.start_span(name=name, input=input_value, metadata=metadata)
        raise RuntimeError("Unsupported Langfuse client API")

    def _create_client(self, settings: Settings) -> Any:
        module = importlib.import_module("langfuse")
        if hasattr(module, "Langfuse"):
            return module.Langfuse(
                public_key=settings.langfuse.public_key,
                secret_key=settings.langfuse.secret_key,
                host=settings.langfuse.host,
            )
        if hasattr(module, "get_client"):
            return module.get_client()
        raise RuntimeError("Unsupported Langfuse SDK")


def build_observability(settings: Settings) -> AssistantObservability:
    if not settings.langfuse.enabled:
        return NoopObservability()
    if not settings.langfuse.public_key or not settings.langfuse.secret_key:
        logger.warning("langfuse_disabled_missing_credentials")
        return NoopObservability()
    try:
        return LangfuseObservability(settings)
    except Exception as exc:  # noqa: BLE001 - optional observability must not block startup
        logger.warning("langfuse_disabled_initialization_failed error=%s", exc)
        return NoopObservability()
