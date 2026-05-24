from __future__ import annotations

from typing import Annotated
from urllib.parse import quote
from uuid import UUID

from fastapi import APIRouter, Depends, File, Form, Request, UploadFile
from fastapi.responses import FileResponse, Response, StreamingResponse

from app.models import CreateConversationRequest, EvaluationRunRequest, ExtractSkillRequest, McpInvokeRequest, McpToolResultResponse, McpToolResponse, RagContextResponse, StreamChatRequest
from app.services.chat_service import ChatStreamService, ConversationService
from app.services.evaluation_service import EvaluationRunOptions, EvaluationService
from app.services.export_service import ExportService
from app.services.file_service import FileService
from app.services.mcp_service import McpService
from app.services.model_service import ModelService
from app.services.rag_service import RagService
from app.services.skill_service import SkillService

router = APIRouter(prefix="/api")


def conversation_service(request: Request) -> ConversationService:
    return request.app.state.conversation_service


def chat_stream_service(request: Request) -> ChatStreamService:
    return request.app.state.chat_stream_service


def skill_service(request: Request) -> SkillService:
    return request.app.state.skill_service


def model_service(request: Request) -> ModelService:
    return request.app.state.model_service


def file_service(request: Request) -> FileService:
    return request.app.state.file_service


def export_service(request: Request) -> ExportService:
    return request.app.state.export_service


def rag_service(request: Request) -> RagService:
    return request.app.state.rag_service


def mcp_service(request: Request) -> McpService:
    return request.app.state.mcp_service


def evaluation_service(request: Request) -> EvaluationService:
    return request.app.state.evaluation_service


@router.get("/conversations")
async def list_conversations(service: Annotated[ConversationService, Depends(conversation_service)]):
    return await service.list_conversations()


@router.post("/conversations")
async def create_conversation(request: CreateConversationRequest, service: Annotated[ConversationService, Depends(conversation_service)]):
    return await service.create_conversation(request.title)


@router.delete("/conversations/{conversation_id}", status_code=204)
async def delete_conversation(conversation_id: UUID, service: Annotated[ConversationService, Depends(conversation_service)]):
    await service.delete_conversation(conversation_id)
    return Response(status_code=204)


@router.get("/conversations/{conversation_id}/messages")
async def list_messages(conversation_id: UUID, service: Annotated[ConversationService, Depends(conversation_service)]):
    return await service.list_messages(conversation_id)


@router.delete("/conversations/{conversation_id}/messages", status_code=204)
async def clear_messages(conversation_id: UUID, service: Annotated[ConversationService, Depends(conversation_service)]):
    await service.clear_messages(conversation_id)
    return Response(status_code=204)


@router.post("/conversations/{conversation_id}/messages/stream")
async def stream_message(
    conversation_id: UUID,
    request: StreamChatRequest,
    service: Annotated[ChatStreamService, Depends(chat_stream_service)],
):
    return StreamingResponse(
        service.stream(conversation_id, request.content, request.skillId, request.modelId, request.attachmentIds),
        media_type="text/event-stream",
        headers={"Cache-Control": "no-cache", "X-Accel-Buffering": "no"},
    )


@router.get("/skills")
async def list_skills(service: Annotated[SkillService, Depends(skill_service)]):
    return await service.list_enabled_skills()


@router.post("/skills/extractions")
async def extract_skill(request: ExtractSkillRequest, service: Annotated[SkillService, Depends(skill_service)]):
    return await service.extract_from_conversation(request.conversationId, request.name)


@router.get("/models")
async def list_models(service: Annotated[ModelService, Depends(model_service)]):
    return service.list_models()


@router.get("/rag/search")
async def search_rag(
    query: str,
    limit: int = 5,
    service: RagService = Depends(rag_service),
):
    contexts = await service.retrieve(query, limit)
    return [
        RagContextResponse(sourceId=context.source_id, title=context.title, content=context.content, score=context.score)
        for context in contexts
    ]


@router.get("/mcp/tools")
async def list_mcp_tools(service: Annotated[McpService, Depends(mcp_service)]):
    tools = await service.list_tools()
    return [McpToolResponse(name=tool.name, description=tool.description, enabled=tool.enabled) for tool in tools]


@router.post("/mcp/tools/{tool_name}/invoke")
async def invoke_mcp_tool(
    tool_name: str,
    request: McpInvokeRequest,
    service: Annotated[McpService, Depends(mcp_service)],
):
    result = await service.invoke(tool_name, request.arguments)
    return McpToolResultResponse(
        toolName=result.tool_name,
        success=result.success,
        content=result.content,
        metadata=result.metadata,
    )


@router.post("/evaluations/runs")
async def run_evaluation(
    request: EvaluationRunRequest,
    service: Annotated[EvaluationService, Depends(evaluation_service)],
):
    return await service.run(
        EvaluationRunOptions(
            dataset=request.dataset,
            model_id=request.modelId,
            semantic_evaluator=request.semanticEvaluator,
            fail_under=request.failUnder,
        )
    )


@router.get("/evaluations/reports/{filename}/download")
async def download_evaluation_report(filename: str, service: Annotated[EvaluationService, Depends(evaluation_service)]):
    report_path = service.resolve_report_path(filename)
    media_type = "application/json" if report_path.suffix == ".json" else "text/markdown; charset=utf-8"
    return FileResponse(report_path, media_type=media_type, filename=report_path.name)


@router.post("/files")
async def upload_file(file: Annotated[UploadFile, File()], service: Annotated[FileService, Depends(file_service)]):
    return await service.upload(file)


@router.post("/exports/markdown")
async def export_markdown(
    content: Annotated[str, Form(min_length=1, max_length=200_000)],
    filename: Annotated[str, Form(min_length=1, max_length=160)],
    service: Annotated[ExportService, Depends(export_service)],
):
    export_file = service.markdown(content, filename)
    return Response(
        content=export_file.content,
        media_type=export_file.content_type,
        headers={"Content-Disposition": f"attachment; filename*=UTF-8''{quote(export_file.filename)}"},
    )


@router.post("/exports/excel")
async def export_excel(
    content: Annotated[str, Form(min_length=1, max_length=200_000)],
    filename: Annotated[str, Form(min_length=1, max_length=160)],
    service: Annotated[ExportService, Depends(export_service)],
):
    export_file = service.excel(content, filename)
    return Response(
        content=export_file.content,
        media_type=export_file.content_type,
        headers={"Content-Disposition": f"attachment; filename*=UTF-8''{quote(export_file.filename)}"},
    )
