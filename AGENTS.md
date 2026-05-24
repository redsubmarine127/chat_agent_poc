# Agent Instructions

Before editing this repository, read:

1. `openspec/project.md`
2. `openspec/specs/product-capabilities/spec.md`
3. The spec for the area you are changing under `openspec/specs/`
4. The relevant local README:
   - `README.md`
   - `python-backend/README.md`
   - `agent-evals/README.md`

## Project Facts

- The project is an intelligent conversation assistant.
- The frontend is shared Vue 3 + Vite code under `frontend/`.
- Java backend runs on `8080`.
- Python LangGraph backend runs on `8090`.
- Java frontend test page usually runs on `5173`.
- Python frontend test page usually runs on `5175`.
- openGauss is the default database.

## Non-Negotiables

- Do not commit secrets or API keys.
- Keep Java and Python backend frontend-facing APIs compatible unless OpenSpec is updated first.
- Preserve SSE event shape: `started`, `reasoning`, `delta`, `completed`, `failed`.
- Preserve rich frontend rendering for Markdown, code blocks, tables, charts, and exports.
- Use `agent-evals/` when changing Agent behavior, streaming, rendering, export, model routing, or backend compatibility.
- Clean temporary test conversations unless the user asks to keep them.

## Recommended Validation

```bash
cd frontend
npm run build
```

```bash
cd backend
mvn test
```

```bash
cd python-backend
.venv/bin/pytest
```

```bash
python-backend/.venv/bin/python agent-evals/run_evals.py \
  --base-url http://127.0.0.1:8090 \
  --dataset agent-evals/datasets/smoke_langgraph.json \
  --fail-under 80
```

