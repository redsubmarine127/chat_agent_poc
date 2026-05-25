# Agent Evaluation Spec

## Purpose

The project MUST include automated evaluation so Agent behavior can be compared across Java and Python backends and across model configurations.

The evaluation stack MUST stay intentionally small:

- `agent-evals/` remains the project-native adapter and regression runner.
- DeepEval SHOULD be used for semantic Agent quality scoring when an LLM-as-judge workflow is required.
- Langfuse SHOULD be kept for production traces, conversation observability, datasets, and feedback loops.
- promptfoo and Ragas SHOULD NOT be introduced unless OpenSpec is updated with a new decision.

## Location

- Evaluation assets MUST live under `agent-evals/`.
- Datasets MUST live under `agent-evals/datasets/`.
- Reports MUST live under `agent-evals/reports/`.
- The runner MUST be `agent-evals/run_evals.py`.

## Runner Requirements

- The runner MUST accept `--base-url`.
- The runner MUST accept `--dataset`.
- The runner MUST support model and Skill overrides.
- The runner MUST create temporary evaluation conversations.
- The runner MUST delete temporary evaluation conversations by default.
- The runner MUST parse SSE events and measure first token latency and total latency.
- The runner MUST test export endpoints when a case declares export requirements.
- The runner MUST generate JSON and Markdown reports.

The runner MUST continue to call the real backend HTTP/SSE APIs instead of bypassing application code. This is required to verify frontend-facing contracts, model routing, stream event shape, export behavior, and Java/Python backend compatibility.

## DeepEval Integration

DeepEval MAY be used inside `agent-evals/` for quality dimensions that are difficult to score deterministically, including:

- task completion quality
- instruction following
- answer relevance
- multi-turn consistency
- hallucination and unsupported claims
- structured output quality

DeepEval MUST NOT replace the project adapter responsibilities of `agent-evals/`. In particular, `agent-evals/` MUST keep ownership of:

- creating and cleaning temporary conversations
- calling `/api/conversations/{conversationId}/messages/stream`
- parsing `started`, `reasoning`, `delta`, `completed`, and `failed` SSE events
- validating export endpoints and downloaded file bytes
- producing repository-local JSON and Markdown reports

## Langfuse Integration

Langfuse SHOULD be retained as the production observability and feedback layer. When enabled, both Java and Python backends SHOULD emit trace metadata for:

- conversation id
- message id
- backend runtime (`java` or `python-langgraph`)
- selected model id
- selected Skill ids
- RAG context ids when RAG is used
- MCP tool names when tools are used
- stream terminal status
- latency and token-related metrics when available

Langfuse integration MUST be enabled by default and configured through environment variables or local ignored configuration. When credentials are missing or initialization fails, the backend MUST fall back to Noop observability without breaking chat. Secrets MUST NOT be committed.

Low-rated or user-flagged production traces SHOULD be exportable into `agent-evals/datasets/` after removing secrets and private content.

## Scoring

The default scoring model MUST use a 100 point scale:

| Dimension | Points |
| --- | ---: |
| Task completion | 40 |
| Accuracy | 20 |
| Format and interaction | 15 |
| Stability | 15 |
| Performance | 10 |

When DeepEval is not enabled, deterministic accuracy scoring SHOULD support both exact keywords and synonym groups. Synonym groups MUST be treated as one expected capability point when any alternative term is present, so semantically valid answers are not failed only because the model used a different wording such as “审计记录” instead of “日志”.

## Dataset Coverage

Datasets SHOULD cover:

- Normal chat responses
- Streaming event completeness
- Reasoning events
- Markdown tables
- Code blocks
- Markdown and Excel export
- Risk review tasks
- Architecture planning tasks
- Latency thresholds
- Forbidden output patterns such as stack traces or generic service errors

## Current Datasets

- `smoke_langgraph.json`: deterministic local fallback smoke tests for Python LangGraph backend.
- `assistant_quality.json`: higher fidelity quality tests for real model-backed evaluation.
