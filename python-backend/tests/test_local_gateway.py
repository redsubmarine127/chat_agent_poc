import pytest

from app.ai.gateway import LocalFallbackChatModelGateway
from app.config import ModelConfig


@pytest.mark.asyncio
async def test_local_gateway_should_stream_reasoning_and_answer():
    gateway = LocalFallbackChatModelGateway()
    model = ModelConfig("local-fallback", "本地回退模型", "local", "local-fallback")

    chunks = [
        chunk
        async for chunk in gateway.stream(
            model,
            [{"role": "user", "content": "生成一个表格"}],
        )
    ]

    assert any(chunk.type == "reasoning" for chunk in chunks)
    assert any(chunk.type == "delta" for chunk in chunks)
    assert "".join(chunk.content for chunk in chunks if chunk.type == "delta")

