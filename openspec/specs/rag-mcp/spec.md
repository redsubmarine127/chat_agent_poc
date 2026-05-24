# RAG And MCP Extension Spec

## Purpose

- RAG and MCP MUST be optional capabilities that can be enabled by configuration.
- The first production-ready implementation MAY use configured local knowledge documents and configured local tool descriptors.
- The architecture MUST preserve extension points for vector databases, embedding providers, external knowledge bases, local tools, artifact generators, and remote MCP servers.

## API Contract

- `GET /api/rag/search?query={query}&limit={limit}` MUST return an ordered list of matching contexts.
- RAG context responses MUST include `sourceId`, `title`, `content`, and `score`.
- `GET /api/mcp/tools` MUST return enabled tool descriptors.
- MCP tool descriptors MUST include `name`, `description`, and `enabled`.
- `POST /api/mcp/tools/{toolName}/invoke` MUST accept an `arguments` object and return `toolName`, `success`, `content`, and `metadata`.

## Chat Integration

- Chat orchestration SHOULD retrieve RAG context for the current user message before invoking the model gateway.
- Chat orchestration SHOULD include enabled MCP tool descriptors in the model system prompt.
- The model gateway MUST remain independent from concrete RAG storage and MCP execution implementations.
- If RAG returns no context, chat streaming MUST continue normally.
- If MCP has no enabled tools, chat streaming MUST continue normally.

## Default Tools

- `context.search` MUST be available when MCP and RAG are enabled.
- `context.search` MUST accept `query` and optional `limit`.
- `artifact.export` MAY be registered as a reserved descriptor for future file generation tools.

## Extensibility

- Vector retrieval implementations SHOULD implement the same RAG service contract.
- Remote MCP server implementations SHOULD implement the same MCP service contract.
- Future tool execution SHOULD add typed argument validation before calling side-effecting tools.
