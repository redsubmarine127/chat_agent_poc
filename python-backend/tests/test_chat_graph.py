import pytest

from app.ai.graph import build_chat_graph
from app.config import ModelConfig
from app.models import McpToolResponse, RagContextResponse, SkillResponse


@pytest.mark.asyncio
async def test_chat_graph_should_compose_prompt_messages():
    graph = build_chat_graph()

    state = await graph.ainvoke(
        {
            "content": "解释 DDD",
            "skill": SkillResponse(id="general", name="通用助手", description="简洁回答"),
            "model": ModelConfig("local-fallback", "本地回退模型", "local", "local-fallback"),
            "history": [],
            "rag_contexts": [RagContextResponse(sourceId="doc-1", title="DDD", content="聚合根负责一致性边界。", score=1.0)],
            "mcp_tools": [McpToolResponse(name="context.search", description="搜索上下文")],
        }
    )

    assert state["prompt_messages"][0]["role"] == "system"
    assert "RAG 上下文" in state["prompt_messages"][0]["content"]
    assert "context.search" in state["prompt_messages"][0]["content"]
    assert state["prompt_messages"][-1]["content"] == "解释 DDD"
    assert "LangGraph" in state["reasoning"]
