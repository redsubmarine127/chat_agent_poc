from __future__ import annotations

from datetime import datetime, timezone
from enum import StrEnum
from typing import Any, Literal
from uuid import UUID

from pydantic import BaseModel, Field


def utc_now() -> datetime:
    return datetime.now(timezone.utc)


class MessageRole(StrEnum):
    USER = "USER"
    ASSISTANT = "ASSISTANT"


class MessageStatus(StrEnum):
    COMPLETED = "COMPLETED"
    FAILED = "FAILED"


class CreateConversationRequest(BaseModel):
    title: str = Field(default="新的对话", min_length=1, max_length=128)


class StreamChatRequest(BaseModel):
    content: str = Field(min_length=1, max_length=8000)
    skillId: str = Field(min_length=1, max_length=96)
    modelId: str = Field(min_length=1, max_length=96)
    attachmentIds: list[UUID] = Field(default_factory=list, max_length=8)


class ExtractSkillRequest(BaseModel):
    conversationId: UUID
    name: str = Field(default="", max_length=128)


class ConversationResponse(BaseModel):
    id: UUID
    title: str
    createdAt: datetime
    updatedAt: datetime


class MessageResponse(BaseModel):
    id: UUID
    conversationId: UUID
    role: MessageRole
    content: str
    skillId: str | None = None
    modelId: str | None = None
    attachmentIds: list[UUID] = Field(default_factory=list)
    status: MessageStatus
    createdAt: datetime


class SkillResponse(BaseModel):
    id: str
    name: str
    description: str
    enabled: bool = True


class ModelResponse(BaseModel):
    id: str
    name: str
    provider: str
    modelName: str
    enabled: bool = True


class AttachmentResponse(BaseModel):
    id: UUID
    originalFilename: str
    contentType: str
    storageKey: str
    sizeInBytes: int
    createdAt: datetime


class ChatStreamResponse(BaseModel):
    type: Literal["started", "reasoning", "delta", "completed", "failed"]
    messageId: UUID
    content: str


class RagContextResponse(BaseModel):
    sourceId: str
    title: str
    content: str
    score: float


class McpToolResponse(BaseModel):
    name: str
    description: str
    enabled: bool = True


class McpInvokeRequest(BaseModel):
    arguments: dict[str, Any] = Field(default_factory=dict)


class McpToolResultResponse(BaseModel):
    toolName: str
    success: bool
    content: str
    metadata: dict[str, Any] = Field(default_factory=dict)


class EvaluationRunRequest(BaseModel):
    dataset: str = Field(default="smoke_langgraph", pattern="^[a-zA-Z0-9_.-]+$")
    modelId: str = Field(default="", max_length=96)
    semanticEvaluator: Literal["none", "auto", "deepeval"] = "none"
    failUnder: float = Field(default=0.0, ge=0.0, le=100.0)


class EvaluationReportFile(BaseModel):
    type: Literal["json", "markdown"]
    filename: str
    downloadUrl: str


class EvaluationRunResponse(BaseModel):
    dataset: str
    status: Literal["PASS", "NEEDS_REVIEW", "ERROR"]
    exitCode: int
    averageScore: float
    passCount: int
    errorCount: int
    caseCount: int
    durationMs: float
    output: str
    report: dict[str, Any]
    files: list[EvaluationReportFile]


class ErrorResponse(BaseModel):
    code: str
    message: str
    path: str
    timestamp: datetime = Field(default_factory=utc_now)
