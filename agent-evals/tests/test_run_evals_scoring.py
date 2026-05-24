from __future__ import annotations

from run_evals import score_case


def test_score_case_uses_semantic_score_for_accuracy_dimension():
    stream_result = {
        "answer": "回答内容",
        "reasoning": "分析",
        "events": ["started", "reasoning", "delta", "completed"],
        "metrics": {"firstTokenMs": 100, "totalMs": 500, "answerChars": 4, "reasoningChars": 2},
    }

    score = score_case(
        stream_result,
        {},
        {"minAnswerChars": 1, "semantic": {"threshold": 0.75}},
        semantic_result={"enabled": True, "provider": "deepeval", "score": 0.8, "reason": "", "skipped": False, "error": ""},
    )

    assert score["dimensions"]["accuracy"] == 16.0
    assert score["checks"]["semanticQuality"] is True
