from __future__ import annotations

from types import SimpleNamespace

import pytest

from evaluators import evaluate_semantic_quality


def test_auto_semantic_evaluation_skips_when_deepeval_is_missing():
    result = evaluate_semantic_quality(
        mode="auto",
        prompt="解释 SSE",
        answer="SSE 是服务端事件流。",
        expected={"semantic": {"criteria": "回答应解释 SSE。"}},
        importer=lambda name: (_ for _ in ()).throw(ImportError(name)),
    )

    assert result.enabled is False
    assert result.provider == "deepeval"
    assert result.skipped is True
    assert "not installed" in result.error


def test_deepeval_mode_raises_when_dependency_is_missing():
    with pytest.raises(RuntimeError, match="DeepEval is required"):
        evaluate_semantic_quality(
            mode="deepeval",
            prompt="解释 SSE",
            answer="SSE 是服务端事件流。",
            expected={"semantic": {"criteria": "回答应解释 SSE。"}},
            importer=lambda name: (_ for _ in ()).throw(ImportError(name)),
        )


def test_deepeval_semantic_evaluation_returns_score_and_reason():
    measured_cases = []

    class FakeMetric:
        def __init__(self, **kwargs):
            self.kwargs = kwargs
            self.score = None
            self.reason = ""

        def measure(self, test_case):
            measured_cases.append(test_case)
            self.score = 0.82
            self.reason = "回答覆盖了关键任务。"

    fake_metrics = SimpleNamespace(GEval=lambda **kwargs: FakeMetric(**kwargs))
    fake_test_case = SimpleNamespace(
        LLMTestCase=lambda **kwargs: SimpleNamespace(**kwargs),
        LLMTestCaseParams=SimpleNamespace(INPUT="input", ACTUAL_OUTPUT="actual_output", EXPECTED_OUTPUT="expected_output"),
    )

    def importer(name: str):
        return {"deepeval.metrics": fake_metrics, "deepeval.test_case": fake_test_case}[name]

    result = evaluate_semantic_quality(
        mode="deepeval",
        prompt="列出三条风险",
        answer="1. 接口超时\n2. 数据不一致\n3. 权限缺失",
        expected={
            "semantic": {
                "criteria": "回答应列出真实风险。",
                "expectedOutput": "至少三条风险",
                "threshold": 0.8,
            }
        },
        importer=importer,
    )

    assert result.enabled is True
    assert result.score == 0.82
    assert result.reason == "回答覆盖了关键任务。"
    assert measured_cases[0].actual_output.startswith("1. 接口超时")
