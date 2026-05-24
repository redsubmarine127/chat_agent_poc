# Operations Spec

## Local Development

- openGauss SHOULD be started with `docker-compose up -d`.
- Java backend SHOULD run on port `8080`.
- Python backend SHOULD run on port `8090`.
- Java-targeted frontend SHOULD run on port `5173`.
- Python-targeted frontend SHOULD run on port `5175`.

## Verification Commands

Recommended checks:

```bash
cd backend
mvn test
```

```bash
cd frontend
npm run build
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

## Browser Verification

- Downloads SHOULD be verified in Google Chrome when browser download behavior is under test.
- The in-app browser may block some localhost automation; this is an environment limitation, not necessarily an app defect.
- When a page is already open at `5173`, Python backend testing requires navigating to `5175`.

## Data Hygiene

- Automated tests and evaluation SHOULD clean temporary conversations.
- One-off manual test data SHOULD be removed when it is not useful for future debugging.
- Runtime data, virtual environments, dependency folders, and build outputs SHOULD remain ignored by git.

## Better Long-Term Practices

In addition to OpenSpec, the project SHOULD maintain:

- `AGENTS.md`: short, high-signal instructions for future coding agents.
- ADRs under `docs/adr/`: architecture decision records for database, model gateway, streaming protocol, and evaluation strategy.
- Evaluation reports under `agent-evals/reports/`: historical evidence of behavior changes.
- API contract tests: black-box tests that run against both Java and Python backends.

