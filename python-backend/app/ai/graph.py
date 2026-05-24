from __future__ import annotations

from typing import TypedDict

from langgraph.graph import END, StateGraph

from app.config import ModelConfig
from app.models import McpToolResponse, MessageResponse, RagContextResponse, SkillResponse


class ChatGraphState(TypedDict, total=False):
    content: str
    skill: SkillResponse
    model: ModelConfig
    history: list[MessageResponse]
    rag_contexts: list[RagContextResponse]
    mcp_tools: list[McpToolResponse]
    prompt_messages: list[dict[str, str]]
    reasoning: str


def build_chat_graph():
    graph = StateGraph(ChatGraphState)
    graph.add_node("compose_prompt", compose_prompt)
    graph.add_node("summarize_context", summarize_context)
    graph.set_entry_point("compose_prompt")
    graph.add_edge("compose_prompt", "summarize_context")
    graph.add_edge("summarize_context", END)
    return graph.compile()


async def compose_prompt(state: ChatGraphState) -> ChatGraphState:
    skill = state["skill"]
    history = state.get("history", [])[-12:]
    rag_contexts = state.get("rag_contexts", [])
    mcp_tools = state.get("mcp_tools", [])
    system_content = (
        "你是一个严谨、简洁、面向生产环境的智能助手。"
        f"当前 Skill：{skill.name}。Skill 指令：{skill.description}"
    )
    if rag_contexts:
        rag_text = "\n".join(
            f"- 来源：{item.title}，相关度：{item.score:.2f}\n  内容：{item.content}"
            for item in rag_contexts
        )
        system_content += f"\n可参考的 RAG 上下文如下，若无关请明确说明：\n{rag_text}"
    if mcp_tools:
        tool_text = "\n".join(f"- {item.name}：{item.description}" for item in mcp_tools)
        system_content += f"\n已注册 MCP 工具清单，真实工具执行由后续执行器扩展：\n{tool_text}"
    messages = [
        {
            "role": "system",
            "content": system_content,
        }
    ]
    for item in history:
        role = "assistant" if item.role == "ASSISTANT" else "user"
        messages.append({"role": role, "content": item.content})
    messages.append({"role": "user", "content": state["content"]})
    return {"prompt_messages": messages}


async def summarize_context(state: ChatGraphState) -> ChatGraphState:
    history_count = len(state.get("history", []))
    rag_count = len(state.get("rag_contexts", []))
    tool_count = len(state.get("mcp_tools", []))
    model = state["model"]
    return {
        "reasoning": (
            f"1. 已加载 {history_count} 条历史消息。\n"
            f"2. 已选择模型 {model.name}。\n"
            f"3. 已检索 {rag_count} 条 RAG 上下文。\n"
            f"4. 已加载 {tool_count} 个 MCP 工具描述。\n"
            "5. 已完成 LangGraph 编排，准备进入模型流式输出。\n"
        )
    }
