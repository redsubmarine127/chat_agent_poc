import pytest

from app.config import LangfuseConfig, McpConfig, McpToolConfig, ModelConfig, RagConfig, RagDocumentConfig, Settings
from app.services.mcp_service import McpService
from app.services.rag_service import RagService


def build_settings() -> Settings:
    return Settings(
        server_host="127.0.0.1",
        server_port=8090,
        persistence_mode="memory",
        db_dsn="postgresql://assistant:password@127.0.0.1:5432/assistant",
        storage_root=__import__("pathlib").Path("/tmp"),
        max_file_size=1024,
        allowed_content_types=frozenset({"text/plain"}),
        default_model_id="local-fallback",
        skills=(),
        models=(ModelConfig("local-fallback", "本地回退模型", "local", "local-fallback"),),
        rag=RagConfig(
            True,
            3,
            (
                RagDocumentConfig("java", "Java 后端", "Spring Boot WebFlux 支持 SSE 流式输出。"),
                RagDocumentConfig("python", "Python 后端", "FastAPI 使用 LangGraph 编排 Agent。"),
            ),
        ),
        mcp=McpConfig(True, (McpToolConfig("context.search", "搜索 RAG 上下文"),)),
        langfuse=LangfuseConfig(False, "https://cloud.langfuse.com", "", "", "test"),
    )


@pytest.mark.asyncio
async def test_rag_should_retrieve_ranked_contexts():
    service = RagService(build_settings())

    contexts = await service.retrieve("LangGraph 后端", 2)

    assert contexts
    assert contexts[0].source_id == "python"


@pytest.mark.asyncio
async def test_mcp_context_search_should_return_context_metadata():
    rag_service = RagService(build_settings())
    mcp_service = McpService(build_settings(), rag_service)

    result = await mcp_service.invoke("context.search", {"query": "WebFlux", "limit": 1})

    assert result.success is True
    assert result.metadata["contexts"][0]["sourceId"] == "java"
