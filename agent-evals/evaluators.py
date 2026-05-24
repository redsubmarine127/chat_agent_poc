from __future__ import annotations

import importlib
from dataclasses import dataclass
from typing import Any, Protocol


class Importer(Protocol):
    def __call__(self, name: str) -> Any:
        ...


@dataclass(frozen=True, slots=True)
class SemanticEvaluation:
    enabled: bool
    provider: str
    score: float | None = None
    reason: str = ""
    skipped: bool = False
    error: str = ""

    def as_dict(self) -> dict[str, Any]:
        return {
            "enabled": self.enabled,
            "provider": self.provider,
            "score": self.score,
            "reason": self.reason,
            "skipped": self.skipped,
            "error": self.error,
        }


def evaluate_semantic_quality(
    *,
    mode: str,
    prompt: str,
    answer: str,
    expected: dict[str, Any],
    importer: Importer = importlib.import_module,
) -> SemanticEvaluation:
    semantic_config = expected.get("semantic") or {}
    if mode == "none" or not semantic_config:
        return SemanticEvaluation(enabled=False, provider="none")

    criteria = str(semantic_config.get("criteria") or "").strip()
    if not criteria:
        return SemanticEvaluation(enabled=False, provider="none", skipped=True, error="semantic criteria is empty")

    try:
        metrics_module = importer("deepeval.metrics")
        test_case_module = importer("deepeval.test_case")
    except ImportError as exc:
        if mode == "auto":
            return SemanticEvaluation(enabled=False, provider="deepeval", skipped=True, error="deepeval is not installed")
        raise RuntimeError("DeepEval is required for semantic evaluation. Install agent-evals/requirements.txt first.") from exc

    expected_output = str(semantic_config.get("expectedOutput") or expected.get("referenceAnswer") or "")
    threshold = float(semantic_config.get("threshold", 0.7))
    evaluation_params = [
        test_case_module.LLMTestCaseParams.INPUT,
        test_case_module.LLMTestCaseParams.ACTUAL_OUTPUT,
    ]
    if expected_output:
        evaluation_params.append(test_case_module.LLMTestCaseParams.EXPECTED_OUTPUT)

    metric = metrics_module.GEval(
        name=str(semantic_config.get("name") or "Agent quality"),
        criteria=criteria,
        evaluation_params=evaluation_params,
        threshold=threshold,
    )
    test_case = test_case_module.LLMTestCase(
        input=prompt,
        actual_output=answer,
        expected_output=expected_output or None,
    )
    metric.measure(test_case)
    return SemanticEvaluation(
        enabled=True,
        provider="deepeval",
        score=float(metric.score or 0.0),
        reason=str(getattr(metric, "reason", "") or ""),
    )
