from __future__ import annotations

from run_evals import safe_evaluate_semantic_quality, score_case


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


def test_score_case_accepts_keyword_group_synonyms():
    stream_result = {
        "answer": "生产风险包括文件上传校验、模型流式输出中断、审计记录缺失，需要给出应对措施。",
        "reasoning": "分析",
        "events": ["started", "reasoning", "delta", "completed"],
        "metrics": {"firstTokenMs": 100, "totalMs": 500, "answerChars": 38, "reasoningChars": 2},
    }

    score = score_case(
        stream_result,
        {},
        {
            "minAnswerChars": 1,
            "keywordGroups": [
                {"name": "风险识别", "anyOf": ["风险"]},
                {"name": "缓解措施", "anyOf": ["缓解", "应对措施", "治理措施"]},
                {"name": "文件上传", "anyOf": ["文件上传"]},
                {"name": "模型输出", "anyOf": ["模型"]},
                {"name": "日志审计", "anyOf": ["日志", "审计记录", "可观测性"]},
            ],
        },
    )

    assert score["dimensions"]["accuracy"] == 20.0
    assert score["keywordHits"] == ["风险识别", "缓解措施", "文件上传", "模型输出", "日志审计"]


def test_safe_semantic_evaluation_skips_when_forced_evaluator_is_unavailable():
    result = safe_evaluate_semantic_quality(
        mode="deepeval",
        prompt="请审查风险",
        answer="生产风险包括文件上传、模型调用、审计记录，需要治理措施。",
        expected={"semantic": {"criteria": "回答应覆盖风险审查。"}},
        evaluator=lambda **_: (_ for _ in ()).throw(RuntimeError("DeepEval is required")),
    )

    assert result["enabled"] is False
    assert result["provider"] == "deepeval"
    assert result["skipped"] is True
    assert result["error"] == "DeepEval is required"
