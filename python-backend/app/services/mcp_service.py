from __future__ import annotations

import logging
from dataclasses import dataclass, field
from typing import Any

from app.config import Settings
from app.errors import AssistantError
from app.services.rag_service import RagService

logger = logging.getLogger(__name__)


@dataclass(frozen=True, slots=True)
class McpToolDescriptor:
    name: str
    description: str
    enabled: bool = True


@dataclass(frozen=True, slots=True)
class McpToolResult:
    tool_name: str
    success: bool
    content: str
    metadata: dict[str, Any] = field(default_factory=dict)


class McpService:
    CONTEXT_SEARCH_TOOL = "context.search"

    def __init__(self, settings: Settings, rag_service: RagService) -> None:
        self._settings = settings
        self._rag_service = rag_service

    async def list_tools(self) -> list[McpToolDescriptor]:
        logger.info("mcp_list_tools_started enabled=%s", self._settings.mcp.enabled)
        if not self._settings.mcp.enabled:
            logger.info("mcp_list_tools_completed enabled=false tool_count=0")
            return []
        tools = [
            McpToolDescriptor(tool.name, tool.description, True)
            for tool in self._settings.mcp.tools
            if tool.enabled
        ]
        logger.info("mcp_list_tools_completed tool_count=%s tool_names=%s", len(tools), [tool.name for tool in tools])
        return tools

    async def invoke(self, tool_name: str, arguments: dict[str, Any]) -> McpToolResult:
        logger.info("mcp_tool_invoke_started tool_name=%s argument_keys=%s", tool_name, sorted(arguments.keys()))
        enabled_tools = {tool.name for tool in await self.list_tools()}
        if tool_name not in enabled_tools:
            logger.warning("mcp_tool_invoke_rejected tool_name=%s enabled_tools=%s", tool_name, sorted(enabled_tools))
            raise AssistantError("MCP 工具不存在或不可用")
        if tool_name == self.CONTEXT_SEARCH_TOOL:
            query = str(arguments.get("query", ""))
            limit = self._parse_limit(arguments.get("limit"))
            contexts = await self._rag_service.retrieve(query, limit)
            logger.info("mcp_tool_invoke_completed tool_name=%s success=true context_count=%s", tool_name, len(contexts))
            return McpToolResult(
                tool_name=tool_name,
                success=True,
                content=f"已检索到 {len(contexts)} 条上下文。" if contexts else "未检索到相关上下文。",
                metadata={
                    "contexts": [
                        {
                            "sourceId": context.source_id,
                            "title": context.title,
                            "content": context.content,
                            "score": context.score,
                        }
                        for context in contexts
                    ]
                },
            )
        logger.info("mcp_tool_invoke_completed tool_name=%s success=false reason=executor_not_connected", tool_name)
        return McpToolResult(tool_name=tool_name, success=False, content="工具已注册，执行器尚未接入。")

    def _parse_limit(self, value: Any) -> int:
        try:
            return max(1, int(value))
        except (TypeError, ValueError):
            return self._settings.rag.top_k
