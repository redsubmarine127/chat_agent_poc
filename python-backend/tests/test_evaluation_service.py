from __future__ import annotations

from pathlib import Path

import pytest

from app.errors import AssistantError
from app.services.evaluation_service import EvaluationService


def test_resolve_report_path_should_accept_existing_report(tmp_path):
    service = EvaluationService()
    service._reports_dir = tmp_path
    report_path = tmp_path / "smoke_langgraph-20260524072408.md"
    report_path.write_text("# report", encoding="utf-8")

    assert service.resolve_report_path(report_path.name) == report_path.resolve()


def test_resolve_report_path_should_reject_path_traversal(tmp_path):
    service = EvaluationService()
    service._reports_dir = tmp_path

    with pytest.raises(AssistantError, match="报告文件名不合法"):
        service.resolve_report_path("../secret.md")
