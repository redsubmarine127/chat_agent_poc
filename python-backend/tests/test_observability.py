from __future__ import annotations

from dataclasses import replace
from uuid import uuid4

from app.config import LangfuseConfig, get_settings
from app.observability import LangfuseObservability, NoopObservability, build_observability


class FakeTrace:
    def __init__(self) -> None:
        self.events = []
        self.updates = []

    def event(self, **kwargs):
        self.events.append(kwargs)

    def update(self, **kwargs):
        self.updates.append(kwargs)


class FakeLangfuseClient:
    def __init__(self) -> None:
        self.trace_calls = []
        self.trace_instance = FakeTrace()
        self.flushed = False

    def trace(self, **kwargs):
        self.trace_calls.append(kwargs)
        return self.trace_instance

    def flush(self):
        self.flushed = True


def test_build_observability_returns_noop_when_langfuse_disabled():
    settings = replace(
        get_settings(),
        langfuse=LangfuseConfig(
            enabled=False,
            host="https://cloud.langfuse.com",
            public_key="",
            secret_key="",
            environment="test",
        ),
    )

    observability = build_observability(settings)

    assert isinstance(observability, NoopObservability)


def test_langfuse_observability_records_chat_metadata_and_completion():
    settings = replace(
        get_settings(),
        langfuse=LangfuseConfig(
            enabled=True,
            host="http://langfuse.local",
            public_key="public",
            secret_key="secret",
            environment="test",
        ),
    )
    client = FakeLangfuseClient()
    conversation_id = uuid4()
    message_id = uuid4()

    trace = LangfuseObservability(settings, client=client).start_chat_trace(
        conversation_id=conversation_id,
        message_id=message_id,
        model_id="deepseek-v4-flash",
        skill_id="general",
        content="测试 Langfuse",
    )
    trace.event("context.loaded", {"historyCount": 2})
    trace.end("completed", output="完成")

    assert client.trace_calls[0]["id"] == str(message_id)
    assert client.trace_calls[0]["metadata"]["conversationId"] == str(conversation_id)
    assert client.trace_calls[0]["metadata"]["runtime"] == "python-langgraph"
    assert client.trace_instance.events[0]["name"] == "context.loaded"
    assert client.trace_instance.updates[0]["metadata"]["status"] == "completed"
    assert client.flushed is True
