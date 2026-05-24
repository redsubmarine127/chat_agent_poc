from __future__ import annotations

import asyncio
import json
import re
import subprocess
import sys
import time
from dataclasses import dataclass
from pathlib import Path

from app.errors import AssistantError
from app.models import EvaluationReportFile, EvaluationRunResponse


class EvaluationBadRequestError(AssistantError):
    code = "EVALUATION_BAD_REQUEST"
    status_code = 400


class EvaluationReportNotFoundError(AssistantError):
    code = "EVALUATION_REPORT_NOT_FOUND"
    status_code = 404


class EvaluationExecutionError(AssistantError):
    code = "EVALUATION_EXECUTION_FAILED"
    status_code = 500


@dataclass(frozen=True, slots=True)
class EvaluationRunOptions:
    dataset: str
    model_id: str
    semantic_evaluator: str
    fail_under: float


class EvaluationService:
    DATASETS = {
        "smoke_langgraph": Path("agent-evals/datasets/smoke_langgraph.json"),
        "assistant_quality": Path("agent-evals/datasets/assistant_quality.json"),
    }

    def __init__(self, backend_base_url: str = "http://127.0.0.1:8090") -> None:
        self._repo_root = Path(__file__).resolve().parents[3]
        self._reports_dir = self._repo_root / "agent-evals" / "reports"
        self._backend_base_url = backend_base_url.rstrip("/")

    async def run(self, options: EvaluationRunOptions) -> EvaluationRunResponse:
        if options.dataset not in self.DATASETS:
            raise EvaluationBadRequestError("不支持的评估数据集")

        started_at = time.perf_counter()
        report_files_before = self._known_report_files()
        completed = await asyncio.to_thread(self._run_subprocess, options)
        report_files_after = self._known_report_files()
        new_report_files = sorted(report_files_after - report_files_before, key=lambda path: path.stat().st_mtime, reverse=True)
        json_report_path = next((path for path in new_report_files if path.suffix == ".json"), None)
        if json_report_path is None:
            json_report_path = self._latest_report_file(options.dataset, ".json")
        if json_report_path is None:
            raise EvaluationExecutionError("评估执行完成但未生成报告")

        report = json.loads(json_report_path.read_text(encoding="utf-8"))
        markdown_report_path = json_report_path.with_suffix(".md")
        summary = report.get("summary", {})
        status = self._resolve_status(completed.returncode, summary)
        files = [
            self._report_file("json", json_report_path),
        ]
        if markdown_report_path.exists():
            files.append(self._report_file("markdown", markdown_report_path))

        return EvaluationRunResponse(
            dataset=str(report.get("dataset") or options.dataset),
            status=status,
            exitCode=completed.returncode,
            averageScore=float(summary.get("averageScore") or 0.0),
            passCount=int(summary.get("passCount") or 0),
            errorCount=int(summary.get("errorCount") or 0),
            caseCount=int(summary.get("caseCount") or 0),
            durationMs=round((time.perf_counter() - started_at) * 1000, 2),
            output=(completed.stdout + "\n" + completed.stderr).strip(),
            report=report,
            files=files,
        )

    def resolve_report_path(self, filename: str) -> Path:
        if not re.fullmatch(r"[a-zA-Z0-9_.-]+\.(json|md)", filename):
            raise EvaluationBadRequestError("报告文件名不合法")
        report_path = (self._reports_dir / filename).resolve()
        if self._reports_dir.resolve() not in report_path.parents:
            raise EvaluationBadRequestError("报告路径不合法")
        if not report_path.exists():
            raise EvaluationReportNotFoundError("评估报告不存在")
        return report_path

    def _run_subprocess(self, options: EvaluationRunOptions) -> subprocess.CompletedProcess[str]:
        dataset_path = self.DATASETS[options.dataset]
        command = [
            sys.executable,
            "agent-evals/run_evals.py",
            "--base-url",
            self._backend_base_url,
            "--dataset",
            str(dataset_path),
            "--semantic-evaluator",
            options.semantic_evaluator,
            "--fail-under",
            str(options.fail_under),
        ]
        if options.model_id:
            command.extend(["--model-id", options.model_id])
        return subprocess.run(
            command,
            cwd=self._repo_root,
            check=False,
            capture_output=True,
            text=True,
            timeout=180,
        )

    def _known_report_files(self) -> set[Path]:
        self._reports_dir.mkdir(parents=True, exist_ok=True)
        return {path for path in self._reports_dir.iterdir() if path.is_file() and path.suffix in {".json", ".md"}}

    def _latest_report_file(self, dataset: str, suffix: str) -> Path | None:
        safe_dataset = re.sub(r"[^a-zA-Z0-9_.-]+", "-", dataset)
        candidates = sorted(self._reports_dir.glob(f"{safe_dataset}-*{suffix}"), key=lambda path: path.stat().st_mtime, reverse=True)
        return candidates[0] if candidates else None

    def _report_file(self, file_type: str, path: Path) -> EvaluationReportFile:
        return EvaluationReportFile(
            type=file_type,
            filename=path.name,
            downloadUrl=f"/api/evaluations/reports/{path.name}/download",
        )

    def _resolve_status(self, exit_code: int, summary: dict) -> str:
        if exit_code not in {0, 2} or int(summary.get("errorCount") or 0) > 0:
            return "ERROR"
        if exit_code == 2:
            return "NEEDS_REVIEW"
        return "PASS"
