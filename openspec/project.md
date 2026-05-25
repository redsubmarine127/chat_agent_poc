# Project Specification

## Mission

Build and maintain an intelligent conversation assistant that supports production-grade chat workflows, streaming model output, Skill-based context, file upload, rich message rendering, exportable artifacts, and automated Agent evaluation.

## Repository Layout

| Path | Purpose |
| --- | --- |
| `backend/` | Java 21 + Spring Boot 3.x backend |
| `python-backend/` | Python 3.11+ + FastAPI + LangGraph backend |
| `frontend/` | Shared Vue 3 + Vite frontend |
| `agent-evals/` | Automated Agent evaluation datasets, runner, and reports |
| `openspec/` | Durable product and engineering specifications |
| `docker-compose.yml` | Local openGauss database dependency |

## Runtime Topology

| Service | Default Port | Notes |
| --- | ---: | --- |
| Java backend | `8080` | Main Spring Boot backend |
| Python backend | `8090` | LangGraph backend compatible with current frontend APIs |
| Java frontend page | `5173` | Shared frontend pointing to Java backend |
| Python frontend page | `5175` | Same frontend code, `VITE_API_BASE_URL=http://127.0.0.1:8090` |
| openGauss | `5432` | PostgreSQL-protocol compatible durable database, only required in database persistence mode |

## Global Requirements

- The frontend MUST remain API-compatible with both Java and Python backends unless a spec change explicitly allows divergence.
- The Java backend MUST remain production-grade and follow Java 21, Spring Boot 3.x, WebFlux, DDD-inspired layering, validation, structured logging, and defensive programming practices.
- The Python backend MUST remain Python 3.11+ compatible and use LangGraph for the conversation orchestration path.
- Both backends MUST support SSE streaming events with the same client-facing event shape.
- Local quick-test persistence MUST default to in-memory storage so Java and Python backends can start without a database.
- Durable database persistence MUST use openGauss through PostgreSQL-compatible drivers/protocol when `ASSISTANT_PERSISTENCE_MODE=database`.
- Uploaded files MUST be stored locally behind a storage service boundary so OSS/S3 replacement remains possible.
- Secrets MUST be provided through environment variables or local configuration and MUST NOT be added to OpenSpec, docs, tests, or evaluation datasets.
- Automated evaluation MUST be kept runnable from the repository and SHOULD be used before changing model orchestration, streaming, formatting, or export behavior.

## Shared API Contract

The frontend expects these endpoints:

| Method | Path | Purpose |
| --- | --- | --- |
| `GET` | `/api/conversations` | List conversations |
| `POST` | `/api/conversations` | Create conversation |
| `DELETE` | `/api/conversations/{conversationId}` | Delete conversation |
| `GET` | `/api/conversations/{conversationId}/messages` | List messages |
| `DELETE` | `/api/conversations/{conversationId}/messages` | Clear current context |
| `POST` | `/api/conversations/{conversationId}/messages/stream` | Send message and stream assistant output |
| `POST` | `/api/files` | Upload file |
| `GET` | `/api/skills` | List built-in and dynamic Skills |
| `POST` | `/api/skills/extractions` | Extract current conversation into a dynamic Skill |
| `GET` | `/api/models` | List enabled models |
| `POST` | `/api/exports/markdown` | Download Markdown artifact |
| `POST` | `/api/exports/excel` | Download Excel artifact |

## SSE Event Contract

Streaming responses MUST use `text/event-stream` and emit JSON payloads shaped like:

```json
{
  "type": "started | reasoning | delta | completed | failed",
  "messageId": "uuid",
  "content": "string"
}
```

Expected event flow:

1. `started`
2. zero or more `reasoning`
3. zero or more `delta`
4. exactly one terminal event: `completed` or `failed`
