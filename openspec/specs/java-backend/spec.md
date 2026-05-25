# Java Backend Spec

## Technology

- The Java backend MUST use Java 21 and Spring Boot 3.x.
- The Java backend MUST use WebFlux for non-blocking APIs and streaming.
- The Java backend MUST use Spring AI Alibaba where applicable.
- The Java backend MUST include OpenAI-compatible SDK/gateway support for model providers.
- The Java backend MUST follow an application/domain/infrastructure/interfaces layering style.

## Persistence

- The Java backend MUST use openGauss by default.
- R2DBC/JDBC access MAY use PostgreSQL-compatible protocol/drivers when connecting to openGauss.
- Schema MUST include conversations, chat messages, attachments, and dynamic Skills.
- Migrations or schema initialization MUST be repeatable and idempotent.

## Model Support

- The backend MUST expose enabled models through `/api/models`.
- The model list MUST include local fallback and configured OpenAI-compatible providers.
- DeepSeek model ids MUST include `deepseek-v4-pro` and `deepseek-v4-flash`.
- `deepseek-v4-flash` SHOULD be the default model id.
- API keys MUST NOT be written into OpenSpec or committed documentation.

## Chat Streaming

- `/api/conversations/{conversationId}/messages/stream` MUST use SSE.
- The service MUST validate request content, Skill id, model id, and attachment ids.
- The service MUST save the user message before streaming assistant output.
- The service MUST save the completed assistant message after stream completion.
- On model failure, the service MUST emit a `failed` event and save a FAILED assistant message.
- Business code MUST depend on a gateway abstraction rather than vendor SDK details.
- Chat prompt assembly SHOULD include enabled RAG context and MCP tool descriptors before invoking the model gateway.
- The chat stream MUST log key Agent steps with contextual identifiers, including database access, Skill loading, model resolution, RAG retrieval, MCP tool loading, model gateway routing, LLM streaming, and assistant message persistence.
- Critical Agent steps SHOULD retry transient failures up to 3 attempts by default. If a step still fails after 3 attempts, the backend MUST stop the current stream, persist a FAILED assistant message when possible, and emit a user-readable `failed` SSE event.

## RAG And MCP

- The Java backend MUST expose `GET /api/rag/search`.
- The Java backend MUST expose `GET /api/mcp/tools`.
- The Java backend MUST expose `POST /api/mcp/tools/{toolName}/invoke`.
- RAG MUST be represented by application-layer abstractions, with the default implementation using configured local documents.
- MCP MUST be represented by application-layer abstractions, with the default implementation using configured tool descriptors and a `context.search` tool backed by RAG.
- Unknown or disabled MCP tools MUST return a structured business error.
- RAG/MCP configuration MUST be driven by `assistant.rag` and `assistant.mcp` properties.

## Files

- File upload MUST validate max size and content type.
- File upload MUST prevent path traversal.
- File metadata MUST be persisted.
- Physical files MUST be stored behind a storage service boundary.

## Exports

- `/api/exports/markdown` MUST return downloadable Markdown bytes.
- `/api/exports/excel` MUST return valid `.xlsx` bytes.
- Export endpoints MUST validate form inputs and return structured errors for invalid input.

## Quality

- Request DTOs SHOULD use Java records.
- Inputs MUST use Bean Validation / JSR-303 where applicable.
- Exceptions MUST be mapped by a global exception handler.
- Logs MUST use SLF4J parameterized structured context.
- The code MUST avoid `printStackTrace()`.
- Tests SHOULD cover positive flow, validation failures, export behavior, and model failure flow.
