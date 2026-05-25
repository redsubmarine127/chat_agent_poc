from __future__ import annotations

import logging
from contextlib import asynccontextmanager

from fastapi import FastAPI, Request
from fastapi.exceptions import RequestValidationError
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import JSONResponse

from app.ai.gateway import RoutingChatModelGateway
from app.api.routes import router
from app.config import get_settings
from app.database import Database
from app.errors import AssistantError
from app.models import ErrorResponse
from app.observability import build_observability
from app.repositories import AttachmentRepository, ConversationRepository, DynamicSkillRepository, InMemoryRepositoryBundle, MessageRepository
from app.services.chat_service import ChatStreamService, ConversationService
from app.services.export_service import ExportService
from app.services.evaluation_service import EvaluationService
from app.services.file_service import FileService
from app.services.mcp_service import McpService
from app.services.model_service import ModelService
from app.services.rag_service import RagService
from app.services.skill_service import SkillService

logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(name)s %(message)s")
logger = logging.getLogger(__name__)


@asynccontextmanager
async def lifespan(app: FastAPI):
    settings = get_settings()
    database: Database | None = None

    if settings.persistence_mode == "database":
        database = Database(settings)
        await database.connect()
        await database.migrate()
        conversation_repository = ConversationRepository(database)
        message_repository = MessageRepository(database)
        attachment_repository = AttachmentRepository(database)
        dynamic_skill_repository = DynamicSkillRepository(database)
        logger.info("persistence_mode_selected mode=database")
    else:
        repositories = InMemoryRepositoryBundle()
        conversation_repository = repositories.conversations
        message_repository = repositories.messages
        attachment_repository = repositories.attachments
        dynamic_skill_repository = repositories.dynamic_skills
        logger.info("persistence_mode_selected mode=memory")

    model_service = ModelService(settings)
    skill_service = SkillService(settings, dynamic_skill_repository, message_repository)
    conversation_service = ConversationService(conversation_repository, message_repository)
    rag_service = RagService(settings)
    mcp_service = McpService(settings, rag_service)
    observability = build_observability(settings)

    app.state.settings = settings
    app.state.database = database
    app.state.model_service = model_service
    app.state.skill_service = skill_service
    app.state.conversation_service = conversation_service
    app.state.rag_service = rag_service
    app.state.mcp_service = mcp_service
    app.state.observability = observability
    app.state.chat_stream_service = ChatStreamService(
        conversation_repository,
        message_repository,
        skill_service,
        model_service,
        RoutingChatModelGateway(),
        rag_service,
        mcp_service,
        observability,
    )
    app.state.file_service = FileService(settings, attachment_repository)
    app.state.export_service = ExportService()
    app.state.evaluation_service = EvaluationService(f"http://127.0.0.1:{settings.server_port}")

    try:
        yield
    finally:
        if database is not None:
            await database.close()


app = FastAPI(title="Intelligent Assistant Python Backend", version="0.1.0", lifespan=lifespan)
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=False,
    allow_methods=["*"],
    allow_headers=["*"],
)
app.include_router(router)


@app.exception_handler(AssistantError)
async def assistant_error_handler(request: Request, error: AssistantError):
    logger.warning("assistant_error path=%s code=%s message=%s", request.url.path, error.code, error.message)
    return JSONResponse(
        status_code=error.status_code,
        content=ErrorResponse(code=error.code, message=error.message, path=request.url.path).model_dump(mode="json"),
    )


@app.exception_handler(RequestValidationError)
async def validation_error_handler(request: Request, error: RequestValidationError):
    logger.warning("request_validation_failed path=%s errors=%s", request.url.path, error.errors())
    return JSONResponse(
        status_code=400,
        content=ErrorResponse(code="VALIDATION_ERROR", message="请求参数不合法", path=request.url.path).model_dump(mode="json"),
    )


@app.exception_handler(Exception)
async def unexpected_error_handler(request: Request, error: Exception):
    logger.exception("unexpected_error path=%s", request.url.path)
    return JSONResponse(
        status_code=500,
        content=ErrorResponse(code="INTERNAL_SERVER_ERROR", message="服务暂时不可用", path=request.url.path).model_dump(mode="json"),
    )
