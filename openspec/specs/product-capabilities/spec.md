# Product Capabilities Spec

## Conversation Management

- The product MUST support creating conversations.
- The product MUST support switching between conversations.
- The product MUST support deleting conversations.
- The product MUST support clearing the current conversation context without deleting the conversation itself.
- Conversation lists MUST remain usable when there are many conversations.
- Conversations in the sidebar MUST show stable visible sequence numbers.
- Messages in the active conversation MUST show visible sequence numbers.

## Message Composition

- The composer MUST support text input.
- Pressing `Enter` MUST send the message.
- Pressing `Shift+Enter` SHOULD insert a newline.
- Sending MUST be disabled when there is no active conversation or the draft is blank.
- The composer MUST support model selection.
- The composer MUST provide Skill management.
- Skills MUST be unloaded by default.
- Skill loading/unloading MUST happen in a modal labeled as Skill management.
- File upload MUST live inside the Skill management modal.
- File upload MUST support drag and drop.

## Skill Management

- The system MUST provide built-in Skills.
- The system MUST support dynamic Skills extracted from the current conversation.
- Extracted Skills MUST be saved and listed in Skill management.
- A Skill selected for a message MUST be included in the backend stream request.

## Streaming Assistant Output

- Assistant responses MUST render progressively while the backend streams content.
- The UI MUST expose model reasoning events as a thinking process.
- Thinking process lines MUST be numbered for readability.
- The UI MUST distinguish loading, streaming, completed, and failed states.
- Failed streams MUST display a user-readable error state.

## Rich Content Rendering

- Assistant responses MUST render Markdown cleanly.
- Code blocks MUST render as formatted code blocks.
- Markdown tables MUST render as tables.
- Common malformed model Markdown such as `###标题`, `####小节`, and `###标题|表头|...` MUST be normalized for display.
- Charts SHOULD be generated from table-like assistant responses when possible.
- The UI MUST avoid exposing raw formatting markers such as repeated `###` when they are intended as headings.

## Artifact Export

- Assistant messages MUST support Markdown download when content exists.
- Assistant messages with Markdown tables MUST support Excel download.
- Downloads MUST produce real files with non-empty content.
- Excel downloads MUST produce valid `.xlsx` bytes.
- Exports MUST work in normal browsers such as Google Chrome.

## Evaluation

- The product MUST include automated Agent evaluation datasets.
- The product MUST include an automated runner that can score backend responses.
- Evaluation SHOULD cover streaming, reasoning, Markdown, tables, exports, latency, and error handling.

## RAG And MCP

- The product MUST provide a RAG retrieval capability for project and domain knowledge.
- The backend MUST expose `GET /api/rag/search` with `query` and `limit` parameters.
- RAG retrieval MUST be implemented behind a service boundary so local configured documents can later be replaced by vector databases or external knowledge bases.
- The product MUST expose MCP tool discovery through `GET /api/mcp/tools`.
- The product MUST expose MCP tool invocation through `POST /api/mcp/tools/{toolName}/invoke`.
- MCP tools MUST be registered behind an extensible service boundary so future local tools, remote MCP servers, and artifact generators can be added without changing chat orchestration contracts.
- Chat streaming SHOULD include retrieved RAG context and available MCP tool descriptions in the model prompt when enabled.
