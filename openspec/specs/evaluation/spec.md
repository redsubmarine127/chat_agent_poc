# Agent Evaluation Spec

## Purpose

The project MUST include automated evaluation so Agent behavior can be compared across Java and Python backends and across model configurations.

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

## Scoring

The default scoring model MUST use a 100 point scale:

| Dimension | Points |
| --- | ---: |
| Task completion | 40 |
| Accuracy | 20 |
| Format and interaction | 15 |
| Stability | 15 |
| Performance | 10 |

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

